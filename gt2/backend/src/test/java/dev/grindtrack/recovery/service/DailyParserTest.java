package dev.grindtrack.recovery.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.grindtrack.web.BadRequestException;
import org.junit.jupiter.api.Test;

/** A book read by the calendar: date lines, titles, bodies, and what is missing. */
class DailyParserTest {

  private static final String BOOK =
      """
      JANUARY 1

      A NEW BEGINNING

      The first paragraph of the first day, wrapped
      across two lines.

      The second paragraph.

      Jan. 2
      Second Day
      Body of the second day.

      29 February

      This one has no title, just a sentence that runs on past the length a title could be and ends.

      December 31

      LAST DAY

      Closing words.
      """;

  @Test
  void dateLinesInAnyOfTheUsualSpellingsStartAnEntry() {
    DailyParser.Parsed parsed = DailyParser.parse(BOOK);

    assertThat(parsed.entries()).hasSize(4);
    assertThat(parsed.entries())
        .extracting(e -> e.month() * 100 + e.day())
        .containsExactly(101, 102, 229, 1231);
  }

  @Test
  void theTitleIsTheShortLineAfterTheDateAndTheBodyKeepsItsParagraphs() {
    DailyParser.Parsed parsed = DailyParser.parse(BOOK);

    DailyParser.Entry first = parsed.entries().get(0);
    assertThat(first.title()).isEqualTo("A New Beginning");
    assertThat(first.body())
        .isEqualTo(
            "The first paragraph of the first day, wrapped across two lines.\n\nThe second"
                + " paragraph.");
    assertThat(parsed.entries().get(1).title()).isEqualTo("Second Day");
    assertThat(parsed.entries().get(2).title()).isEmpty();
  }

  @Test
  void missingDatesAreCountedAcrossALeapYear() {
    DailyParser.Parsed parsed = DailyParser.parse(BOOK);

    assertThat(parsed.missing()).hasSize(366 - 4);
    assertThat(parsed.missing().get(0)).isEqualTo("Jan 3");
    assertThat(parsed.missing()).doesNotContain("Feb 29", "Dec 31");
  }

  @Test
  void aRepeatedDateKeepsTheLaterCopyAndSaysSo() {
    DailyParser.Parsed parsed = DailyParser.parse("March 3\nFirst\nold.\n\nMarch 3\nSecond\nnew.");

    assertThat(parsed.entries()).hasSize(1);
    assertThat(parsed.entries().get(0).body()).isEqualTo("new.");
    assertThat(parsed.warnings()).anyMatch(w -> w.contains("twice"));
  }

  @Test
  void anImpossibleDateIsSkippedWithAWarning() {
    DailyParser.Parsed parsed = DailyParser.parse("February 30\nNope.\n\nApril 1\nFine\nGood.");

    assertThat(parsed.entries()).hasSize(1);
    assertThat(parsed.warnings()).anyMatch(w -> w.contains("February 30"));
  }

  @Test
  void noDatesAtAllIsRefused() {
    assertThatThrownBy(() -> DailyParser.parse("Some prose with no dates in it."))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("JANUARY 1");
  }
}
