package dev.grindtrack.push.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * RFC 8291: a payload encrypted so that only the subscribing browser can read it.
 *
 * <p>The push services relay bytes; this is what keeps a sentence of the brief unread on the way
 * through. The scheme is ECDH between a one-off key pair and the browser's, two rounds of HKDF
 * salted with the browser's auth secret and a fresh salt, and AES-128-GCM over one record. The
 * result is the {@code aes128gcm} body of RFC 8188: a header naming the salt and our public key,
 * then the ciphertext.
 *
 * <p>Every step is one JDK primitive. The usual library for this brings BouncyCastle for the same
 * primitives, which is a large dependency for two messages a day. Correctness is checked against
 * the worked example in the RFC's appendix, byte for byte — the ephemeral key and salt are
 * parameters so the test can feed in the example's fixed ones.
 */
public final class PayloadCipher {

  /** The one record size every push service accepts. Our payloads are a few hundred bytes. */
  static final int RECORD_SIZE = 4096;

  private static final int TAG_BYTES = 16;
  private static final int SALT_BYTES = 16;
  private static final int KEY_BYTES = 16;
  private static final int NONCE_BYTES = 12;

  /** Room for the GCM tag and the one-byte record delimiter. */
  private static final int MAX_PLAINTEXT = RECORD_SIZE - TAG_BYTES - 1;

  private static final byte[] KEY_INFO_PREFIX = ascii("WebPush: info\0");
  private static final byte[] CEK_INFO = ascii("Content-Encoding: aes128gcm\0");
  private static final byte[] NONCE_INFO = ascii("Content-Encoding: nonce\0");

  private static final SecureRandom RANDOM = new SecureRandom();

  private PayloadCipher() {}

  /**
   * @param receiverPublic the subscription's {@code p256dh}, decoded: a 65-byte P-256 point
   * @param authSecret the subscription's {@code auth}, decoded: 16 bytes
   */
  public static byte[] encrypt(byte[] plaintext, byte[] receiverPublic, byte[] authSecret) {
    byte[] salt = new byte[SALT_BYTES];
    RANDOM.nextBytes(salt);
    return encrypt(plaintext, receiverPublic, authSecret, freshKeyPair(), salt);
  }

  static byte[] encrypt(
      byte[] plaintext, byte[] receiverPublic, byte[] authSecret, KeyPair sender, byte[] salt) {
    if (plaintext.length > MAX_PLAINTEXT) {
      throw new IllegalArgumentException(
          "a push payload is at most " + MAX_PLAINTEXT + " bytes, got " + plaintext.length);
    }
    if (authSecret.length != 16) {
      throw new IllegalArgumentException("the auth secret must be 16 bytes");
    }
    ECPublicKey receiver = Vapid.publicKey(receiverPublic);
    byte[] senderPublic = Vapid.raw((ECPublicKey) sender.getPublic());

    byte[] sharedSecret = agree((ECPrivateKey) sender.getPrivate(), receiver);
    // First round: the browser's auth secret salts the ECDH result, with both public keys as info,
    // so a key agreement replayed against a different subscription yields a different key.
    byte[] prkKey = hmac(authSecret, sharedSecret);
    byte[] ikm = hkdfExpand(prkKey, concat(KEY_INFO_PREFIX, receiverPublic, senderPublic), 32);
    // Second round: the fresh salt, giving the content key and nonce for this one message.
    byte[] prk = hmac(salt, ikm);
    byte[] cek = hkdfExpand(prk, CEK_INFO, KEY_BYTES);
    byte[] nonce = hkdfExpand(prk, NONCE_INFO, NONCE_BYTES);

    // One record: the plaintext, then 0x02 marking it as the last (and only) record.
    byte[] record = Arrays.copyOf(plaintext, plaintext.length + 1);
    record[plaintext.length] = 0x02;
    byte[] sealed = aesGcm(cek, nonce, record);

    ByteBuffer out = ByteBuffer.allocate(SALT_BYTES + 4 + 1 + senderPublic.length + sealed.length);
    out.put(salt).putInt(RECORD_SIZE).put((byte) senderPublic.length).put(senderPublic).put(sealed);
    return out.array();
  }

  static KeyPair freshKeyPair() {
    try {
      KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
      gen.initialize(new ECGenParameterSpec("secp256r1"), RANDOM);
      return gen.generateKeyPair();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("P-256 is not available", e);
    }
  }

  private static byte[] agree(ECPrivateKey ours, ECPublicKey theirs) {
    try {
      KeyAgreement ecdh = KeyAgreement.getInstance("ECDH");
      ecdh.init(ours);
      ecdh.doPhase(theirs, true);
      return ecdh.generateSecret();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("ECDH failed", e);
    }
  }

  /** HKDF-Expand for outputs up to one hash block, which is all this scheme ever asks for. */
  private static byte[] hkdfExpand(byte[] prk, byte[] info, int length) {
    byte[] block = hmac(prk, concat(info, new byte[] {0x01}));
    return Arrays.copyOf(block, length);
  }

  private static byte[] hmac(byte[] key, byte[] data) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      return mac.doFinal(data);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("HMAC-SHA256 is not available", e);
    }
  }

  private static byte[] aesGcm(byte[] key, byte[] nonce, byte[] data) {
    try {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.ENCRYPT_MODE,
          new SecretKeySpec(key, "AES"),
          new GCMParameterSpec(TAG_BYTES * 8, nonce));
      return cipher.doFinal(data);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("AES-GCM failed", e);
    }
  }

  private static byte[] concat(byte[]... parts) {
    int total = 0;
    for (byte[] p : parts) {
      total += p.length;
    }
    byte[] out = new byte[total];
    int at = 0;
    for (byte[] p : parts) {
      System.arraycopy(p, 0, out, at, p.length);
      at += p.length;
    }
    return out;
  }

  private static byte[] ascii(String s) {
    return s.getBytes(StandardCharsets.US_ASCII);
  }
}
