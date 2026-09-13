package dev.grindtrack.recovery.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A typeset book's pages, as the PDF text comes out, into chapters and pages and paragraphs. The
 * words are made up; the shapes — running heads, footers, hyphens, drop caps — are the real ones.
 */
class PdfBookParserTest {

  private static final String P = String.valueOf(PdfText.PARAGRAPH);

  /** Forty-odd words: enough for a chapter to be a chapter and not a note that folds away. */
  private static final String LONG =
      "Enough words here to make this a chapter and not a note, which takes forty of them at"
          + " the least, so here they are, one after another, in a single paragraph, until the"
          + " count is reached and passed and the point is made.";

  private static PdfBookParser.Source chapter() {
    return new PdfBookParser.Source(
        "en_book_chapt3.pdf",
        List.of(
            List.of(
                P + "Alco_1893007162_6p_01_r5.qxd  4/4/03  11:17 AM  Page 30",
                P + "Chapter 3",
                P + "MORE ABOUT THE ROAD",
                P + "W e, the walkers, know the road",
                "well enough to say where it goes and where it",
                P + "does not; that is the whole of it.",
                P + "A second paragraph begins here and runs to a hy-",
                "phenated word across the line, then ends.",
                "30"),
            List.of(
                P + "Alco_1893007162_6p_01_r5.qxd  4/4/03  11:17 AM  Page 31",
                P + "31 MORE ABOUT THE ROAD",
                P + "The third paragraph opens the next page with a",
                "line that goes on, and another that goes on to",
                P + "the end of the sentence.",
                P + "1. A numbered item.",
                P + "2. Another numbered item.")));
  }

  @Test
  void chaptersPagesAndParagraphsComeOutOfTheShapeOfThePage() {
    PdfBookParser.Parsed parsed = PdfBookParser.parse(List.of(chapter()));

    assertThat(parsed.chapters()).hasSize(1);
    assertThat(parsed.chapters().get(0).title()).isEqualTo("More About the Road");
    assertThat(parsed.chapters().get(0).firstPage()).isEqualTo("30");
    assertThat(parsed.chapters().get(0).lastPage()).isEqualTo("31");
    assertThat(parsed.paragraphs())
        .extracting(PdfBookParser.Paragraph::body)
        .containsExactly(
            "We, the walkers, know the road well enough to say where it goes and where it does not;"
                + " that is the whole of it.",
            "A second paragraph begins here and runs to a hyphenated word across the line, then"
                + " ends.",
            "The third paragraph opens the next page with a line that goes on, and another that"
                + " goes on to the end of the sentence.",
            "1. A numbered item.",
            "2. Another numbered item.");
    assertThat(parsed.paragraphs())
        .extracting(PdfBookParser.Paragraph::pageLabel)
        .containsExactly("30", "30", "31", "31", "31");
    assertThat(parsed.paragraphs())
        .extracting(PdfBookParser.Paragraph::pageSeq)
        .containsExactly(0, 0, 1, 1, 1);
    assertThat(parsed.pages()).isEqualTo(2);
    assertThat(parsed.warnings()).isEmpty();
  }

  @Test
  void filesGoInTheBooksOrderByNameAndTheOnesNotForReadingAreSkipped() {
    PdfBookParser.Source appendix =
        new PdfBookParser.Source(
            "en_book_appendiceii.pdf",
            List.of(List.of(P + "II", P + "A NOTE", P + "Text of the note. " + LONG, "567")));
    PdfBookParser.Source foreword =
        new PdfBookParser.Source(
            "en_book_forewordfirstedition.pdf",
            List.of(
                List.of(
                    P + "xiii FOREWORD",
                    P + "FOREWORD TO FIRST EDITION",
                    P + "Text of the foreword. " + LONG)));
    PdfBookParser.Source contents =
        new PdfBookParser.Source(
            "en_book_contents.pdf", List.of(List.of(P + "CONTENTS", P + "Chapter 1 ... 1")));

    PdfBookParser.Parsed parsed =
        PdfBookParser.parse(List.of(appendix, chapter(), contents, foreword));

    assertThat(parsed.chapters())
        .extracting(PdfBookParser.Chapter::title)
        .containsExactly("Foreword to First Edition", "More About the Road", "A Note");
    assertThat(parsed.paragraphs().get(0).pageLabel()).isEqualTo("xiii");
    assertThat(parsed.paragraphs().get(0).pageSeq()).isZero();
    // Pages keep counting across files: the foreword's one, the chapter's two, the appendix's one.
    assertThat(parsed.pages()).isEqualTo(4);
    assertThat(parsed.warnings()).anyMatch(w -> w.contains("Skipped en_book_contents.pdf"));
  }

