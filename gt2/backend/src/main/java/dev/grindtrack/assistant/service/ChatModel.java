package dev.grindtrack.assistant.service;

import java.util.List;

/** The chat seam, for the same reason {@link ReviewModel} is one: tests must not spend money. */
public interface ChatModel {

  boolean configured();

  /**
   * One turn: prior text turns, the fresh context, the new message. The implementation may call
   * read tools any number of times before answering; the return value is the final text and the
   * total bill, whether or not anybody was listening along the way.
   */
  Reply reply(String contextJson, List<Turn> history, String userMessage, Listener listener);

  /**
   * What a turn looks like while it is still happening.
   *
   * <p>The turn is the same either way — one code path, no streaming variant of the loop. This is a
   * window onto it, and the default implementation is the window boarded up: a caller that only
   * wants the answer passes {@link #NONE} and the loop never notices the difference.
   *
   * <p>Both methods are called from the thread running the turn, in order, and must not block: a
   * slow listener slows the model call it is watching.
   */
  interface Listener {

    Listener NONE = new Listener() {};

    /** The model has started reading something. Named so a waiting person can see which. */
    default void onToolUse(String toolName) {}

    /** A fragment of the answer. Fragments concatenate to exactly {@link Reply#text()}. */
    default void onText(String delta) {}

    /**
     * The model is still working, called once for everything that arrives from upstream — the
     * fragments above included, and also the events this app makes no use of.
     *
     * <p>It exists because "nothing has happened for a minute" and "the connection is dead" look
     * identical from the far end of a proxy, and a model that is thinking hard about a hard
     * question can be silent for longer than a proxy will wait.
     */
    default void onProgress() {}
  }

  record Turn(String role, String content) {}

  /**
   * @param cacheWriteTokens input tokens written to the prompt cache, billed at 1.25x
   * @param cacheReadTokens input tokens served from the prompt cache, billed at 0.1x
   * @param proposedWeekStart the Monday this turn drafted a week for, or null. A draft is not a
   *     booking: it is a row and a card, and the calendar is untouched until someone clicks
   * @param proposedLogDate the day this turn drafted a log for, or null — the same contract: a row
   *     and a card, and the day's log untouched until someone clicks
   */
  record Reply(
      String text,
      long inputTokens,
      long outputTokens,
      long cacheWriteTokens,
      long cacheReadTokens,
      String proposedWeekStart,
      String proposedLogDate) {}
}
