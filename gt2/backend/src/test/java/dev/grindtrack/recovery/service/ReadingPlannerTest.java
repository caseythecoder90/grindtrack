package dev.grindtrack.recovery.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.grindtrack.recovery.domain.RecoveryParagraph;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReadingPlannerTest {

  private static RecoveryParagraph para(int seq, int page) {
    return new RecoveryParagraph(1L, 1, "One", seq, "x", 50, String.valueOf(page + 57), page);
  }

  @Test
  void twoPagesFromTheCursorFinishingTheParagraphThatCrosses() {
    List<RecoveryParagraph> from =
        List.of(para(10, 3), para(11, 3), para(12, 4), para(13, 4), para(14, 5), para(15, 6));

    List<RecoveryParagraph> part = ReadingPlanner.part(from, 2);

    // Pages 3 and 4: every paragraph that starts on them, however far the last one runs.
    assertThat(part).extracting(RecoveryParagraph::getSeq).containsExactly(10, 11, 12, 13);
    assertThat(part.get(0).getPageLabel()).isEqualTo("60");
  }

  @Test
  void atLeastOneParagraphHoweverManyPagesAreOwed() {
    assertThat(ReadingPlanner.part(List.of(para(0, 0)), 0)).hasSize(1);
    assertThat(ReadingPlanner.part(List.of(), 2)).isEmpty();
  }

  @Test
  void aBacklogTakesMorePages() {
    List<RecoveryParagraph> from =
        List.of(para(0, 0), para(1, 1), para(2, 2), para(3, 3), para(4, 4), para(5, 5));

    assertThat(ReadingPlanner.part(from, 4)).hasSize(4);
  }
}
