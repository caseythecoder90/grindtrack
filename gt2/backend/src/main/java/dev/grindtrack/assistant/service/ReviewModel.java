package dev.grindtrack.assistant.service;

/**
 * The one seam between this app and a language model.
 *
 * <p>An interface not for ceremony but because everything above it must be testable without
 * spending money: the service's clamping, persistence and cost math all run against a fake in
 * tests, and only {@link AnthropicReviewModel} ever opens a socket.
 */
public interface ReviewModel {

  /** False when no API key is configured — the assistant is off, not broken. */
  boolean configured();

  /**
   * Draft a weekly review from the assembled context.
   *
   * @param contextJson the {@code AssistantContext} for the week, serialized — the model sees
   *     exactly what {@code GET /api/assistant/context} returns, nothing more
   */
  DraftedReview draft(String contextJson);

  /**
   * @param inputTokens what the call cost, kept with the draft — a feature that spends money per
   *     click should say what it spent
   */
  record DraftedReview(ReviewDraft draft, String model, long inputTokens, long outputTokens) {}
}
