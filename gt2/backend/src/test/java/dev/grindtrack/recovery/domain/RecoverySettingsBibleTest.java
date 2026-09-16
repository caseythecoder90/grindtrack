package dev.grindtrack.recovery.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** The passage cursor: one a day, another on request, and the old date plan's place kept. */
class RecoverySettingsBibleTest {

  private static final LocalDate DAY = LocalDate.of(2026, 9, 16);

  @Test
  void theFirstReadStartsWhereTheDatePlanWasAndTheSameDayDoesNotMove() {
    RecoverySettings s = new RecoverySettings();
    assertThat(s.biblePassage(DAY, 41, 100)).isEqualTo(41);
    assertThat(s.biblePassage(DAY, 41, 100)).isEqualTo(41);
    assertThat(s.biblePassage(DAY, 99, 100)).isEqualTo(41);
  }

  @Test
  void aNewDayMovesOnOnceHoweverManyDaysWereMissed() {
    RecoverySettings s = new RecoverySettings();
    s.biblePassage(DAY, 41, 100);
    assertThat(s.biblePassage(DAY.plusDays(1), 0, 100)).isEqualTo(42);
    assertThat(s.biblePassage(DAY.plusDays(9), 0, 100)).isEqualTo(43);
  }

  @Test
  void anotherPassageMovesOnNowAndTomorrowFollowsFromThere() {
    RecoverySettings s = new RecoverySettings();
    s.biblePassage(DAY, 41, 100);
    assertThat(s.nextBiblePassage(DAY, 0, 100)).isEqualTo(42);
    assertThat(s.biblePassage(DAY, 0, 100)).isEqualTo(42);
    assertThat(s.biblePassage(DAY.plusDays(1), 0, 100)).isEqualTo(43);
  }

  @Test
  void theEndWrapsAndARestartGoesToTheFirst() {
    RecoverySettings s = new RecoverySettings();
    s.biblePassage(DAY, 99, 100);
    assertThat(s.nextBiblePassage(DAY, 0, 100)).isEqualTo(0);
    s.restartBiblePlan(DAY.plusDays(3));
    assertThat(s.biblePassage(DAY.plusDays(3), 77, 100)).isEqualTo(0);
    assertThat(s.getBiblePlanStart()).isEqualTo(DAY.plusDays(3));
  }
}
