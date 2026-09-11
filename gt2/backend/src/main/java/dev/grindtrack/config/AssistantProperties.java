package dev.grindtrack.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The {@code grindtrack.assistant.*} block: everything about calling a model.
 *
 * <p>Separate from {@link AppProperties} because absence means something different here. A missing
 * JWT secret is a broken deployment; a missing API key is a feature switched off. The assistant
 * endpoints answer 503 with a sentence, and every other part of the app neither knows nor cares.
 *
 * @param apiKey from the {@code ANTHROPIC_API_KEY} env var, which comes from a Kubernetes secret.
 *     Blank means the assistant is off.
 * @param zone the timezone the review schedule fires in, because "Friday at five" means the owner's
 *     Friday, not the pod's UTC
 */
@ConfigurationProperties(prefix = "grindtrack.assistant")
public record AssistantProperties(String apiKey, String model, String zone, String reviewCron) {

  public boolean configured() {
    return apiKey != null && !apiKey.isBlank();
  }
}
