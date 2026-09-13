package dev.grindtrack.recovery.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.grindtrack.recovery.domain.RecoveryParagraph;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReadingPlannerTest {

  private static RecoveryParagraph para(int seq, int words) {
    return new RecoveryParagraph(1L, 1, "One", seq, "x", words);
  }

  @Test
  void wholeParagraphsUntilTheMinutesAreFilledEndingOnTheOneThatCrosses() {
    List<RecoveryParagraph> from = List.of(para(0, 400), para(1, 400), para(2, 200), para(3, 400));

    List<RecoveryParagraph> part = ReadingPlanner.part(from, 5);

    // 5 minutes is 900 words: 400 + 400 is under, the third crosses, the fourth is tomorrow's.
    assertThat(part).extracting(RecoveryParagraph::getSeq).containsExactly(0, 1, 2);
  }

  @Test
  void atLeastOneParagraphHoweverShortTheSetting() {
    assertThat(ReadingPlanner.part(List.of(para(0, 900)), 1)).hasSize(1);
    assertThat(ReadingPlanner.part(List.of(), 5)).isEmpty();
  }

  @Test
  void theEndOfTheBookIsWhatIsLeft() {
    assertThat(ReadingPlanner.part(List.of(para(9, 50)), 5)).hasSize(1);
  }
}