  @Test
  void aPartHeadingAndAStoryTitleOnTheSamePageAreOneChapterAndTheNextStoryIsAnother() {
    PdfBookParser.Source stories =
        new PdfBookParser.Source(
            "en_book_personalstories_partI.pdf",
            List.of(
                List.of(
                    P + "PART I",
                    P + "PIONEERS OF THE ROAD",
                    P + "What this part is about. " + LONG,
                    "171"),
                List.of(
                    P + "172 THE ROAD",
                    P + "THE FIRST STORY",
                    P + "How it began for one of them. " + LONG,
                    P + "And how it went.")));

    PdfBookParser.Parsed parsed = PdfBookParser.parse(List.of(stories));

    assertThat(parsed.chapters())
        .extracting(PdfBookParser.Chapter::title)
        .containsExactly("Part I · Pioneers of the Road", "The First Story");
    assertThat(parsed.chapters().get(1).firstPage()).isEqualTo("172");
  }

  @Test
  void aNoteThatOpensASectionOfNotesNamesTheSection() {
    PdfBookParser.Source intro =
        new PdfBookParser.Source(
            "en_book_personalstories.pdf",
            List.of(
                List.of(
                    P + "PERSONAL STORIES",
                    P + "A note about the stories.",
                    P + "PART I",
                    P + "THE PIONEERS",
                    P + "Who they were, in a line.",
                    P + "PART II",
                    P + "THE LATER ONES",
                    P + "Who they were, in another line.",
                    "165")));

    PdfBookParser.Parsed parsed = PdfBookParser.parse(List.of(intro));

    assertThat(parsed.chapters())
        .extracting(PdfBookParser.Chapter::title)
        .containsExactly("Personal Stories");
    assertThat(parsed.paragraphs())
        .extracting(PdfBookParser.Paragraph::body)
        .containsExactly(
            "A note about the stories.",
            "Part I · The Pioneers",
            "Who they were, in a line.",
            "Part II · The Later Ones",
            "Who they were, in another line.");
  }

  @Test
  void aNoteOfAFewWordsBetweenChaptersFoldsIntoItsNeighbourWithItsTitleKept() {
    PdfBookParser.Source note =
        new PdfBookParser.Source(
            "en_book_appendicei.pdf",
            List.of(
                List.of(
                    P + "APPENDICES",
                    P + "A few words about the appendices.",
                    P + "I",
                    P + "THE TRADITION",
                    P
                        + "The tradition itself. "
                        + LONG
                        + " "
                        + LONG
                        + " "
                        + LONG
                        + " "
                        + LONG
                        + " "
                        + LONG,
                    "561")));
    PdfBookParser.Source story =
        new PdfBookParser.Source(
            "en_book_personalstories.pdf",
            List.of(
                List.of(
                    P + "PERSONAL STORIES",
                    P + "A short note. " + LONG,
                    P + "PART I",
                    P + "A shorter one.",
                    "165")));

    PdfBookParser.Parsed parsed = PdfBookParser.parse(List.of(note, story));

    assertThat(parsed.chapters())
        .extracting(PdfBookParser.Chapter::title)
        .containsExactly("Personal Stories", "The Tradition");
    // The stories note keeps the part line as text; the appendices line opens the tradition.
    assertThat(parsed.paragraphs().get(0).body()).startsWith("A short note.");
    assertThat(parsed.paragraphs().get(1).body()).isEqualTo("Part I");
    assertThat(parsed.paragraphs().get(2).body()).isEqualTo("A shorter one.");
    assertThat(parsed.paragraphs().get(3).body()).isEqualTo("Appendices");
    assertThat(parsed.paragraphs().get(3).chapterTitle()).isEqualTo("The Tradition");
    assertThat(parsed.paragraphs())
        .extracting(PdfBookParser.Paragraph::seq)
        .containsExactly(0, 1, 2, 3, 4, 5);
  }

