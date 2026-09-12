package dev.grindtrack.push.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * RFC 8291 Appendix A, byte for byte.
 *
 * <p>The one place a hand-written cipher can be checked rather than trusted: the RFC walks a fixed
 * message through fixed keys and a fixed salt and prints the body that must come out. If any step —
 * the info strings, the order of the public keys, the record delimiter, the header layout — is off
 * by a byte, this fails and every browser would have silently discarded every push.
 */
class PayloadCipherTest {

  private static final Base64.Decoder B64D = Base64.getUrlDecoder();
  private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

  private static final String PLAINTEXT = "When I grow up, I want to be a watermelon";
  private static final String RECEIVER_PUBLIC =
      "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
  private static final String SENDER_PRIVATE = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";
  private static final String SENDER_PUBLIC =
      "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
  private static final String AUTH_SECRET = "BTBZMqHH6r4Tts7J_aSIgg";
  private static final String SALT = "DGv6ra1nlYgDCS1FRnbzlw";
  private static final String EXPECTED_BODY =
      "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6"
          + "TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Q"
          + "ulcy4a-fN";

  @Test
  void producesTheBodyInTheRfcsWorkedExample() {
    KeyPair sender =
        new KeyPair(
            Vapid.publicKey(B64D.decode(SENDER_PUBLIC)),
            Vapid.privateKey(B64D.decode(SENDER_PRIVATE)));

    byte[] body =
        PayloadCipher.encrypt(
            PLAINTEXT.getBytes(StandardCharsets.UTF_8),
            B64D.decode(RECEIVER_PUBLIC),
            B64D.decode(AUTH_SECRET),
            sender,
            B64D.decode(SALT));

    assertThat(B64.encodeToString(body)).isEqualTo(EXPECTED_BODY);
  }

  /** The header the RFC specifies: salt, record size 4096 big-endian, key length 65, the key. */
  @Test
  void theHeaderNamesTheSaltTheRecordSizeAndOurKey() {
    byte[] body =
        PayloadCipher.encrypt(
            "hi".getBytes(StandardCharsets.UTF_8),
            B64D.decode(RECEIVER_PUBLIC),
            B64D.decode(AUTH_SECRET));

    assertThat(body).hasSizeGreaterThan(86);
    assertThat(body[16]).isEqualTo((byte) 0);
    assertThat(body[17]).isEqualTo((byte) 0);
    assertThat(body[18]).isEqualTo((byte) 0x10);
    assertThat(body[19]).isEqualTo((byte) 0);
    assertThat(body[20]).isEqualTo((byte) 65);
    assertThat(body[21]).isEqualTo((byte) 0x04);
    // "hi" + delimiter + 16-byte tag
    assertThat(body.length).isEqualTo(16 + 4 + 1 + 65 + 2 + 1 + 16);
  }

  @Test
  void aPayloadThatDoesNotFitOneRecordIsRefusedRatherThanTruncated() {
    byte[] tooBig = new byte[4096];
    assertThatThrownBy(
            () ->
                PayloadCipher.encrypt(
                    tooBig, B64D.decode(RECEIVER_PUBLIC), B64D.decode(AUTH_SECRET)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at most");
  }

  @Test
  void anAuthSecretOfTheWrongSizeIsRefused() {
    assertThatThrownBy(
            () ->
                PayloadCipher.encrypt(
                    "hi".getBytes(StandardCharsets.UTF_8),
                    B64D.decode(RECEIVER_PUBLIC),
                    new byte[8]))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("16 bytes");
  }
}
