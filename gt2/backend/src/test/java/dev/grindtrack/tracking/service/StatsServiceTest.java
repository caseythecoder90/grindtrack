package dev.grindtrack.tracking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.grindtrack.tracking.domain.DailyLog;
import dev.grindtrack.tracking.domain.DailyLogRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StatsServiceTest {

  /** A Wednesday; the Monday of its week is 2026-07-13. */
  private static final LocalDate TODAY = LocalDate.of(2026, 7, 15);

  private static final LocalDate THIS_MONDAY = LocalDate.of(2026, 7, 13);

  private static DailyLog log(LocalDate date, double hours, String... categories) {
    DailyLog log = new DailyLog(date);
    log.update(BigDecimal.valueOf(hours), List.of(categories), null, null, null, null, null);
    return log;
  }

  @Nested
  class Streak {

    @Test
    void countsBackFromTodayWhenTodayIsLogged() {
      Stats stats =
          StatsService.compute(
              List.of(log(TODAY, 1.0), log(TODAY.minusDays(1), 2.0), log(TODAY.minusDays(2), 0.5)),
              TODAY);
      assertThat(stats.streak()).isEqualTo(3);
    }

    @Test
    void countsFromYesterdayWhenTodayNotYetLogged() {
      Stats stats =
          StatsService.compute(
              List.of(log(TODAY.minusDays(1), 2.0), log(TODAY.minusDays(2), 1.0)), TODAY);
      assertThat(stats.streak()).isEqualTo(2);
    }

    @Test
    void gapBreaksTheStreak() {
      Stats stats =
          StatsService.compute(List.of(log(TODAY, 1.0), log(TODAY.minusDays(2), 5.0)), TODAY);
      assertThat(stats.streak()).isEqualTo(1);
    }

    @Test
    void zeroHourDayBreaksTheStreak() {
      Stats stats =
          StatsService.compute(
              List.of(log(TODAY, 1.0), log(TODAY.minusDays(1), 0.0), log(TODAY.minusDays(2), 3.0)),
              TODAY);
      assertThat(stats.streak()).isEqualTo(1);
    }

    @Test
    void zeroWhenNeitherTodayNorYesterdayIsLogged() {
      Stats stats = StatsService.compute(List.of(log(TODAY.minusDays(2), 4.0)), TODAY);
      assertThat(stats.streak()).isZero();
    }

    @Test
    void zeroWithNoLogsAtAll() {
      assertThat(StatsService.compute(List.of(), TODAY).streak()).isZero();
    }
  }

  @Nested
  class WeeklyBuckets {

    @Test
    void alwaysContainsExactlyTheLastTwelveMondays() {
      Stats stats = StatsService.compute(List.of(), TODAY);
      assertThat(stats.weeks()).hasSize(12);
      assertThat(stats.weeks().getFirst().weekStart())
          .isEqualTo(THIS_MONDAY.minusWeeks(11).toString());
      assertThat(stats.weeks().getLast().weekStart()).isEqualTo(THIS_MONDAY.toString());
      assertThat(stats.weeks()).allSatisfy(w -> assertThat(w.hours()).isZero());
    }

    @Test
    void sumsAllLogsOfAWeekUnderItsMonday() {
      Stats stats =
          StatsService.compute(
              List.of(
                  log(THIS_MONDAY, 1.5),
                  log(THIS_MONDAY.plusDays(2), 2.0),
                  log(THIS_MONDAY.minusWeeks(1).plusDays(4), 3.0)),
              TODAY);
      assertThat(stats.weeks().getLast().weekStart()).isEqualTo("2026-07-13");
      assertThat(stats.weeks().getLast().hours()).isEqualTo(3.5);
      assertThat(stats.weeks().get(10).weekStart()).isEqualTo("2026-07-06");
      assertThat(stats.weeks().get(10).hours()).isEqualTo(3.0);
    }

    @Test
    void logsOlderThanTwelveWeeksAreDroppedFromWeeksButNotFromTotals() {
      Stats stats = StatsService.compute(List.of(log(THIS_MONDAY.minusWeeks(12), 8.0)), TODAY);
      assertThat(stats.weeks()).allSatisfy(w -> assertThat(w.hours()).isZero());
      assertThat(stats.totalHours()).isEqualTo(8.0);
    }

    @Test
    void weekHoursAreRoundedToOneDecimal() {
      Stats stats =
          StatsService.compute(
              List.of(log(THIS_MONDAY, 1.11), log(THIS_MONDAY.plusDays(1), 2.22)), TODAY);
      assertThat(stats.weeks().getLast().hours()).isEqualTo(3.3);
    }
  }

  @Nested
  class CategorySplit {

    @Test
    void splitsADaysHoursEvenlyAcrossItsCategories() {
      Stats stats = StatsService.compute(List.of(log(TODAY, 3.0, "java", "aws")), TODAY);
      assertThat(stats.categories())
          .extracting(Stats.CategoryHours::category, Stats.CategoryHours::hours)
          .containsExactlyInAnyOrder(tuple("java", 1.5), tuple("aws", 1.5));
    }

    @Test
    void sumsSharesAcrossDaysAndOrdersMostWorkedFirst() {
      Stats stats =
          StatsService.compute(
              List.of(log(TODAY, 2.0, "java", "aws"), log(TODAY.minusDays(1), 3.0, "aws")), TODAY);
      assertThat(stats.categories())
          .extracting(Stats.CategoryHours::category, Stats.CategoryHours::hours)
          .containsExactly(tuple("aws", 4.0), tuple("java", 1.0));
    }

    @Test
    void uncategorizedDaysContributeNothing() {
      Stats stats =
          StatsService.compute(
              List.of(log(TODAY, 5.0), log(TODAY.minusDays(1), 1.0, "java")), TODAY);
      assertThat(stats.categories())
          .extracting(Stats.CategoryHours::category)
          .containsExactly("java");
    }

    @Test
    void categoryHoursAreRoundedToOneDecimal() {
      Stats stats = StatsService.compute(List.of(log(TODAY, 1.0, "a", "b", "c")), TODAY);
      assertThat(stats.categories()).allSatisfy(c -> assertThat(c.hours()).isEqualTo(0.3));
    }
  }

  @Nested
  class Totals {

    @Test
    void totalHoursIsTheRoundedSumAndDaysLoggedCountsAllRows() {
      Stats stats =
          StatsService.compute(
              List.of(log(TODAY, 0.1), log(TODAY.minusDays(1), 0.2), log(TODAY.minusDays(2), 0.0)),
              TODAY);
      assertThat(stats.totalHours()).isEqualTo(0.3);
      assertThat(stats.daysLogged()).isEqualTo(3);
    }
  }

  @Test
  void computeReadsAllLogsFromTheRepository() {
    DailyLogRepository repository = mock(DailyLogRepository.class);
    LocalDate longAgo = LocalDate.of(2020, 1, 1);
    when(repository.findAll())
        .thenReturn(List.of(log(longAgo, 2.0), log(longAgo.plusDays(1), 3.0)));

    Stats stats = new StatsService(repository).compute();

    assertThat(stats.totalHours()).isEqualTo(5.0);
    assertThat(stats.daysLogged()).isEqualTo(2);
    assertThat(stats.streak()).isZero();
  }
}
