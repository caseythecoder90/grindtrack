package dev.grindtrack.recovery.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The sixty-six books, in canonical order, by USFM code. */
public final class BibleBooks {

  private static final Map<String, String> NAMES = new LinkedHashMap<>();

  static {
    String[][] books = {
      {"GEN", "Genesis"},
      {"EXO", "Exodus"},
      {"LEV", "Leviticus"},
      {"NUM", "Numbers"},
      {"DEU", "Deuteronomy"},
      {"JOS", "Joshua"},
      {"JDG", "Judges"},
      {"RUT", "Ruth"},
      {"1SA", "1 Samuel"},
      {"2SA", "2 Samuel"},
      {"1KI", "1 Kings"},
      {"2KI", "2 Kings"},
      {"1CH", "1 Chronicles"},
      {"2CH", "2 Chronicles"},
      {"EZR", "Ezra"},
      {"NEH", "Nehemiah"},
      {"EST", "Esther"},
      {"JOB", "Job"},
      {"PSA", "Psalms"},
      {"PRO", "Proverbs"},
      {"ECC", "Ecclesiastes"},
      {"SNG", "Song of Solomon"},
      {"ISA", "Isaiah"},
      {"JER", "Jeremiah"},
      {"LAM", "Lamentations"},
      {"EZK", "Ezekiel"},
      {"DAN", "Daniel"},
      {"HOS", "Hosea"},
      {"JOL", "Joel"},
      {"AMO", "Amos"},
      {"OBA", "Obadiah"},
      {"JON", "Jonah"},
      {"MIC", "Micah"},
      {"NAM", "Nahum"},
      {"HAB", "Habakkuk"},
      {"ZEP", "Zephaniah"},
      {"HAG", "Haggai"},
      {"ZEC", "Zechariah"},
      {"MAL", "Malachi"},
      {"MAT", "Matthew"},
      {"MRK", "Mark"},
      {"LUK", "Luke"},
      {"JHN", "John"},
      {"ACT", "Acts"},
      {"ROM", "Romans"},
      {"1CO", "1 Corinthians"},
      {"2CO", "2 Corinthians"},
      {"GAL", "Galatians"},
      {"EPH", "Ephesians"},
      {"PHP", "Philippians"},
      {"COL", "Colossians"},
      {"1TH", "1 Thessalonians"},
      {"2TH", "2 Thessalonians"},
      {"1TI", "1 Timothy"},
      {"2TI", "2 Timothy"},
      {"TIT", "Titus"},
      {"PHM", "Philemon"},
      {"HEB", "Hebrews"},
      {"JAS", "James"},
      {"1PE", "1 Peter"},
      {"2PE", "2 Peter"},
      {"1JN", "1 John"},
      {"2JN", "2 John"},
      {"3JN", "3 John"},
      {"JUD", "Jude"},
      {"REV", "Revelation"},
    };
    for (String[] b : books) {
      NAMES.put(b[0], b[1]);
    }
  }

  private BibleBooks() {}

  public static List<String> codes() {
    return List.copyOf(NAMES.keySet());
  }

  /** Canonical position, 1 to 66; zero for a code that is not a book. */
  public static int ord(String code) {
    int i = 1;
    for (String c : NAMES.keySet()) {
      if (c.equals(code)) {
        return i;
      }
      i++;
    }
    return 0;
  }

  public static boolean isBook(String code) {
    return NAMES.containsKey(code);
  }

  public static String name(String code) {
    return NAMES.getOrDefault(code, code);
  }

  /** "Psalm 23", "John 3:16–21": the way a reference is said. */
  public static String reference(String code, int chapter, int from, int to, boolean wholeChapter) {
    String name = "PSA".equals(code) ? "Psalm" : name(code);
    if (wholeChapter) {
      return name + " " + chapter;
    }
    return name + " " + chapter + ":" + (from == to ? from : from + "–" + to);
  }
}
