package dev.grindtrack.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The {@code grindtrack.speech.*} block: speech to text for the ask box.
 *
 * <p>Absent means off, like the assistant without its key: the status endpoint says so, the mic
 * button is not rendered, and the relay refuses a connection with a sentence.
 *
 * @param apiKey from the {@code OPENAI_API_KEY} env var, which comes from the Kubernetes secret
 * @param model the transcription model. {@code gpt-4o-mini-transcribe} is the cheap one — about a
 *     third of a cent a minute — and transcribes each phrase once the server hears you pause;
 *     {@code gpt-live-transcribe} streams word by word at several times the price
 * @param language ISO-639-1; naming it improves accuracy and latency
 */
@ConfigurationProperties(prefix = "grindtrack.speech")
public record SpeechProperties(String apiKey, String model, String language) {

  public boolean configured() {
    return apiKey != null && !apiKey.isBlank();
  }
}
