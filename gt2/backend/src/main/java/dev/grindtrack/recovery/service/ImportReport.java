package dev.grindtrack.recovery.service;

import java.util.List;

/**
 * What an import found, before or after writing. The same shape either way, with {@code dryRun}
 * saying which, so the screen can show the preview and the result in one place.
 *
 * @param pages for a book read in order, how many pages it has (the printed ones, or one per three
 *     hundred words for plain text)
 * @param chapters for a book read in order, with the printed page range; empty for a dated book
 * @param entries for a dated book; zero for a book read in order
 * @param missing dates with no entry, as "Feb 29"; the first few only, {@code missingCount} has the
 *     total
 * @param sample the first two hundred characters of the first chapter or entry, so a wrong split is
 *     visible before anything is saved
 * @param cursorReset true when the reading cursor could not be kept across a replacement
 */
public record ImportReport(
    boolean dryRun,
    String slot,
    String title,
    int paragraphs,
    int words,
    int pages,
    List<Chapter> chapters,
    int entries,
    int missingCount,
    List<String> missing,
    String sample,
    List<String> warnings,
    boolean cursorReset) {

  public record Chapter(
      int no, String title, int paragraphs, int words, String firstPage, String lastPage) {}
}
