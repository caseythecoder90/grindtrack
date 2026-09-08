package dev.grindtrack.auth.service;

import dev.grindtrack.auth.domain.RefreshToken;
import dev.grindtrack.auth.domain.RefreshTokenRepository;
import dev.grindtrack.auth.domain.User;
import dev.grindtrack.auth.domain.UserRepository;
import dev.grindtrack.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Login (password + TOTP), refresh-token issuance, rotation, and revocation. */
@Service
public class AuthService {

  /**
   * Every path that ends a session says so, at a level worth waking up for.
   *
   * <p>Added because "I was logged out again, I think it was the deploy" could not be answered from
   * anything the app recorded. The cascade is the only thing that signs every device out at once,
   * so if it fires there is now a line saying so, and if it did not the question moves on rather
   * than being guessed at.
   */
  private static final Logger log = LoggerFactory.getLogger(AuthService.class);

  /**
   * How long after a rotation the superseded token is still accepted as a race rather than treated
   * as theft.
   *
   * <p>Generous, and it can afford to be now that rotation happens once a day rather than on every
   * renewal. What a client actually does when a rotation goes wrong is hold the old token until the
   * next time it needs one, and "the next time" can be tomorrow morning. A minute covered two
   * browser windows racing each other and nothing else -- not a phone that suspended mid-request,
   * and not a rotation whose response died with the pod that was being replaced.
   *
   * <p>What is given up is narrow: someone holding a stolen cookie who replays it inside a day of
   * its rotation gets a session. They would have had one anyway, because the cookie they stole was
   * live until it rotated. What is bought is that the honest owner is not signed out of every
   * device by a dropped packet.
   */
  private static final Duration ROTATION_GRACE = Duration.ofHours(24);

  private final UserRepository users;
  private final RefreshTokenRepository refreshTokens;
  private final PasswordEncoder passwordEncoder;
  private final TotpService totpService;
  private final AppProperties props;
  private final SecureRandom random = new SecureRandom();

  public AuthService(
      UserRepository users,
      RefreshTokenRepository refreshTokens,
      PasswordEncoder passwordEncoder,
      TotpService totpService,
      AppProperties props) {
    this.users = users;
    this.refreshTokens = refreshTokens;
    this.passwordEncoder = passwordEncoder;
    this.totpService = totpService;
    this.props = props;
  }

  /** Returns the user if password AND current TOTP code both check out. */
  public Optional<User> authenticate(String username, String password, String otp) {
    return authenticate(username, password, otp, null);
  }

  /**
   * The same, except that a browser which already proved the second factor may skip the code.
   *
   * <p>{@code deviceTrustedUserId} is who the device cookie belongs to, or null for a browser that
   * has never been trusted. It is compared inside the chain, after the password, and never replaces
   * it: a trusted device presenting the wrong password fails exactly where a wrong password always
   * failed. It is an id rather than a boolean on purpose -- a boolean would let a device trusted by
   * one account waive the second factor for another, and that should be impossible by construction
   * rather than by there happening to be one user.
   */
  public Optional<User> authenticate(
      String username, String password, String otp, Long deviceTrustedUserId) {
    return users
        .findByUsername(username)
        .filter(u -> passwordEncoder.matches(password, u.getPasswordHash()))
        .filter(u -> trusts(u, deviceTrustedUserId) || totpService.verify(u.getTotpSecret(), otp));
  }

  private static boolean trusts(User user, Long deviceTrustedUserId) {
    return deviceTrustedUserId != null && deviceTrustedUserId.equals(user.getId());
  }

  /** The signed-in user, by name. For callers that hold a Principal and need the row. */
  public Optional<User> findByUsername(String username) {
    return users.findByUsername(username);
  }

  /** Issues a new opaque refresh token, storing only its SHA-256 hash. */
  @Transactional
  public String issueRefreshToken(User user) {
    String token = randomUrlSafeToken();
    OffsetDateTime expiresAt = OffsetDateTime.now().plusDays(props.refreshTokenDays());
    refreshTokens.save(new RefreshToken(user.getId(), sha256(token), expiresAt));
    return token;
  }

