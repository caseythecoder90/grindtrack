package dev.grindtrack.recovery.service;

import dev.grindtrack.recovery.domain.RecoveryParagraph;
import java.util.ArrayList;
import java.util.List;

/**
 * Today's part of the book: the pages owed, from the cursor, in whole paragraphs. A paragraph that
 * starts on the last page owed is read to its end, wherever that is, rather than cut.
 */
public final class ReadingPlanner {

  /** For a book that came in as plain text and has no pages of its own. */
  public static final int WORDS_PER_PAGE = 300;

  private ReadingPlanner() {}

  /**
   * @param fromCursor paragraphs from the cursor onwards, in order
   * @param pages how many pages are owed; at least one paragraph comes back whatever the count
   */
  public static List<RecoveryParagraph> part(List<RecoveryParagraph> fromCursor, int pages) {
    List<RecoveryParagraph> out = new ArrayList<>();
    if (fromCursor.isEmpty()) {
      return out;
    }
    int end = fromCursor.get(0).getPageSeq() + Math.max(1, pages);
    for (RecoveryParagraph p : fromCursor) {
      if (p.getPageSeq() >= end && !out.isEmpty()) {
        break;
      }
      out.add(p);
    }
    return out;
  }
}
