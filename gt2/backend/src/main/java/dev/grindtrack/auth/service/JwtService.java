package dev.grindtrack.auth.service;

import dev.grindtrack.auth.domain.Role;
import dev.grindtrack.auth.domain.User;
import dev.grindtrack.auth.security.SignedIn;
import dev.grindtrack.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Short-lived HS256 access tokens. Refresh tokens are deliberately NOT JWTs — they are opaque
 * random strings tracked (hashed) server-side so they can be revoked and rotated; see AuthService.
 *
 * <p>Three claims: the username as the subject, the account's id ({@code uid}) and its role. The
 * role in the token is what makes authorisation stateless — the filter grants the authority without
 * a lookup — and it can be, because a role never changes for the life of an account. A token
 * without the two claims is one this version did not issue, and is refused; the client's next
 * refresh mints a current one.
 */
@Service
public class JwtService {

  static final String USER_ID_CLAIM = "uid";
  static final String ROLE_CLAIM = "role";

  private final SecretKey key;
  private final Duration accessTtl;

  public JwtService(AppProperties props) {
    this.key = Keys.hmacShaKeyFor(props.jwtSecret().getBytes(StandardCharsets.UTF_8));
    this.accessTtl = Duration.ofMinutes(props.accessTokenMinutes());
  }

  public String issueAccessToken(User user) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(user.getUsername())
        .claim(USER_ID_CLAIM, user.getId())
        .claim(ROLE_CLAIM, user.getRole().name())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(accessTtl)))
        .signWith(key)
        .compact();
  }

  /**
   * Who the token is for, if it parses, is signed by us, is unexpired and carries all three claims.
   */
  public Optional<SignedIn> validate(String token) {
    try {
      Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
      String username = claims.getSubject();
      Object id = claims.get(USER_ID_CLAIM);
      Object role = claims.get(ROLE_CLAIM);
      if (username == null || !(id instanceof Number uid) || !(role instanceof String name)) {
        return Optional.empty();
      }
      return Optional.of(new SignedIn(uid.longValue(), username, Role.valueOf(name)));
    } catch (JwtException | IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