  @Test
  void aTitleThatRunsToTwoLinesAndOneEndingInAnAbbreviation() {
    PdfBookParser.Source source =
        new PdfBookParser.Source(
            "en_book_appendicev.pdf",
            List.of(
                List.of(P + "V", P + "THE VIEW ON A.A.", P + "The view. " + LONG, "572"),
                List.of(
                    P + "573 THE VIEW",
                    P + "ALCOHOLIC ANONYMOUS",
                    P + "NUMBER THREE",
                    P + "And the story. " + LONG)));

    PdfBookParser.Parsed parsed = PdfBookParser.parse(List.of(source));

    assertThat(parsed.chapters())
        .extracting(PdfBookParser.Chapter::title)
        .containsExactly("The View on A.A.", "Alcoholic Anonymous Number Three");
  }

  @Test
  void dropCaps() {
    assertThat(PdfBookParser.dropCap("W e, of the road, know")).isEqualTo("We, of the road, know");
    assertThat(PdfBookParser.dropCap("M ost of us")).isEqualTo("Most of us");
    assertThat(PdfBookParser.dropCap("I n the preceding chapters"))
        .isEqualTo("In the preceding chapters");
    assertThat(PdfBookParser.dropCap("A short note.")).isEqualTo("A short note.");
    assertThat(PdfBookParser.dropCap("I think so.")).isEqualTo("I think so.");
    assertThat(PdfBookParser.dropCap("War fever ran high")).isEqualTo("War fever ran high");
  }

  @Test
  void runningHeadsAreRecognisedInBothTheirShapes() {
    assertThat(PdfBookParser.labelOf("18 ALCOHOLICS ANONYMOUS ")).isEqualTo("18");
    assertThat(PdfBookParser.labelOf("DOCTOR BOB’S NIGHTMARE 173")).isEqualTo("173");
    assertThat(PdfBookParser.labelOf("xvi FOREWORD TO SECOND EDITION")).isEqualTo("xvi");
    assertThat(PdfBookParser.labelOf("569")).isEqualTo("569");
    assertThat(PdfBookParser.labelOf("In 1951 the award was given")).isNull();
    assertThat(PdfBookParser.labelOf("I. Final responsibility")).isNull();
  }

  @Test
  void ranks() {
    assertThat(PdfBookParser.rank("en_bigbook_preface.pdf"))
        .isLessThan(PdfBookParser.rank("en_bigbook_forewordfirstedition.pdf"));
    assertThat(PdfBookParser.rank("en_bigbook_foreworddoctorsopinion.pdf"))
        .isLessThan(PdfBookParser.rank("en_bigbook_chapt1.pdf"));
    assertThat(PdfBookParser.rank("en_bigbook_chapt2.pdf"))
        .isLessThan(PdfBookParser.rank("en_bigbook_chapt10.pdf"));
    assertThat(PdfBookParser.rank("en_bigbook_chapt11.pdf"))
        .isLessThan(PdfBookParser.rank("en_bigbook_personalstories.pdf"));
    assertThat(PdfBookParser.rank("en_bigbook_personalstories.pdf"))
        .isLessThan(PdfBookParser.rank("en_bigbook_personalstories_partI.pdf"));
    assertThat(PdfBookParser.rank("en_bigbook_personalstories_partIII.pdf"))
        .isLessThan(PdfBookParser.rank("en_bigbook_appendicei.pdf"));
    assertThat(PdfBookParser.rank("en_bigbook_appendicevi.pdf"))
        .isLessThan(PdfBookParser.rank("en_bigbook_appendicevii_.pdf"));
    assertThat(PdfBookParser.skipped("en_bigbook_copyright.pdf")).isTrue();
  }
}
