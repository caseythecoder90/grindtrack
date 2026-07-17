package dev.grindtrack.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TotpServiceTest {

  /** RFC 6238 Appendix B reference secret: ASCII "12345678901234567890", Base32-encoded. */
  private static final String RFC_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

  // HOTP codes for the RFC secret at consecutive counter values (RFC 4226 Appendix D).
  private static final String CODE_STEP_1 = "287082";
  private static final String CODE_STEP_2 = "359152";
  private static final String CODE_STEP_3 = "969429";
  private static final String CODE_STEP_4 = "338314";
  private static final String CODE_STEP_5 = "254676";

  /** Clock fixed at t=90s..119s puts the current TOTP step at 3. */
  private static final TotpService AT_STEP_3 = totpAt(90);

  private static TotpService totpAt(long epochSeconds) {
    return new TotpService(Clock.fixed(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC));
  }

  @Test
  void generateCodeMatchesRfc6238TestVectors() {
    TotpService totp = new TotpService();
    assertThat(totp.generateCode(RFC_SECRET, 59 / 30)).isEqualTo("287082");
    assertThat(totp.generateCode(RFC_SECRET, 1111111109L / 30)).isEqualTo("081804");
    assertThat(totp.generateCode(RFC_SECRET, 1111111111L / 30)).isEqualTo("050471");
    assertThat(totp.generateCode(RFC_SECRET, 1234567890L / 30)).isEqualTo("005924");
    assertThat(totp.generateCode(RFC_SECRET, 2000000000L / 30)).isEqualTo("279037");
  }

  @Test
  void verifyAcceptsCurrentStepCode() {
    assertThat(AT_STEP_3.verify(RFC_SECRET, CODE_STEP_3)).isTrue();
  }

  @Test
  void verifyAcceptsPreviousStepCodeForClockDrift() {
    assertThat(AT_STEP_3.verify(RFC_SECRET, CODE_STEP_2)).isTrue();
  }

  @Test
  void verifyAcceptsNextStepCodeForClockDrift() {
    assertThat(AT_STEP_3.verify(RFC_SECRET, CODE_STEP_4)).isTrue();
  }

  @Test
  void verifyRejectsCodeTwoStepsBehind() {
    assertThat(AT_STEP_3.verify(RFC_SECRET, CODE_STEP_1)).isFalse();
  }

  @Test
  void verifyRejectsCodeTwoStepsAhead() {
    assertThat(AT_STEP_3.verify(RFC_SECRET, CODE_STEP_5)).isFalse();
  }

  @Test
  void verifyRejectsMalformedInput() {
    assertThat(AT_STEP_3.verify(RFC_SECRET, null)).isFalse();
    assertThat(AT_STEP_3.verify(RFC_SECRET, "")).isFalse();
    assertThat(AT_STEP_3.verify(RFC_SECRET, "12345")).isFalse();
    assertThat(AT_STEP_3.verify(RFC_SECRET, "1234567")).isFalse();
    assertThat(AT_STEP_3.verify(RFC_SECRET, "abcdef")).isFalse();
    assertThat(AT_STEP_3.verify(RFC_SECRET, "96942x")).isFalse();
  }

  @Test
  void verifyRejectsWellFormedButWrongCode() {
    // 000000 is none of the step 2/3/4 codes for the RFC secret.
    assertThat(AT_STEP_3.verify(RFC_SECRET, "000000")).isFalse();
  }

  @Test
  void generatedSecretIsBase32WithoutPadding() {
    TotpService totp = new TotpService();
    String secret = totp.generateSecret();
    // 20 random bytes encode to exactly 32 Base32 characters.
    assertThat(secret).matches("[A-Z2-7]{32}");
    assertThat(totp.generateSecret()).isNotEqualTo(secret);
  }

  @Test
  void generatedSecretRoundTripsThroughVerify() {
    TotpService totp = totpAt(90);
    String secret = totp.generateSecret();
    assertThat(totp.verify(secret, totp.generateCode(secret, 3))).isTrue();
  }

  @Test
  void provisioningUriEmbedsUserAndSecret() {
    String uri = new TotpService().provisioningUri("casey", RFC_SECRET);
    assertThat(uri)
        .startsWith("otpauth://totp/Grindtrack:casey?")
        .contains("secret=" + RFC_SECRET)
        .contains("issuer=Grindtrack")
        .contains("digits=6")
        .contains("period=30");
  }
}
