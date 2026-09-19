package dev.grindtrack.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.grindtrack.auth.domain.Role;
import dev.grindtrack.auth.domain.User;
import dev.grindtrack.auth.security.SignedIn;
import dev.grindtrack.config.AppProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JwtServiceTest {

  private static final String SECRET = "0123456789abcdef0123456789abcdef"; // 256-bit HS256 key

  private static JwtService serviceWithTtlMinutes(int minutes) {
    return new JwtService(new AppProperties(SECRET, minutes, 30, 24, 30, true, null, null));
  }

  private static User account(long id, String username, Role role) {
    User user = new User(username, "hash", "SECRET", role);
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }

  private static User casey() {
    return account(7L, "casey", Role.OWNER);
  }

  @Test
  void roundTripsWhoTheTokenIsFor() {
    JwtService jwt = serviceWithTtlMinutes(15);
    String token = jwt.issueAccessToken(casey());
    assertThat(jwt.validate(token)).contains(new SignedIn(7L, "casey", Role.OWNER));
  }

  @Test
  void carriesAPartnersRoleJustTheSame() {
    JwtService jwt = serviceWithTtlMinutes(15);
    String token = jwt.issueAccessToken(account(8L, "wife", Role.PARTNER));
    assertThat(jwt.validate(token)).contains(new SignedIn(8L, "wife", Role.PARTNER));
  }

  /**
   * A token from before accounts had roles carries a subject and nothing else. It is refused rather
   * than read as the owner's: the refresh that follows mints a current one, and nothing is ever
   * authorised on a claim that is not there.
   */
  @Test
  void refusesATokenWithoutTheAccountClaims() {
    String bare =
        Jwts.builder()
            .subject("casey")
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 60_000))
            .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
            .compact();
    assertThat(serviceWithTtlMinutes(15).validate(bare)).isEmpty();
  }

  @Test
  void refusesARoleItDoesNotKnow() {
    String odd =
        Jwts.builder()
            .subject("casey")
            .claim(JwtService.USER_ID_CLAIM, 7L)
            .claim(JwtService.ROLE_CLAIM, "ADMIN")
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 60_000))
            .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
            .compact();
    assertThat(serviceWithTtlMinutes(15).validate(odd)).isEmpty();
  }

  @Test
  void rejectsTamperedPayload() {
    JwtService jwt = serviceWithTtlMinutes(15);
    String[] parts = jwt.issueAccessToken(casey()).split("\\.");
    String tampered = parts[0] + "." + flipFirstChar(parts[1]) + "." + parts[2];
    assertThat(jwt.validate(tampered)).isEmpty();
  }

  @Test
  void rejectsTamperedSignature() {
    JwtService jwt = serviceWithTtlMinutes(15);
    String[] parts = jwt.issueAccessToken(casey()).split("\\.");
    String tampered = parts[0] + "." + parts[1] + "." + flipFirstChar(parts[2]);
    assertThat(jwt.validate(tampered)).isEmpty();
  }

  @Test
  void rejectsTokenSignedWithDifferentKey() {
    JwtService signer =
        new JwtService(
            new AppProperties(
                "another-secret-key-32-bytes-long!!", 15, 30, 24, 30, true, null, null));
    JwtService verifier = serviceWithTtlMinutes(15);
    assertThat(verifier.validate(signer.issueAccessToken(casey()))).isEmpty();
  }

  @Test
  void rejectsExpiredToken() {
    JwtService jwt = serviceWithTtlMinutes(-1);
    assertThat(jwt.validate(jwt.issueAccessToken(casey()))).isEmpty();
  }

  @Test
  void rejectsGarbage() {
    JwtService jwt = serviceWithTtlMinutes(15);
    assertThat(jwt.validate(null)).isEmpty();
    assertThat(jwt.validate("")).isEmpty();
    assertThat(jwt.validate("not.a.jwt")).isEmpty();
  }

  private static String flipFirstChar(String s) {
    return flipChar(s.charAt(0)) + s.substring(1);
  }

  private static String flipChar(char c) {
    return String.valueOf(c == 'A' ? 'B' : 'A');
  }
}