  /**
   * Renew a session from the token presented in the cookie.
   *
   * <p>Renewing is not the same as rotating, and conflating them is what made this app ask for a
   * password and a TOTP code every day or so. The access cookie lasts minutes and the session lasts
   * months, but the only way to mint a new access cookie was to spend the session token -- so an
   * ordinary day burned dozens of rotations, and every one of them was a chance for the response to
   * be lost and the client to be left holding something the server had already revoked. A phone
   * suspending mid-request or a pod replaced mid-deploy was enough.
   *
   * <p>So a renewal now slides the session's expiry and hands the same token back. The token is
   * only replaced once it is older than {@code refresh-rotate-hours}, which turns thousands of
   * rotations a year into a few hundred, each one far likelier to complete. This is the ordinary
   * shape of a first-party web session: use it and it stays alive, abandon it for the whole window
   * and it dies.
   *
   * <p>Rotation is kept rather than dropped because it still bounds how long a token that leaked is
   * worth anything, and because the session is revocable either way -- it is a hash in a table, not
   * a bearer JWT.
   */
  @Transactional
  public Optional<RenewedSession> renew(String presentedToken) {
    Optional<RefreshToken> stored = refreshTokens.findByTokenHash(sha256(presentedToken));
    if (stored.isEmpty()) {
      // Not a token this server ever issued: a stale cookie from before the database was reset,
      // or a different deployment. Distinct from a revoked one, and worth telling apart.
      log.info("Refresh presented a token that is not on file.");
      return Optional.empty();
    }
    return stored.flatMap(t -> renewStored(t, presentedToken));
  }

  private Optional<RenewedSession> renewStored(RefreshToken stored, String presentedToken) {
    if (stored.isRevoked()) {
      if (!withinRotationGrace(stored)) {
        log.warn(
            "Refresh token reuse for user {}: rotated at {}, outside the {} grace. "
                + "Revoking every session for this user.",
            stored.getUserId(),
            stored.getRotatedAt(),
            ROTATION_GRACE);
        revokeAllForUser(stored.getUserId());
        return Optional.empty();
      }
      log.info(
          "Refresh token for user {} was rotated at {}, inside the grace window: "
              + "issuing a replacement rather than treating it as reuse.",
          stored.getUserId(),
          stored.getRotatedAt());
      // Lost the race, or never heard the answer to the rotation it won. Give it one of its own:
      // only the hash of a token is stored, so the successor cannot be handed out twice even in
      // principle, and several live tokens for one user is already normal across devices.
      return issueFor(stored.getUserId());
    }
    if (isExpired(stored)) {
      log.info("Session for user {} expired at {}.", stored.getUserId(), stored.getExpiresAt());
      return Optional.empty();
    }
    if (dueForRotation(stored)) {
      stored.markRotated(OffsetDateTime.now());
      refreshTokens.save(stored);
      return issueFor(stored.getUserId());
    }
    stored.renewUntil(OffsetDateTime.now().plusDays(props.refreshTokenDays()));
    refreshTokens.save(stored);
    return users.findById(stored.getUserId()).map(u -> new RenewedSession(u, presentedToken));
  }

  /**
   * Age, not use. Rotating on a clock rather than on every renewal is the whole point: it makes the
   * number of rotations a function of how long you have been signed in rather than of how often you
   * open the app.
   */
  private boolean dueForRotation(RefreshToken stored) {
    OffsetDateTime issuedAt = stored.getCreatedAt();
    // A row from before this column was written by the application. Rotate it and move on.
    return issuedAt == null
        || issuedAt.plusHours(props.refreshRotateHours()).isBefore(OffsetDateTime.now());
  }

  private Optional<RenewedSession> issueFor(Long userId) {
    return users.findById(userId).map(u -> new RenewedSession(u, issueRefreshToken(u)));
  }

  /**
   * True for a token rotated away recently. A token revoked by logout carries no rotation instant
   * and is never in grace: presenting one is not a race.
   */
  private static boolean withinRotationGrace(RefreshToken stored) {
    OffsetDateTime rotatedAt = stored.getRotatedAt();
    return rotatedAt != null && rotatedAt.plus(ROTATION_GRACE).isAfter(OffsetDateTime.now());
  }

  @Transactional
  public void revoke(String presentedToken) {
    refreshTokens
        .findByTokenHash(sha256(presentedToken))
        .ifPresent(
            t -> {
              t.revoke();
              refreshTokens.save(t);
            });
  }

  private void revokeAllForUser(Long userId) {
    List<RefreshToken> active = refreshTokens.findByUserIdAndRevokedFalse(userId);
    active.forEach(RefreshToken::revoke);
    refreshTokens.saveAll(active);
  }

  private static boolean isExpired(RefreshToken token) {
    return !token.getExpiresAt().isAfter(OffsetDateTime.now());
  }

  private String randomUrlSafeToken() {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  static String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * @param sessionToken what belongs in the cookie now -- the same token when the session was
   *     merely renewed, a new one when it was rotated. The caller does not need to know which.
   */
  public record RenewedSession(User user, String sessionToken) {}
}
