package dev.grindtrack.auth.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.codec.binary.Base32;
import org.springframework.stereotype.Service;

/**
 * RFC 6238 TOTP, implemented directly so the mechanism is visible: HMAC-SHA1 over a 30-second
 * counter, dynamic truncation, mod 10^6. Compatible with Google Authenticator, Authy, 1Password.
 */
@Service
public class TotpService {

  private static final int PERIOD_SECONDS = 30;
  private static final int DIGITS = 6;
  private static final int WINDOW = 1; // accept previous/current/next step for clock drift

  private final SecureRandom random = new SecureRandom();

  /** Generates a new 160-bit secret, Base32-encoded (the format authenticator apps expect). */
  public String generateSecret() {
    byte[] bytes = new byte[20];
    random.nextBytes(bytes);
    return new Base32().encodeToString(bytes).replace("=", "");
  }

  /** The otpauth:// URI to load into an authenticator app (paste into any QR generator). */
  public String provisioningUri(String username, String secret) {
    return "otpauth://totp/Grindtrack:"
        + username
        + "?secret="
        + secret
        + "&issuer=Grindtrack&algorithm=SHA1&digits=6&period=30";
  }

  /** Verifies a submitted 6-digit code against the secret, tolerating +/-1 time step. */
  public boolean verify(String secret, String code) {
    if (code == null || !code.matches("\\d{6}")) {
      return false;
    }
    long currentStep = Instant.now().getEpochSecond() / PERIOD_SECONDS;
    boolean match = false;
    for (long step = currentStep - WINDOW; step <= currentStep + WINDOW; step++) {
      // Constant-time comparison, and no early exit: don't leak which time step matched.
      match |=
          MessageDigest.isEqual(
              generateCode(secret, step).getBytes(StandardCharsets.UTF_8),
              code.getBytes(StandardCharsets.UTF_8));
    }
    return match;
  }

  private String generateCode(String secret, long step) {
    try {
      byte[] key = new Base32().decode(secret);
      byte[] counter = ByteBuffer.allocate(8).putLong(step).array();
      Mac mac = Mac.getInstance("HmacSHA1");
      mac.init(new SecretKeySpec(key, "HmacSHA1"));
      byte[] hash = mac.doFinal(counter);
      int offset = hash[hash.length - 1] & 0x0f; // dynamic truncation (RFC 4226 s5.3)
      int binary =
          ((hash[offset] & 0x7f) << 24)
              | ((hash[offset + 1] & 0xff) << 16)
              | ((hash[offset + 2] & 0xff) << 8)
              | (hash[offset + 3] & 0xff);
      return String.format("%06d", binary % 1_000_000);
    } catch (Exception e) {
      throw new IllegalStateException("TOTP generation failed", e);
    }
  }
}
