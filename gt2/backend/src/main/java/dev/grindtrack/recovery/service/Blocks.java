package dev.grindtrack.recovery.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Plain text into paragraphs, for both parsers.
 *
 * <p>A text file of a book comes in two shapes. Most exports separate paragraphs with a blank line
 * and wrap the lines inside them; some put each paragraph on one long line with no blank lines at
 * all. So a block of lines is one paragraph when its lines are wrapped (short), and one paragraph
 * per line when they are not. Page numbers standing on a line of their own are dropped.
 */
final class Blocks {

  /** A line longer than this is a paragraph that was never wrapped. */
  private static final int UNWRAPPED = 150;

  private static final Pattern PAGE_NUMBER = Pattern.compile("^(\\d{1,4}|[ivxlc]{1,7})$");
  private static final Pattern BLANK_LINES = Pattern.compile("\\n[ \\t]*\\n");

  private Blocks() {}

  /** Line endings, tabs, form feeds and the byte-order mark, normalised away. */
  static String clean(String text) {
    return text.replace("﻿", "")
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace('\f', '\n')
        .replace('\t', ' ')
        .replace(' ', ' ');
  }

  /** The paragraphs of a run of text, in order, with page numbers dropped and lines joined. */
  static List<String> paragraphs(String text) {
    List<String> out = new ArrayList<>();
    for (String block : BLANK_LINES.split(clean(text))) {
      List<String> lines = lines(block);
      if (lines.isEmpty()) {
        continue;
      }
      if (unwrapped(lines)) {
        out.addAll(lines);
      } else {
        out.add(String.join(" ", lines));
      }
    }
    return out;
  }

  /** The non-blank, trimmed lines of a block, page numbers left out. */
  static List<String> lines(String block) {
    List<String> lines = new ArrayList<>();
    for (String raw : block.split("\n")) {
      String line = raw.trim().replaceAll(" {2,}", " ");
      if (line.isEmpty() || PAGE_NUMBER.matcher(line).matches()) {
        continue;
      }
      lines.add(line);
    }
    return lines;
  }

  /** True when the block's lines are paragraphs in their own right rather than wrapped text. */
  static boolean unwrapped(List<String> lines) {
    if (lines.size() < 2) {
      return false;
    }
    int total = 0;
    for (String line : lines) {
      total += line.length();
    }
    return total / lines.size() > UNWRAPPED;
  }

  static int words(String text) {
    String trimmed = text.trim();
    return trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
  }
}
