package dev.grindtrack.recovery.service;

/** The passage explanation's seam to a model, so tests spend nothing. */
public interface PassageModel {

  boolean configured();

  /**
   * What the passage means, in plain words.
   *
   * @param reference "John 3:16–21"
   * @param translation the edition's name, so the model knows whose wording it is reading
   * @param text the verses, numbered, one per line
   */
  Explained explain(String reference, String translation, String text);

  record Explained(String body, String model, long inputTokens, long outputTokens) {}
}
