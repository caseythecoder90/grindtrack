package dev.grindtrack.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * The {@code grindtrack.assistant.*} block: everything about calling a model.
 *
 * <p>Separate from {@link AppProperties} because absence means something different here. A missing
 * JWT secret is a broken deployment; a missing API key is a feature switched off. The assistant
 * endpoints answer 503 with a sentence, and every other part of the app neither knows nor cares.
 *
 * @param apiKey from the {@code ANTHROPIC_API_KEY} env var, which comes from a Kubernetes secret.
 *     Blank means the assistant is off.
 * @param model the model for the brief, the review, the week plan and the chat
 * @param lightModel the model for the short, plain jobs (a passage explained); blank means the main
 *     model
 * @param zone the timezone both schedules fire in, because "Friday at five" and "six in the
 *     morning" mean the owner's, not the pod's UTC
 * @param briefCron when the morning brief drafts itself; before the morning block, so it is waiting
 *     rather than loading
 */
@ConfigurationProperties(prefix = "grindtrack.assistant")
public record AssistantProperties(
    String apiKey,
    String model,
    String lightModel,
    String zone,
    String reviewCron,
    String briefCron) {

  @ConstructorBinding
  public AssistantProperties {}

  /** Without a lighter model: everything on the one model. Tests, mostly. */
  public AssistantProperties(
      String apiKey, String model, String zone, String reviewCron, String briefCron) {
    this(apiKey, model, null, zone, reviewCron, briefCron);
  }

  /**
   * The model for the short, plain jobs — a passage explained — where the cheaper tier does the
   * work as well. Falls back to the main model when none is configured.
   */
  public String lightModel() {
    return lightModel == null || lightModel.isBlank() ? model : lightModel;
  }

  public boolean configured() {
    return apiKey != null && !apiKey.isBlank();
  }
}
