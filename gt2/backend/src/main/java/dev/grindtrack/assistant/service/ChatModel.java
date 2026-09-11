package dev.grindtrack.assistant.service;

import java.util.List;

/** The chat seam, for the same reason {@link ReviewModel} is one: tests must not spend money. */
public interface ChatModel {

  boolean configured();

  /**
   * One turn: prior text turns, the fresh context, the new message. The implementation may call
   * read tools any number of times before answering; the caller only ever sees the final text and
   * the total bill.
   */
  Reply reply(String contextJson, List<Turn> history, String userMessage);

  record Turn(String role, String content) {}

  /**
   * @param cacheWriteTokens input tokens written to the prompt cache, billed at 1.25x
   * @param cacheReadTokens input tokens served from the prompt cache, billed at 0.1x
   */
  record Reply(
      String text,
      long inputTokens,
      long outputTokens,
      long cacheWriteTokens,
      long cacheReadTokens) {}
}
