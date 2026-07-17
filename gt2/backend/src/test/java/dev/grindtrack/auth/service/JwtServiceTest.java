package dev.grindtrack.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.grindtrack.config.AppProperties;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

  private static final String SECRET = "0123456789abcdef0123456789abcdef"; // 256-bit HS256 key

  private static JwtService serviceWithTtlMinutes(int minutes) {
    return new JwtService(new AppProperties(SECRET, minutes, 30, true, null, null));
  }

  @Test
  void roundTripsSubject() {
    JwtService jwt = serviceWithTtlMinutes(15);
    String token = jwt.issueAccessToken("casey");
    assertThat(jwt.validate(token)).contains("casey");
  }

  @Test
  void rejectsTamperedPayload() {
    JwtService jwt = serviceWithTtlMinutes(15);
    String[] parts = jwt.issueAccessToken("casey").split("\\.");
    String tampered = parts[0] + "." + flipFirstChar(parts[1]) + "." + parts[2];
    assertThat(jwt.validate(tampered)).isEmpty();
  }

  @Test
  void rejectsTamperedSignature() {
    JwtService jwt = serviceWithTtlMinutes(15);
    String[] parts = jwt.issueAccessToken("casey").split("\\.");
    String tampered = parts[0] + "." + parts[1] + "." + flipFirstChar(parts[2]);
    assertThat(jwt.validate(tampered)).isEmpty();
  }

  @Test
  void rejectsTokenSignedWithDifferentKey() {
    JwtService signer =
        new JwtService(
            new AppProperties("another-secret-key-32-bytes-long!!", 15, 30, true, null, null));
    JwtService verifier = serviceWithTtlMinutes(15);
    assertThat(verifier.validate(signer.issueAccessToken("casey"))).isEmpty();
  }

  @Test
  void rejectsExpiredToken() {
    JwtService jwt = serviceWithTtlMinutes(-1);
    assertThat(jwt.validate(jwt.issueAccessToken("casey"))).isEmpty();
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
