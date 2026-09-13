package dev.grindtrack.recovery.service;

import dev.grindtrack.web.BadRequestException;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One text file of a book read by the calendar into its dated entries.
 *
 * <p>A line that is a month and a day — {@code JANUARY 1}, {@code January 1}, {@code Jan. 1},
 * {@code 1 January} — starts an entry. The next line is the title when it is short and not a
 * sentence; everything up to the next date line is the body. A date that appears twice keeps the
 * later copy and says so.
 */
public final class DailyParser {

  private static final String MONTHS =
      "jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?"
          + "|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?";
  private static final Pattern DATE_LINE =
      Pattern.compile(
          "^\\s*(?:("
              + MONTHS
              + ")\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?|(\\d{1,2})(?:st|nd|rd|th)?\\s+("
              + MONTHS
              + ")\\.?)\\s*$",
          Pattern.CASE_INSENSITIVE);
  private static final int MAX_TITLE_CHARS = 80;

  public record Entry(int month, int day, String title, String body) {}

  public record Parsed(List<Entry> entries, List<String> missing, List<String> warnings) {}

  private DailyParser() {}

  public static Parsed parse(String text) {
    Map<Integer, Entry> byDate = new LinkedHashMap<>();
    List<String> warnings = new ArrayList<>();
    int duplicates = 0;

    int month = 0;
    int day = 0;
    List<String> current = new ArrayList<>();
    for (String raw : Blocks.clean(text).split("\n")) {
      Matcher m = DATE_LINE.matcher(raw);
      if (m.matches()) {
        if (month > 0) {
          if (byDate.put(month * 100 + day, entry(month, day, current)) != null) {
            duplicates++;
          }
        }
        String monthText = m.group(1) != null ? m.group(1) : m.group(4);
        String dayText = m.group(2) != null ? m.group(2) : m.group(3);
        month = month(monthText);
        day = Integer.parseInt(dayText);
        current = new ArrayList<>();
        if (day < 1 || day > Month.of(month).maxLength()) {
          warnings.add("\"" + raw.trim() + "\" is not a date; its text was skipped.");
          month = 0;
        }
        continue;
      }
      if (month > 0) {
        current.add(raw);
      }
    }
    if (month > 0) {
      if (byDate.put(month * 100 + day, entry(month, day, current)) != null) {
        duplicates++;
      }
    }

    if (byDate.isEmpty()) {
      throw new BadRequestException(
          "No dated entries were found. An entry starts with a line that is the date, like"
              + " JANUARY 1.");
    }
    if (duplicates > 0) {
      warnings.add(
          duplicates
              + (duplicates == 1 ? " date appears" : " dates appear")
              + " twice; the later copy was kept.");
    }
    List<String> missing = new ArrayList<>();
    for (Month mo : Month.values()) {
      for (int d = 1; d <= mo.maxLength(); d++) {
        if (!byDate.containsKey(mo.getValue() * 100 + d)) {
          missing.add(mo.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + d);
        }
      }
    }
    return new Parsed(List.copyOf(byDate.values()), List.copyOf(missing), List.copyOf(warnings));
  }

  private static Entry entry(int month, int day, List<String> lines) {
    // The title is the first line when it is short and not a sentence — whether or not the file
    // puts a blank line after it.
    List<String> text = Blocks.lines(String.join("\n", lines));
    String title = "";
    List<String> rest = lines;
    if (text.size() > 1) {
      String first = text.get(0);
      if (first.length() <= MAX_TITLE_CHARS && !first.endsWith(".")) {
        title = BookParser.title(first);
        int at = indexOfLine(lines, first);
        rest = lines.subList(at + 1, lines.size());
      }
    }
    return new Entry(
        month, day, title, String.join("\n\n", Blocks.paragraphs(String.join("\n", rest))));
  }

  private static int indexOfLine(List<String> lines, String trimmed) {
    for (int i = 0; i < lines.size(); i++) {
      if (lines.get(i).trim().replaceAll(" {2,}", " ").equals(trimmed)) {
        return i;
      }
    }
    return 0;
  }

  private static int month(String text) {
    String key = text.toLowerCase(Locale.ROOT).substring(0, 3);
    for (Month m : Month.values()) {
      if (m.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
          .toLowerCase(Locale.ROOT)
          .startsWith(key)) {
        return m.getValue();
      }
    }
    throw new IllegalArgumentException(text);
  }
}
