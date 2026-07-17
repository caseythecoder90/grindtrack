package dev.grindtrack.auth.service;

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
 */
@Service
public class JwtService {

  private final SecretKey key;
  private final Duration accessTtl;

  public JwtService(AppProperties props) {
    this.key = Keys.hmacShaKeyFor(props.jwtSecret().getBytes(StandardCharsets.UTF_8));
    this.accessTtl = Duration.ofMinutes(props.accessTokenMinutes());
  }

  public String issueAccessToken(String username) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(username)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(accessTtl)))
        .signWith(key)
        .compact();
  }

  /** Returns the subject if the token parses, is signed by us, and is unexpired. */
  public Optional<String> validate(String token) {
    try {
      Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
      return Optional.ofNullable(claims.getSubject());
    } catch (JwtException | IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
