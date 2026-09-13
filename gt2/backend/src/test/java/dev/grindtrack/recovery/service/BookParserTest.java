package dev.grindtrack.recovery.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.grindtrack.web.BadRequestException;
import org.junit.jupiter.api.Test;

/** A whole book in one text file, with nothing marked up, into chapters and paragraphs. */
class BookParserTest {

  private static final String BOOK =
      """
      Copyright 1939, 2001. All rights reserved.

      PREFACE

      This is the first paragraph of the preface. It wraps
      across two lines because the export wrapped it.

      This is the second paragraph of the preface.

      12

      Chapter 1

      THE FIRST CHAPTER

      A first paragraph in the first chapter, long enough to be read as text and not as a
      heading of any kind.

      A second paragraph. A third sentence.

      xiv

      HOW IT WORKS
      Hard up against its heading, with no blank line between.

      Another paragraph in that chapter.
      """;

  @Test
  void headingsBecomeChaptersAndTheRestBecomesParagraphs() {
    BookParser.Parsed parsed = BookParser.parse(BOOK);

    assertThat(parsed.chapters())
        .extracting(BookParser.Chapter::title)
        .containsExactly("Preface", "The First Chapter", "How It Works");
    assertThat(parsed.chapters().get(0).no()).isEqualTo(1);
    assertThat(parsed.paragraphs()).hasSize(6);
    assertThat(parsed.paragraphs().get(0).body())
        .isEqualTo(
            "This is the first paragraph of the preface. It wraps across two lines because the"
                + " export wrapped it.");
    assertThat(parsed.paragraphs().get(4).body())
        .isEqualTo("Hard up against its heading, with no blank line between.");
    assertThat(parsed.paragraphs().get(4).chapterTitle()).isEqualTo("How It Works");
    assertThat(parsed.paragraphs())
        .extracting(BookParser.Paragraph::seq)
        .containsExactly(0, 1, 2, 3, 4, 5);
  }

  @Test
  void aShortFrontMatterIsDroppedAndPageNumbersNeverBecomeText() {
    BookParser.Parsed parsed = BookParser.parse(BOOK);

    assertThat(parsed.paragraphs()).noneMatch(p -> p.body().contains("Copyright"));
    assertThat(parsed.paragraphs()).noneMatch(p -> p.body().equals("12") || p.body().equals("xiv"));
  }

  @Test
  void aChapterNumberOnItsOwnLineTakesTheTitleThatFollows() {
    BookParser.Parsed parsed =
        BookParser.parse(
            "Chapter 2\n\nBILL'S STORY\n\nWar fever ran high.\n\nAnd on it went.\n\nA third one.");

    assertThat(parsed.chapters()).hasSize(1);
    assertThat(parsed.chapters().get(0).title()).isEqualTo("Bill's Story");
    assertThat(parsed.warnings()).isEmpty();
  }

  @Test
  void unwrappedLinesAreParagraphsOfTheirOwn() {
    String longLine = "word ".repeat(60).trim();
    BookParser.Parsed parsed =
        BookParser.parse("THE ONLY CHAPTER\n\n" + longLine + "\n" + longLine + "\n" + longLine);

    assertThat(parsed.paragraphs()).hasSize(3);
    assertThat(parsed.paragraphs().get(0).words()).isEqualTo(60);
  }

  @Test
  void aFileWithNoHeadingsIsOneChapterAndSaysSo() {
    BookParser.Parsed parsed = BookParser.parse("Just some words.\n\nAnd some more words here.");

    assertThat(parsed.chapters()).hasSize(1);
    assertThat(parsed.chapters().get(0).no()).isZero();
    assertThat(parsed.warnings()).anyMatch(w -> w.startsWith("No chapter headings"));
  }

  @Test
  void aThinChapterIsFlagged() {
    BookParser.Parsed parsed =
        BookParser.parse(
            "ONE\n\nEnough text here to be a paragraph.\n\nTWO\n\nOne paragraph only.\n\nA second.\n\nA third.");

    assertThat(parsed.warnings()).anyMatch(w -> w.contains("\"One\" has only 1 paragraph"));
  }

  @Test
  void anEmptyFileIsRefused() {
    assertThatThrownBy(() -> BookParser.parse("\n\n  \n"))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("no text");
  }

  @Test
  void headingRules() {
    assertThat(BookParser.isHeading("WE AGNOSTICS")).isTrue();
    assertThat(BookParser.isHeading("Into Action")).isTrue();
    assertThat(BookParser.isHeading("Chapter 7")).isTrue();
    assertThat(BookParser.isHeading("We admitted we were powerless.")).isFalse();
    assertThat(BookParser.isHeading("I")).isFalse();
    assertThat(BookParser.isHeading("A line of ordinary prose that is short")).isFalse();
    assertThat(BookParser.title("TO WIVES")).isEqualTo("To Wives");
    assertThat(BookParser.title("A VISION FOR YOU")).isEqualTo("A Vision for You");
    assertThat(BookParser.title("THE DOCTOR'S OPINION")).isEqualTo("The Doctor's Opinion");
    assertThat(BookParser.title("Chapter 3: More About Alcoholism"))
        .isEqualTo("More About Alcoholism");
  }
}
