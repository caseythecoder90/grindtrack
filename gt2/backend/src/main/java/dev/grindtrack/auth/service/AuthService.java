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
   * Rotation: validate the presented token, revoke it, issue a replacement. A stolen refresh token
   * is single-use — presenting an already-rotated token is treated as theft, and every live token
   * for that user is revoked (forcing a fresh password+TOTP login everywhere).
   */
  @Transactional
  public Optional<RotatedTokens> rotate(String presentedToken) {
    return refreshTokens.findByTokenHash(sha256(presentedToken)).flatMap(this::rotateStoredToken);
  }

  private Optional<RotatedTokens> rotateStoredToken(RefreshToken stored) {
    if (stored.isRevoked()) {
      revokeAllForUser(stored.getUserId());
      return Optional.empty();
    }
    if (isExpired(stored)) {
      return Optional.empty();
    }
    stored.revoke();
    refreshTokens.save(stored);
    return users.findById(stored.getUserId()).map(u -> new RotatedTokens(u, issueRefreshToken(u)));
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
