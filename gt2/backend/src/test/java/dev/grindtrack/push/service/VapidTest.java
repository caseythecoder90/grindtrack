package dev.grindtrack.push.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.grindtrack.config.PushProperties;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/** The sender's signature: verifiable with the public half, addressed to the right push service. */
class VapidTest {

  private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder B64D = Base64.getUrlDecoder();

  /** A fresh pair in the raw form the configuration carries, as the docs' one-liner produces. */
  static PushProperties freshProperties(String subject) {
    KeyPair pair = PayloadCipher.freshKeyPair();
    byte[] scalar = ((ECPrivateKey) pair.getPrivate()).getS().toByteArray();
    byte[] raw32 = new byte[32];
    int skip = Math.max(0, scalar.length - 32);
    System.arraycopy(scalar, skip, raw32, 32 - (scalar.length - skip), scalar.length - skip);
    return new PushProperties(
        B64.encodeToString(Vapid.raw((ECPublicKey) pair.getPublic())),
        B64.encodeToString(raw32),
        subject);
  }

  @Test
  void theTokenVerifiesWithThePublicKeyAndNamesThePushServiceAndTheSubject() throws Exception {
    PushProperties props = freshProperties("mailto:casey@example.com");
    Vapid vapid = Vapid.from(props);
    Instant now = Instant.parse("2026-09-12T10:00:00Z");

    String header = vapid.authorization("https://web.push.apple.com/QGb5zW0Z9nJ3fXkq", now);

    assertThat(header).startsWith("vapid t=").contains(", k=" + props.vapidPublicKey());
    String jwt = header.substring("vapid t=".length(), header.indexOf(", k="));
    String[] parts = jwt.split("\\.");
    assertThat(parts).hasSize(3);

    Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
    verifier.initVerify(vapid.publicKey());
    verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
    assertThat(verifier.verify(B64D.decode(parts[2]))).isTrue();

    JsonNode head = new ObjectMapper().readTree(B64D.decode(parts[0]));
    assertThat(head.get("alg").asText()).isEqualTo("ES256");
    JsonNode claims = new ObjectMapper().readTree(B64D.decode(parts[1]));
    assertThat(claims.get("aud").asText()).isEqualTo("https://web.push.apple.com");
    assertThat(claims.get("exp").asLong()).isEqualTo(now.getEpochSecond() + 12 * 3600);
    assertThat(claims.get("sub").asText()).isEqualTo("mailto:casey@example.com");
  }

  @Test
  void aTokenSignedWithAnotherKeyDoesNotVerify() throws Exception {
    Vapid ours = Vapid.from(freshProperties(null));
    Vapid theirs = Vapid.from(freshProperties(null));
    String header = theirs.authorization("https://fcm.googleapis.com/fcm/send/abc", Instant.now());
    String jwt = header.substring("vapid t=".length(), header.indexOf(", k="));
    String[] parts = jwt.split("\\.");

    Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
    verifier.initVerify(ours.publicKey());
    verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
    assertThat(verifier.verify(B64D.decode(parts[2]))).isFalse();
  }

  /** The raw form round-trips, including coordinates that happen to start with a zero byte. */
  @Test
  void rawKeysRoundTrip() {
    for (int i = 0; i < 20; i++) {
      byte[] raw = Vapid.raw((ECPublicKey) PayloadCipher.freshKeyPair().getPublic());
      assertThat(raw).hasSize(65);
      assertThat(Vapid.raw(Vapid.publicKey(raw))).isEqualTo(raw);
    }
    BigInteger x =
        Vapid.publicKey(B64D.decode(freshProperties(null).vapidPublicKey())).getW().getAffineX();
    assertThat(x.bitLength()).isLessThanOrEqualTo(256);
  }

  @Test
  void misconfiguredKeysFailLoudlyWithTheVariableNamed() {
    assertThatThrownBy(() -> Vapid.from(new PushProperties("not base64!", "AAAA", null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("PUSH_VAPID_PUBLIC_KEY");
    String pub = freshProperties(null).vapidPublicKey();
    assertThatThrownBy(
            () -> Vapid.from(new PushProperties(pub, B64.encodeToString(new byte[31]), null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("PUSH_VAPID_PRIVATE_KEY");
    byte[] compressed = Arrays.copyOf(B64D.decode(pub), 33);
    assertThatThrownBy(
            () -> Vapid.from(new PushProperties(B64.encodeToString(compressed), pub, null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("65-byte");
  }
}
