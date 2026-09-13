package dev.grindtrack.recovery.service;

import dev.grindtrack.web.BadRequestException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The publisher's per-chapter PDFs of a book into chapters, pages and paragraphs.
 *
 * <p>Works on lines, not on PDFs: {@link PdfText} turns each file into pages of lines with the
 * paragraph starts marked, and this class knows what a page of a typeset book looks like — a
 * running head with the page number, a print-shop footer, a hyphen at the end of a line, a chapter
 * title in capitals, a drop cap. Files are put in the book's order by their names, because a
 * browser hands them over in whatever order they were picked.
 */
public final class PdfBookParser {

  /** A file as uploaded: its name and its pages of lines. */
  public record Source(String name, List<List<String>> pages) {}

  /**
   * @param pageLabel the page as printed: "xvi", "58"; null before the first numbered page
   * @param pageSeq the page's running number across the whole book, from zero
   */
  public record Paragraph(
      int chapterNo,
      String chapterTitle,
      int seq,
      String body,
      int words,
      String pageLabel,
      int pageSeq) {}

  public record Chapter(
      int no, String title, int paragraphs, int words, String firstPage, String lastPage) {}

  public record Parsed(List<Paragraph> paragraphs, List<Chapter> chapters, List<String> warnings) {

    public int words() {
      return paragraphs.stream().mapToInt(Paragraph::words).sum();
    }

    public int pages() {
      return paragraphs.isEmpty() ? 0 : paragraphs.get(paragraphs.size() - 1).pageSeq() + 1;
    }
  }

  private static final Pattern FOOTER = Pattern.compile("^\\s*¶?\\s*Alco_\\d.*");
  private static final String NUMBER = "(\\d{1,3}|[ivxlc]{1,7})";
  private static final Pattern LABEL_ALONE = Pattern.compile("^" + NUMBER + "$");
  private static final Pattern HEAD_LEFT = Pattern.compile("^" + NUMBER + "\\s+[^a-z]{3,}$");
  private static final Pattern HEAD_RIGHT = Pattern.compile("^[^a-z]{3,}\\s+" + NUMBER + "$");
  private static final Pattern CHAPTER_NUMBER =
      Pattern.compile("^(chapter|part)\\s+(\\d+|[ivxlc]+)\\.?$", Pattern.CASE_INSENSITIVE);
  private static final Pattern ROMAN_ALONE = Pattern.compile("^[IVXLC]{1,7}$");
  private static final Pattern DROP_CAP = Pattern.compile("^([A-Z]) ([a-z])");

  /** "A.A." at the end of a title is an abbreviation, not the end of a sentence. */
  private static final Pattern ABBREVIATION_END = Pattern.compile("\\b[A-Z]\\.$");

  private static final Pattern SENTENCE_END = Pattern.compile("[.!?”’)\"']$");
  private static final int MAX_TITLE_CHARS = 60;

  /** A chapter with fewer words than this is a note between chapters, not a chapter. */
  static final int TINY_CHAPTER_WORDS = 40;

  /**
   * A chapter under this that a note folds into is a section of notes, and takes the note's name.
   */
  static final int SECTION_NOTE_WORDS = 200;

  /** Files the app must not read from: not text, or a copyright line, or the table of contents. */
  private static final List<String> SKIPPED = List.of("titlepage", "copyright", "contents");

  private static final Map<String, Integer> RANK = new LinkedHashMap<>();

  static {
    RANK.put("preface", 3);
    RANK.put("forewordfirst", 4);
    RANK.put("forewordsecond", 5);
    RANK.put("forewordthird", 6);
    RANK.put("forewordfourth", 7);
    RANK.put("doctorsopinion", 8);
    RANK.put("personalstories_parti.", 31);
    RANK.put("personalstories_partii.", 32);
    RANK.put("personalstories_partiii.", 33);
    RANK.put("personalstories", 30);
    RANK.put("appendicei.", 41);
    RANK.put("appendiceii.", 42);
    RANK.put("appendiceiii.", 43);
    RANK.put("appendiceiv.", 44);
    RANK.put("appendicev.", 45);
    RANK.put("appendicevi.", 46);
    RANK.put("appendicevii", 47);
    RANK.put("appendix", 49);
  }

  private PdfBookParser() {}

  /** Where a file goes in the book, from its name; unknown names go last, alphabetically. */
  static int rank(String name) {
    String n = name.toLowerCase(Locale.ROOT);
    Matcher chapter = Pattern.compile("chapt(?:er)?[ _-]?(\\d{1,2})").matcher(n);
    if (chapter.find()) {
      return 10 + Integer.parseInt(chapter.group(1));
    }
    for (Map.Entry<String, Integer> e : RANK.entrySet()) {
      if (n.contains(e.getKey())) {
        return e.getValue();
      }
    }
    return 100;
  }

