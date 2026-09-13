package dev.grindtrack.recovery.service;

import dev.grindtrack.recovery.domain.RecoveryParagraph;
import java.util.ArrayList;
import java.util.List;

/**
 * Today's part of the book: whole paragraphs from the cursor until the words add up to the minutes.
 * The part ends on the paragraph that crosses the line, so it runs a little over rather than
 * stopping mid-thought a little under.
 */
public final class ReadingPlanner {

  /** Reading to understand, not to finish. */
  public static final int WORDS_PER_MINUTE = 180;

  private ReadingPlanner() {}

  /**
   * @param fromCursor paragraphs from the cursor onwards, in order; may be fewer than a day's worth
   *     at the end of the book
   */
  public static List<RecoveryParagraph> part(List<RecoveryParagraph> fromCursor, int minutes) {
    int target = Math.max(1, minutes) * WORDS_PER_MINUTE;
    List<RecoveryParagraph> out = new ArrayList<>();
    int words = 0;
    for (RecoveryParagraph p : fromCursor) {
      if (words >= target) {
        break;
      }
      out.add(p);
      words += p.getWords();
    }
    return out;
  }
}
