package dev.grindtrack.auth.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
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
  private final Clock clock;

  public TotpService() {
    this(Clock.systemUTC());
  }

  TotpService(Clock clock) {
    this.clock = clock;
  }

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
    long currentStep = clock.instant().getEpochSecond() / PERIOD_SECONDS;
    boolean match = false;
    for (long step = currentStep - WINDOW; step <= currentStep + WINDOW; step++) {
      // Constant-time comparison, and no early exit: don't leak which time step matched.
      match |= constantTimeEquals(generateCode(secret, step), code);
    }
    return match;
  }

  /** The 6-digit code for one specific time step (package-visible for tests). */
  String generateCode(String secret, long step) {
    try {
      byte[] key = new Base32().decode(secret);
      byte[] counter = ByteBuffer.allocate(8).putLong(step).array();
      byte[] hash = hmacSha1(key, counter);
      return String.format("%0" + DIGITS + "d", dynamicTruncation(hash) % 1_000_000);
    } catch (Exception e) {
      throw new IllegalStateException("TOTP generation failed", e);
    }
  }

  private static byte[] hmacSha1(byte[] key, byte[] message) throws GeneralSecurityException {
    Mac mac = Mac.getInstance("HmacSHA1");
    mac.init(new SecretKeySpec(key, "HmacSHA1"));
    return mac.doFinal(message);
  }

  /** RFC 4226 s5.3: a 31-bit big-endian int read at an offset chosen by the hash's last nibble. */
  private static int dynamicTruncation(byte[] hash) {
    int offset = hash[hash.length - 1] & 0x0f;
    return ((hash[offset] & 0x7f) << 24)
        | ((hash[offset + 1] & 0xff) << 16)
        | ((hash[offset + 2] & 0xff) << 8)
        | (hash[offset + 3] & 0xff);
  }

  private static boolean constantTimeEquals(String expected, String presented) {
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8), presented.getBytes(StandardCharsets.UTF_8));
  }
}