  static boolean skipped(String name) {
    String n = name.toLowerCase(Locale.ROOT);
    return SKIPPED.stream().anyMatch(n::contains);
  }

  public static Parsed parse(List<Source> files) {
    List<Source> ordered = new ArrayList<>(files);
    ordered.sort(Comparator.comparingInt((Source s) -> rank(s.name())).thenComparing(Source::name));
    List<String> warnings = new ArrayList<>();
    Book book = new Book();
    int index = 0;
    for (Source file : ordered) {
      if (skipped(file.name())) {
        warnings.add("Skipped " + file.name() + ": not for reading.");
        continue;
      }
      int before = book.paragraphs.size();
      book.startFile(index++);
      for (List<String> page : file.pages()) {
        book.page(page);
      }
      book.flush();
      if (book.paragraphs.size() == before) {
        warnings.add("Nothing readable was found in " + file.name() + ".");
      }
    }
    if (book.paragraphs.isEmpty()) {
      throw new BadRequestException("No text was found in those files.");
    }
    List<Paragraph> folded = fold(book.paragraphs, book.chapterFiles);
    List<Chapter> chapters = chapters(folded);
    for (Chapter c : chapters) {
      if (c.paragraphs() < 2) {
        warnings.add("\"" + c.title() + "\" has only " + c.paragraphs() + " paragraph.");
      }
    }
    return new Parsed(List.copyOf(folded), chapters, List.copyOf(warnings));
  }

  /** The state of a read through the files: where the chapter, the page and the paragraph are. */
  private static final class Book {
    final List<Paragraph> paragraphs = new ArrayList<>();

    /** Which file each chapter number opened in, for the folding afterwards. */
    final Map<Integer, Integer> chapterFiles = new LinkedHashMap<>();

    int chapterNo;
    String chapterTitle = "Front matter";
    String pendingNumber;
    boolean atFileStart;
    boolean atPageStart;
    boolean lastWasHeading;
    boolean firstInChapter;
    int fileIndex;
    String pageLabel;
    int pageSeq = -1;
    StringBuilder body;
    String bodyLabel;
    int bodySeq;

    void startFile(int index) {
      atFileStart = true;
      pendingNumber = null;
      fileIndex = index;
    }

    void page(List<String> rawLines) {
      List<String> lines = new ArrayList<>();
      for (String raw : rawLines) {
        if (FOOTER.matcher(raw).matches()) {
          continue;
        }
        lines.add(raw);
      }
      // The page number: on a running head at the top, or alone at the bottom, or alone at the top.
      String label = null;
      int labelAt = -1;
      for (int i = 0; i < lines.size() && i < 2 && label == null; i++) {
        label = labelOf(lines.get(i));
        labelAt = i;
      }
      for (int i = lines.size() - 1; i >= 0 && i >= lines.size() - 3 && label == null; i--) {
        label = labelOf(lines.get(i));
        labelAt = i;
      }
      if (label != null) {
        lines.remove(labelAt);
        pageLabel = label;
        pageSeq++;
      } else if (pageSeq < 0 || !lines.isEmpty()) {
        // A page with no printed number still turns the page.
        pageSeq++;
        pageLabel = pageLabel == null ? null : nextLabel(pageLabel);
      }
      atPageStart = true;
      for (String raw : lines) {
        boolean marked = raw.stripLeading().startsWith(String.valueOf(PdfText.PARAGRAPH));
        String text = clean(raw);
        if (text.isEmpty()) {
          continue;
        }
        if (heading(text, marked)) {
          atPageStart = false;
          continue;
        }
        atPageStart = false;
        if (pendingNumber != null) {
          open(pendingNumber);
        }
        lastWasHeading = false;
        if (marked && !continues(text)) {
          flush();
          body = new StringBuilder(text);
          bodyLabel = pageLabel;
          bodySeq = pageSeq;
          atFileStart = false;
        } else if (body == null) {
          body = new StringBuilder(text);
          bodyLabel = pageLabel;
          bodySeq = pageSeq;
          atFileStart = false;
        } else {
          join(text);
        }
      }
    }

