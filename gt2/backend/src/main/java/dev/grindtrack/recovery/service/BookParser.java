package dev.grindtrack.recovery.service;

import dev.grindtrack.web.BadRequestException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * One text file of a whole book into chapters and paragraphs, with no markup asked of the person
 * who made the file.
 *
 * <p>A chapter heading is a line standing on its own that is short, is not a sentence, and is
 * either one of the Big Book's chapter titles or set in capitals. "Chapter 3" on a line before the
 * title is folded into the title. Whatever comes before the first heading is the front matter, kept
 * as chapter zero when it is long enough to be text and dropped when it is a copyright line.
 */
public final class BookParser {

  static final int MAX_HEADING_CHARS = 60;

  /** Fewer paragraphs than this and the chapter is probably a misread heading. */
  private static final int THIN_CHAPTER = 3;

  private static final int FRONT_MATTER_MIN_WORDS = 40;

  private static final Set<String> KNOWN =
      Set.of(
          "preface",
          "foreword",
          "the doctors opinion",
          "doctors opinion",
          "bills story",
          "there is a solution",
          "more about alcoholism",
          "we agnostics",
          "how it works",
          "into action",
          "working with others",
          "to wives",
          "the family afterward",
          "to employers",
          "a vision for you",
          "appendices",
          "appendix",
          "the spiritual experience",
          "spiritual experience");

  private static final Pattern CHAPTER_NUMBER =
      Pattern.compile("^(chapter|part)\\s+(\\d+|[ivxlc]+)\\.?$", Pattern.CASE_INSENSITIVE);
  private static final Pattern CHAPTER_PREFIX =
      Pattern.compile("^(chapter|part)\\s+(\\d+|[ivxlc]+)[\\s.:\\-–—]+", Pattern.CASE_INSENSITIVE);
  private static final Pattern NOT_A_LETTER = Pattern.compile("[^a-z0-9 ]");

  public record Paragraph(int chapterNo, String chapterTitle, int seq, String body, int words) {}

  public record Chapter(int no, String title, int paragraphs, int words) {}

  public record Parsed(List<Paragraph> paragraphs, List<Chapter> chapters, List<String> warnings) {

    public int words() {
      return paragraphs.stream().mapToInt(Paragraph::words).sum();
    }
  }

  private BookParser() {}

  public static Parsed parse(String text) {
    List<String> warnings = new ArrayList<>();
    List<Paragraph> paragraphs = new ArrayList<>();

    int chapterNo = 0;
    String chapterTitle = "Front matter";
    String pendingNumber = null;
    for (String block : Blocks.clean(text).split("\\n[ \\t]*\\n")) {
      List<String> lines = Blocks.lines(block);
      if (lines.isEmpty()) {
        continue;
      }
      // A heading with the first paragraph hard up against it, no blank line between.
      List<String> units;
      if (Blocks.unwrapped(lines)) {
        units = lines;
      } else if (lines.size() > 1 && isHeading(lines.get(0))) {
        units = List.of(lines.get(0), String.join(" ", lines.subList(1, lines.size())));
      } else {
        units = List.of(String.join(" ", lines));
      }
      for (String unit : units) {
        if (isHeading(unit)) {
          if (CHAPTER_NUMBER.matcher(unit).matches()) {
            // "Chapter 3" alone: the title is the next heading, or this if there is none.
            pendingNumber = unit;
            continue;
          }
          chapterNo++;
          chapterTitle = title(unit);
          pendingNumber = null;
          continue;
        }
        if (pendingNumber != null) {
          chapterNo++;
          chapterTitle = title(pendingNumber);
          pendingNumber = null;
        }
        paragraphs.add(
            new Paragraph(chapterNo, chapterTitle, paragraphs.size(), unit, Blocks.words(unit)));
      }
    }

    // Front matter that is only a copyright line or two is not a chapter — when there are
    // chapters. A file with no headings at all is one chapter, however short.
    if (chapterNo > 0) {
      int frontWords =
          paragraphs.stream().filter(p -> p.chapterNo() == 0).mapToInt(Paragraph::words).sum();
      if (frontWords < FRONT_MATTER_MIN_WORDS) {
        paragraphs.removeIf(p -> p.chapterNo() == 0);
      }
    }
    if (paragraphs.isEmpty()) {
      throw new BadRequestException("That file has no text in it.");
    }
    List<Paragraph> renumbered = new ArrayList<>(paragraphs.size());
    for (Paragraph p : paragraphs) {
      renumbered.add(
          new Paragraph(p.chapterNo(), p.chapterTitle(), renumbered.size(), p.body(), p.words()));
    }

    List<Chapter> chapters = chapters(renumbered);
    if (chapters.size() == 1 && chapters.get(0).no() == 0) {
      warnings.add(
          "No chapter headings were recognised, so the whole file is one chapter. A heading is a"
              + " line on its own, in CAPITALS or one of the book's chapter titles.");
    }
    for (Chapter c : chapters) {
      if (c.paragraphs() < THIN_CHAPTER && c.no() > 0) {
        warnings.add(
            "\""
                + c.title()
                + "\" has only "
                + c.paragraphs()
                + " paragraph"
                + (c.paragraphs() == 1 ? "" : "s")
                + " — a heading may have been read where there was none.");
      }
    }
    int avg = renumbered.stream().mapToInt(Paragraph::words).sum() / renumbered.size();
    if (avg > 400) {
      warnings.add(
          "Paragraphs average "
              + avg
              + " words. The file may have lost its blank lines; a line"
              + " longer than 150 characters is read as a paragraph of its own.");
    }
    return new Parsed(List.copyOf(renumbered), chapters, List.copyOf(warnings));
  }

