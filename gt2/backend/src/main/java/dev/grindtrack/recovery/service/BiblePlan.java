package dev.grindtrack.recovery.service;

import dev.grindtrack.recovery.domain.BibleVerse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The daily passages, built once from the verse table.
 *
 * <p>Within a chapter, verses are grouped from one paragraph start to the next, groups are joined
 * until there are at least five verses, and a group is cut at sixteen whatever the paragraphs say.
 * That gives passages of six to eleven verses, about a minute read aloud. Books come in the
 * configured order; any not listed follow in canonical order, so the plan always covers the whole
 * book.
 */
public final class BiblePlan {

  static final int MIN_VERSES = 5;
  static final int MAX_VERSES = 16;

  public record Passage(String book, int chapter, int from, int to, boolean wholeChapter) {

    public String reference() {
      return BibleBooks.reference(book, chapter, from, to, wholeChapter);
    }
  }

  private BiblePlan() {}

  /**
   * @param verses every verse, in canonical order
   * @param order USFM codes to read first, in this order
   */
  public static List<Passage> build(List<BibleVerse> verses, List<String> order) {
    Map<String, List<BibleVerse>> byBook = new LinkedHashMap<>();
    for (BibleVerse v : verses) {
      byBook.computeIfAbsent(v.getBook(), k -> new ArrayList<>()).add(v);
    }
    List<String> books = new ArrayList<>();
    for (String code : order) {
      if (byBook.containsKey(code) && !books.contains(code)) {
        books.add(code);
      }
    }
    for (String code : byBook.keySet()) {
      if (!books.contains(code)) {
        books.add(code);
      }
    }

    List<Passage> out = new ArrayList<>();
    for (String code : books) {
      List<BibleVerse> chapter = new ArrayList<>();
      for (BibleVerse v : byBook.get(code)) {
        if (!chapter.isEmpty() && chapter.get(0).getChapter() != v.getChapter()) {
          out.addAll(cut(chapter));
          chapter = new ArrayList<>();
        }
        chapter.add(v);
      }
      out.addAll(cut(chapter));
    }
    return List.copyOf(out);
  }

  private static List<Passage> cut(List<BibleVerse> chapter) {
    List<Passage> out = new ArrayList<>();
    if (chapter.isEmpty()) {
      return out;
    }
    int last = chapter.get(chapter.size() - 1).getVerse();
    List<BibleVerse> group = new ArrayList<>();
    for (BibleVerse v : chapter) {
      boolean cutHere =
          !group.isEmpty()
              && ((v.isPara() && group.size() >= MIN_VERSES) || group.size() >= MAX_VERSES);
      if (cutHere) {
        out.add(passage(group, last));
        group = new ArrayList<>();
      }
      group.add(v);
    }
    out.add(passage(group, last));
    return out;
  }

  private static Passage passage(List<BibleVerse> group, int lastVerseOfChapter) {
    BibleVerse first = group.get(0);
    int to = group.get(group.size() - 1).getVerse();
    boolean whole = first.getVerse() == 1 && to == lastVerseOfChapter;
    return new Passage(first.getBook(), first.getChapter(), first.getVerse(), to, whole);
  }
}