    /**
     * A chapter title is capitals on a line of its own, or "Chapter 3", before the body. Inside a
     * file, capitals count as a title only at the top of a page, or right after a number or another
     * title line — a line of capitals in the middle of a page is emphasis, not a chapter.
     */
    private boolean heading(String text, boolean marked) {
      if (text.length() > MAX_TITLE_CHARS || text.endsWith(",")) {
        return false;
      }
      if (text.endsWith(".") && !ABBREVIATION_END.matcher(text).find()) {
        return false;
      }
      if (CHAPTER_NUMBER.matcher(text).matches()
          || (ROMAN_ALONE.matcher(text).matches() && (atFileStart || marked))) {
        // The number alone. Chapters are numbered by the app; the title usually follows, and a
        // number with no title still opens a chapter under that name.
        pendingNumber = CHAPTER_NUMBER.matcher(text).matches() ? BookParser.title(text) : text;
        lastWasHeading = true;
        return true;
      }
      // The second line of a two-line title is centred like the first but not always marked.
      if (!marked && !atFileStart && !lastWasHeading) {
        return false;
      }
      int letters = 0;
      for (char ch : text.toCharArray()) {
        if (Character.isLetter(ch)) {
          if (Character.isLowerCase(ch)) {
            return false;
          }
          letters++;
        }
      }
      if (letters < 3) {
        return false;
      }
      String title = BookParser.title(text.replaceAll("\\*+$", "").trim());
      // "Part I" keeps its number in the name; "Chapter 3" and a bare numeral do not.
      if (pendingNumber != null && pendingNumber.toLowerCase(Locale.ROOT).startsWith("part ")) {
        title = pendingNumber + " · " + title;
      }
      open(title);
      return true;
    }

    /**
     * A new chapter — or, when the current one has no text yet, the same chapter under a longer
     * name: the second line of a title that ran to two, or "Part I" then what the part is called.
     */
    private void open(String title) {
      flush();
      if (chapterNo > 0 && chapterEmpty() && lastWasHeading && pendingNumber == null) {
        chapterTitle = chapterTitle + " " + title;
      } else if (chapterNo > 0 && chapterEmpty()) {
        chapterTitle = chapterTitle + " · " + title;
      } else {
        chapterNo++;
        chapterTitle = title;
        chapterFiles.put(chapterNo, fileIndex);
      }
      pendingNumber = null;
      firstInChapter = true;
      lastWasHeading = true;
    }

    private boolean chapterEmpty() {
      return paragraphs.isEmpty() || paragraphs.get(paragraphs.size() - 1).chapterNo() != chapterNo;
    }

    /** A marked line that is really the rest of the paragraph: after a hyphen, or mid-sentence. */
    private boolean continues(String text) {
      if (body == null) {
        return false;
      }
      String prev = body.toString();
      char first = text.charAt(0);
      if (prev.endsWith("-") || prev.endsWith("­")) {
        return Character.isLowerCase(first);
      }
      return Character.isLowerCase(first) && !SENTENCE_END.matcher(prev).find();
    }

    private void join(String text) {
      String prev = body.toString();
      if ((prev.endsWith("-") || prev.endsWith("­")) && Character.isLowerCase(text.charAt(0))) {
        body.setLength(body.length() - 1);
        body.append(text);
      } else {
        body.append(' ').append(text);
      }
    }

    void flush() {
      if (body == null) {
        return;
      }
      String text = body.toString().replace("­", "").replaceAll(" {2,}", " ").trim();
      if (firstInChapter) {
        text = dropCap(text);
        firstInChapter = false;
      }
      if (!text.isEmpty()) {
        paragraphs.add(
            new Paragraph(
                chapterNo,
                chapterTitle,
                paragraphs.size(),
                text,
                Blocks.words(text),
                bodyLabel,
                bodySeq));
        chapterFiles.putIfAbsent(chapterNo, fileIndex);
      }
      body = null;
    }
  }

