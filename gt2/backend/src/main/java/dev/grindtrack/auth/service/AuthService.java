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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Login (password + TOTP), refresh-token issuance, rotation, and revocation. */
@Service
public class AuthService {

  /**
   * How long after a rotation the superseded token is still treated as a race rather than as theft.
   *
   * <p>A legitimate client presents an already-rotated token in two ordinary situations: two
   * windows sharing one cookie jar refresh at the same moment, and a rotation whose response never
   * reached the client (a pod replaced mid-deploy, a phone suspended). Both land within a second or
   * two of the rotation they lost. A replay by someone holding a stolen cookie has no such
   * deadline, so a minute separates the two cases with room to spare while leaving the cascade in
   * force for anything later.
   */
  private static final Duration ROTATION_GRACE = Duration.ofMinutes(1);

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
    return users
        .findByUsername(username)
        .filter(u -> passwordEncoder.matches(password, u.getPasswordHash()))
        .filter(u -> totpService.verify(u.getTotpSecret(), otp));
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
   * Rotation: validate the presented token, revoke it, issue a replacement.
   *
   * <p>A refresh token is single-use. Presenting one that has already been rotated away is treated
   * as theft once it is outside {@link #ROTATION_GRACE}: every live token for that user is revoked,
   * forcing a fresh password+TOTP login everywhere. Inside the window it is treated as the race it
   * almost certainly is, and the caller gets a token of its own.
   */
  @Transactional
  public Optional<RotatedTokens> rotate(String presentedToken) {
    return refreshTokens.findByTokenHash(sha256(presentedToken)).flatMap(this::rotateStoredToken);
  }

  private Optional<RotatedTokens> rotateStoredToken(RefreshToken stored) {
    if (stored.isRevoked()) {
      if (!withinRotationGrace(stored)) {
        revokeAllForUser(stored.getUserId());
        return Optional.empty();
      }
      // The loser of a rotation race gets a token of its own rather than the winner's: only the
      // hash of a token is stored, so the winner's cannot be handed out a second time even in
      // principle. Several live tokens for one user is already the normal state across devices.
      return issueFor(stored.getUserId());
    }
    if (isExpired(stored)) {
      return Optional.empty();
    }
    stored.markRotated(OffsetDateTime.now());
    refreshTokens.save(stored);
    return issueFor(stored.getUserId());
  }

  private Optional<RotatedTokens> issueFor(Long userId) {
    return users.findById(userId).map(u -> new RotatedTokens(u, issueRefreshToken(u)));
  }

  /**
   * True for a token rotated away moments ago. A token revoked by logout carries no rotation
   * instant and is never in grace: presenting one is not a race.
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

  public record RotatedTokens(User user, String newRefreshToken) {}
}
