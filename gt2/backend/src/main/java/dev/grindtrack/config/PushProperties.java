package dev.grindtrack.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The {@code grindtrack.push.*} block: the VAPID pair that identifies this app to the push
 * services.
 *
 * <p>Absent means off, in the same sense as {@link AssistantProperties}: the status endpoint says
 * so, subscribing answers 503 with a sentence, the schedulers skip their push line, and nothing
 * else notices. Both halves come from the Kubernetes secret; the public half is handed to browsers
 * when they subscribe, the private half signs every send and goes nowhere.
 *
 * @param vapidPublicKey the uncompressed P-256 point, 65 bytes, base64url
 * @param vapidPrivateKey the P-256 scalar, 32 bytes, base64url
 * @param subject a {@code mailto:} the push services may write to about abuse — required by the
 *     protocol, read by nobody in practice
 */
@ConfigurationProperties(prefix = "grindtrack.push")
public record PushProperties(String vapidPublicKey, String vapidPrivateKey, String subject) {

  public boolean configured() {
    return present(vapidPublicKey) && present(vapidPrivateKey);
  }

  private static boolean present(String value) {
    return value != null && !value.isBlank();
  }
}