  /**
   * A chapter of a few words — the note on the page before the stories, the line that opens the
   * appendices — is not a chapter. It folds into its neighbour, its title kept as a line of text:
   * into the next chapter when it opens a file, otherwise into the one before.
   */
  static List<Paragraph> fold(List<Paragraph> in, Map<Integer, Integer> chapterFiles) {
    Map<Integer, List<Paragraph>> byChapter = new LinkedHashMap<>();
    for (Paragraph p : in) {
      byChapter.computeIfAbsent(p.chapterNo(), k -> new ArrayList<>()).add(p);
    }
    List<Integer> numbers = new ArrayList<>(byChapter.keySet());
    for (int i = 0; i < numbers.size(); i++) {
      int no = numbers.get(i);
      List<Paragraph> ps = byChapter.get(no);
      if (ps == null || no == 0) {
        continue;
      }
      int words = ps.stream().mapToInt(Paragraph::words).sum();
      if (words >= TINY_CHAPTER_WORDS) {
        continue;
      }
      Integer previous = null;
      for (int j = i - 1; j >= 0 && previous == null; j--) {
        if (byChapter.containsKey(numbers.get(j))) {
          previous = numbers.get(j);
        }
      }
      Integer next = i + 1 < numbers.size() ? numbers.get(i + 1) : null;
      boolean opensFile =
          previous == null
              || !chapterFiles.getOrDefault(no, -1).equals(chapterFiles.getOrDefault(previous, -2));
      Integer into = opensFile && next != null ? next : previous;
      if (into == null) {
        continue;
      }
      List<Paragraph> target = byChapter.get(into);
      // A note that opens a section of small pieces names the section; one that opens a real
      // chapter is a line in it.
      int targetWords = target.stream().mapToInt(Paragraph::words).sum();
      boolean namesTarget = into > no && targetWords < SECTION_NOTE_WORDS;
      String title = namesTarget ? ps.get(0).chapterTitle() : target.get(0).chapterTitle();
      if (namesTarget) {
        // The section takes the note's name; what the piece was called becomes its first line.
        Paragraph head = target.get(0);
        String was = head.chapterTitle();
        List<Paragraph> renamed = new ArrayList<>();
        renamed.add(
            new Paragraph(
                into, title, 0, was, Blocks.words(was), head.pageLabel(), head.pageSeq()));
        for (Paragraph p : target) {
          renamed.add(
              new Paragraph(into, title, 0, p.body(), p.words(), p.pageLabel(), p.pageSeq()));
        }
        target.clear();
        target.addAll(renamed);
      }
      List<Paragraph> moved = new ArrayList<>();
      Paragraph first = ps.get(0);
      if (!namesTarget) {
        moved.add(
            new Paragraph(
                into,
                title,
                0,
                first.chapterTitle(),
                Blocks.words(first.chapterTitle()),
                first.pageLabel(),
                first.pageSeq()));
      }
      for (Paragraph p : ps) {
        moved.add(new Paragraph(into, title, 0, p.body(), p.words(), p.pageLabel(), p.pageSeq()));
      }
      if (into > no) {
        target.addAll(0, moved);
      } else {
        target.addAll(moved);
      }
      byChapter.remove(no);
    }
    // Renumber chapters and paragraphs in reading order.
    List<Paragraph> out = new ArrayList<>(in.size());
    int chapter = 0;
    for (Map.Entry<Integer, List<Paragraph>> e : byChapter.entrySet()) {
      int number = e.getKey() == 0 ? 0 : ++chapter;
      for (Paragraph p : e.getValue()) {
        out.add(
            new Paragraph(
                number,
                p.chapterTitle(),
                out.size(),
                p.body(),
                p.words(),
                p.pageLabel(),
                p.pageSeq()));
      }
    }
    return out;
  }

  /**
   * "W e, of" back to "We, of": a drop cap comes out of the PDF as a letter on its own. "A" and "I"
   * are words, so they are joined only to a fragment too short to be one.
   */
  static String dropCap(String text) {
    Matcher m = DROP_CAP.matcher(text);
    if (!m.find()) {
      return text;
    }
    String letter = m.group(1);
    int end = text.indexOf(' ', 2);
    String fragment = end < 0 ? text.substring(2) : text.substring(2, end);
    boolean word = letter.equals("A") || letter.equals("I");
    if (word && fragment.replaceAll("[^a-z]", "").length() > 2) {
      return text;
    }
    return letter + text.substring(2);
  }

  /** The page number on a running head or standing alone; null when the line is text. */
  static String labelOf(String raw) {
    String text = clean(raw);
    Matcher m = LABEL_ALONE.matcher(text);
    if (m.matches()) {
      return m.group(1);
    }
    m = HEAD_LEFT.matcher(text);
    if (m.matches()) {
      return m.group(1);
    }
    m = HEAD_RIGHT.matcher(text);
    if (m.matches()) {
      return m.group(1);
    }
    return null;
  }

  static String nextLabel(String label) {
    try {
      return String.valueOf(Integer.parseInt(label) + 1);
    } catch (NumberFormatException e) {
      return label;
    }
  }

  /** The paragraph mark and the typesetter's oddities gone: ligatures, doubled quotes, spacing. */
  static String clean(String raw) {
    String text = raw.replace(String.valueOf(PdfText.PARAGRAPH), "");
    text = Normalizer.normalize(text, Normalizer.Form.NFKC);
    text = text.replace("’’", "”").replace("‘‘", "“").replace('\t', ' ');
    return text.replaceAll(" {2,}", " ").trim();
  }

  private static List<Chapter> chapters(List<Paragraph> paragraphs) {
    Map<Integer, List<Paragraph>> byChapter = new LinkedHashMap<>();
    for (Paragraph p : paragraphs) {
      byChapter.computeIfAbsent(p.chapterNo(), k -> new ArrayList<>()).add(p);
    }
    List<Chapter> out = new ArrayList<>();
    for (List<Paragraph> ps : byChapter.values()) {
      out.add(
          new Chapter(
              ps.get(0).chapterNo(),
              ps.get(0).chapterTitle(),
              ps.size(),
              ps.stream().mapToInt(Paragraph::words).sum(),
              ps.get(0).pageLabel(),
              ps.get(ps.size() - 1).pageLabel()));
    }
    return List.copyOf(out);
  }
}