  static boolean isHeading(String line) {
    if (line.length() > MAX_HEADING_CHARS || line.endsWith(".") || line.endsWith(",")) {
      return false;
    }
    if (CHAPTER_NUMBER.matcher(line).matches()) {
      return true;
    }
    String key = normalize(line);
    if (KNOWN.contains(key)) {
      return true;
    }
    int letters = 0;
    for (char ch : line.toCharArray()) {
      if (Character.isLetter(ch)) {
        if (Character.isLowerCase(ch)) {
          return false;
        }
        letters++;
      }
    }
    return letters >= 3;
  }

  /** Lower-case letters and digits only, and any leading "chapter 3" gone. */
  static String normalize(String line) {
    String key = CHAPTER_PREFIX.matcher(line).replaceFirst("");
    key = key.toLowerCase(Locale.ROOT).replace('’', '\'').replace("'", "");
    key = NOT_A_LETTER.matcher(key).replaceAll(" ").trim().replaceAll(" {2,}", " ");
    return key;
  }

  private static final Set<String> SMALL_WORDS =
      Set.of("a", "an", "the", "and", "or", "of", "to", "in", "on", "for", "with", "about", "at");

  /** "BILL'S STORY" as "Bill's Story"; a heading already in mixed case is left alone. */
  static String title(String line) {
    String bare = CHAPTER_PREFIX.matcher(line).replaceFirst("").trim();
    if (bare.isEmpty()) {
      bare = line.trim();
    }
    boolean allCaps = bare.chars().filter(Character::isLetter).noneMatch(Character::isLowerCase);
    if (!allCaps) {
      return bare;
    }
    StringBuilder out = new StringBuilder(bare.length());
    for (String word : bare.split(" ")) {
      if (out.length() > 0) {
        out.append(' ');
      }
      String lower = word.toLowerCase(Locale.ROOT);
      if (out.length() > 0 && SMALL_WORDS.contains(lower)) {
        out.append(lower);
        continue;
      }
      boolean startOfWord = true;
      for (char ch : lower.toCharArray()) {
        out.append(startOfWord && Character.isLetter(ch) ? Character.toUpperCase(ch) : ch);
        startOfWord = !Character.isLetter(ch) && ch != '\'' && ch != '’';
      }
    }
    return out.toString();
  }

  private static List<Chapter> chapters(List<Paragraph> paragraphs) {
    Map<Integer, int[]> counts = new LinkedHashMap<>();
    Map<Integer, String> titles = new LinkedHashMap<>();
    for (Paragraph p : paragraphs) {
      counts.computeIfAbsent(p.chapterNo(), k -> new int[2]);
      counts.get(p.chapterNo())[0]++;
      counts.get(p.chapterNo())[1] += p.words();
      titles.putIfAbsent(p.chapterNo(), p.chapterTitle());
    }
    List<Chapter> out = new ArrayList<>();
    for (Map.Entry<Integer, int[]> e : counts.entrySet()) {
      out.add(new Chapter(e.getKey(), titles.get(e.getKey()), e.getValue()[0], e.getValue()[1]));
    }
    return List.copyOf(out);
  }
}
