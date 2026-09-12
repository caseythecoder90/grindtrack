package dev.grindtrack.push.service;

import dev.grindtrack.config.PushProperties;
import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

/**
 * RFC 8292: the header that tells a push service who is sending.
 *
 * <p>A short JWT, signed with the app's P-256 key, saying which push service it is for ({@code
 * aud}), when it stops being valid ({@code exp}) and who to write to about abuse ({@code sub}). The
 * push service checks it against the public key the browser subscribed with, so a message signed by
 * anyone else is refused even if they learned the endpoint.
 *
 * <p>Written on the JDK rather than a JWT library because the library's ES256 wants a {@code
 * PrivateKey} it can only build from PEM, and the key arrives here as the 32 raw bytes every Web
 * Push tool exchanges. Converting raw to PEM to hand to a library is more code than signing.
 */
public final class Vapid {

  /** Twelve hours: comfortably under the day the protocol allows, long past any send. */
  private static final Duration TOKEN_LIFETIME = Duration.ofHours(12);

  private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder B64D = Base64.getUrlDecoder();

  private final ECPublicKey publicKey;
  private final ECPrivateKey privateKey;
  private final String publicKeyBase64;
  private final String subject;

  private Vapid(
      ECPublicKey publicKey, ECPrivateKey privateKey, String publicKeyBase64, String subject) {
    this.publicKey = publicKey;
    this.privateKey = privateKey;
    this.publicKeyBase64 = publicKeyBase64;
    this.subject = subject;
  }

  /**
   * @throws IllegalArgumentException when either key is not the raw form described on {@link
   *     PushProperties} — a misconfiguration worth failing loudly at startup rather than at six in
   *     the morning
   */
  public static Vapid from(PushProperties props) {
    byte[] pub = decode(props.vapidPublicKey(), "PUSH_VAPID_PUBLIC_KEY");
    byte[] priv = decode(props.vapidPrivateKey(), "PUSH_VAPID_PRIVATE_KEY");
    if (pub.length != 65 || pub[0] != 0x04) {
      throw new IllegalArgumentException(
          "PUSH_VAPID_PUBLIC_KEY must be a 65-byte uncompressed P-256 point");
    }
    if (priv.length != 32) {
      throw new IllegalArgumentException("PUSH_VAPID_PRIVATE_KEY must be a 32-byte P-256 scalar");
    }
    String subject = props.subject() == null || props.subject().isBlank() ? null : props.subject();
    return new Vapid(publicKey(pub), privateKey(priv), B64.encodeToString(pub), subject);
  }

  private static byte[] decode(String value, String name) {
    try {
      return B64D.decode(value.trim());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(name + " is not base64url", e);
    }
  }

  /** The public half, base64url, as the browser needs it to subscribe. */
  public String publicKeyBase64() {
    return publicKeyBase64;
  }

  public ECPublicKey publicKey() {
    return publicKey;
  }

  /**
   * The {@code Authorization} header value for one send: {@code vapid t=<jwt>, k=<public key>}.
   *
   * @param endpoint the subscription's endpoint; the token's audience is its origin
   */
  public String authorization(String endpoint, Instant now) {
    URI uri = URI.create(endpoint);
    String audience = uri.getScheme() + "://" + uri.getAuthority();
    long exp = now.plus(TOKEN_LIFETIME).getEpochSecond();
    String claims =
        "{\"aud\":\""
            + audience
            + "\",\"exp\":"
            + exp
            + (subject == null ? "" : ",\"sub\":\"" + subject + "\"")
            + "}";
    String signingInput = b64("{\"typ\":\"JWT\",\"alg\":\"ES256\"}") + "." + b64(claims);
    return "vapid t="
        + signingInput
        + "."
        + B64.encodeToString(sign(signingInput))
        + ", k="
        + publicKeyBase64;
  }

  private byte[] sign(String input) {
    try {
      // P1363 is the raw r||s the JWT spec wants; the JDK's default is DER.
      Signature sig = Signature.getInstance("SHA256withECDSAinP1363Format");
      sig.initSign(privateKey);
      sig.update(input.getBytes(StandardCharsets.US_ASCII));
      return sig.sign();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("could not sign the VAPID token", e);
    }
  }

  private static String b64(String s) {
    return B64.encodeToString(s.getBytes(StandardCharsets.UTF_8));
  }

  // --- raw <-> JDK key conversions, shared with the cipher ---

  static ECParameterSpec p256() {
    try {
      AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
      params.init(new ECGenParameterSpec("secp256r1"));
      return params.getParameterSpec(ECParameterSpec.class);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("P-256 is not available", e);
    }
  }

  /** A 65-byte uncompressed point ({@code 0x04 || x || y}) as a JDK key. */
  static ECPublicKey publicKey(byte[] raw) {
    if (raw.length != 65 || raw[0] != 0x04) {
      throw new IllegalArgumentException("expected a 65-byte uncompressed P-256 point");
    }
    BigInteger x = new BigInteger(1, Arrays.copyOfRange(raw, 1, 33));
    BigInteger y = new BigInteger(1, Arrays.copyOfRange(raw, 33, 65));
    try {
      return (ECPublicKey)
          KeyFactory.getInstance("EC")
              .generatePublic(new ECPublicKeySpec(new ECPoint(x, y), p256()));
    } catch (GeneralSecurityException e) {
      throw new IllegalArgumentException("not a point on P-256", e);
    }
  }

  static ECPrivateKey privateKey(byte[] raw) {
    try {
      return (ECPrivateKey)
          KeyFactory.getInstance("EC")
              .generatePrivate(new ECPrivateKeySpec(new BigInteger(1, raw), p256()));
    } catch (GeneralSecurityException e) {
      throw new IllegalArgumentException("not a P-256 scalar", e);
    }
  }

  /** The 65-byte uncompressed form of a JDK key, as the wire wants it. */
  static byte[] raw(ECPublicKey key) {
    byte[] out = new byte[65];
    out[0] = 0x04;
    copyPadded(key.getW().getAffineX(), out, 1);
    copyPadded(key.getW().getAffineY(), out, 33);
    return out;
  }

  /** A coordinate as exactly 32 big-endian bytes; BigInteger drops leading zeros and adds signs. */
  private static void copyPadded(BigInteger value, byte[] into, int at) {
    byte[] bytes = value.toByteArray();
    int skip = Math.max(0, bytes.length - 32);
    int len = bytes.length - skip;
    System.arraycopy(bytes, skip, into, at + (32 - len), len);
  }
}
