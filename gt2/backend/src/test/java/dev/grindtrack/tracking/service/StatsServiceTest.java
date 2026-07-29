package dev.grindtrack.tracking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.grindtrack.tracking.domain.DailyLog;
import dev.grindtrack.tracking.domain.DailyLogRepository;
import dev.grindtrack.tracking.service.StatsService.DayRow;
import dev.grindtrack.work.domain.WorkLog;
import dev.grindtrack.work.domain.WorkLogRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StatsServiceTest {

  /** A Wednesday; the Monday of its week is 2026-07-13. */
  private static final LocalDate TODAY = LocalDate.of(2026, 7, 15);

  private static final LocalDate THIS_MONDAY = LocalDate.of(2026, 7, 13);

  private static DayRow row(LocalDate date, double hours, String... categories) {
    return new DayRow(date, hours, List.of(categories));
  }

  /** One scope's stats, which is what most of the folds below are about. */
  private static Stats.ScopeStats scope(List<DayRow> rows) {
    return StatsService.scopeStats(rows, TODAY);
  }

  @Nested
  class Streak {

    @Test
    void countsBackFromTodayWhenTodayIsLogged() {
      assertThat(
              scope(
                      List.of(
                          row(TODAY, 1.0),
                          row(TODAY.minusDays(1), 2.0),
                          row(TODAY.minusDays(2), 0.5)))
                  .streak())
          .isEqualTo(3);
    }

    @Test
    void countsFromYesterdayWhenTodayNotYetLogged() {
      assertThat(
              scope(List.of(row(TODAY.minusDays(1), 2.0), row(TODAY.minusDays(2), 1.0))).streak())
          .isEqualTo(2);
    }

    @Test
    void gapBreaksTheStreak() {
      assertThat(scope(List.of(row(TODAY, 1.0), row(TODAY.minusDays(2), 5.0))).streak())
          .isEqualTo(1);
    }

    @Test
    void zeroHourDayBreaksTheStreak() {
      assertThat(
              scope(
                      List.of(
                          row(TODAY, 1.0),
                          row(TODAY.minusDays(1), 0.0),
                          row(TODAY.minusDays(2), 3.0)))
                  .streak())
          .isEqualTo(1);
    }

    @Test
    void zeroWhenNeitherTodayNorYesterdayIsLogged() {
      assertThat(scope(List.of(row(TODAY.minusDays(2), 4.0))).streak()).isZero();
    }

    @Test
    void zeroWithNoLogsAtAll() {
      assertThat(scope(List.of()).streak()).isZero();
    }
  }

  @Nested
  class DaysThisMonth {

    @Test
    void countsOnlyDaysWithHoursInTheCurrentCalendarMonth() {
      Stats.ScopeStats stats =
          scope(
              List.of(
                  row(TODAY, 8.0),
                  row(TODAY.minusDays(1), 7.5),
                  row(TODAY.minusDays(2), 0.0), // no hours — not counted
                  row(LocalDate.of(2026, 6, 30), 8.0))); // previous month
      assertThat(stats.daysThisMonth()).isEqualTo(2);
    }

    @Test
    void isZeroWithNoLogs() {
      assertThat(scope(List.of()).daysThisMonth()).isZero();
    }
  }

  @Nested
  class WeeklyBuckets {

    @Test
    void alwaysContainsExactlyTheLastTwelveMondays() {
      Stats.ScopeStats stats = scope(List.of());
      assertThat(stats.weeks()).hasSize(12);
      assertThat(stats.weeks().getFirst().weekStart())
          .isEqualTo(THIS_MONDAY.minusWeeks(11).toString());
      assertThat(stats.weeks().getLast().weekStart()).isEqualTo(THIS_MONDAY.toString());
      assertThat(stats.weeks()).allSatisfy(w -> assertThat(w.hours()).isZero());
    }

    @Test
    void sumsAllLogsOfAWeekUnderItsMonday() {
      Stats.ScopeStats stats =
          scope(
              List.of(
                  row(THIS_MONDAY, 1.5),
                  row(THIS_MONDAY.plusDays(2), 2.0),
                  row(THIS_MONDAY.minusWeeks(1).plusDays(4), 3.0)));
      assertThat(stats.weeks().getLast().weekStart()).isEqualTo("2026-07-13");
      assertThat(stats.weeks().getLast().hours()).isEqualTo(3.5);
      assertThat(stats.weeks().get(10).weekStart()).isEqualTo("2026-07-06");
      assertThat(stats.weeks().get(10).hours()).isEqualTo(3.0);
    }

    @Test
    void logsOlderThanTwelveWeeksAreDroppedFromWeeksButNotFromTotals() {
      Stats.ScopeStats stats = scope(List.of(row(THIS_MONDAY.minusWeeks(12), 8.0)));
      assertThat(stats.weeks()).allSatisfy(w -> assertThat(w.hours()).isZero());
      assertThat(stats.totalHours()).isEqualTo(8.0);
    }

    @Test
    void weekHoursAreRoundedToOneDecimal() {
      Stats.ScopeStats stats =
          scope(List.of(row(THIS_MONDAY, 1.11), row(THIS_MONDAY.plusDays(1), 2.22)));
      assertThat(stats.weeks().getLast().hours()).isEqualTo(3.3);
    }
  }

  @Nested
  class HeatmapDays {

    @Test
    void keepsOnlyTheLastTwentySixWeeksEndingWithThisSunday() {
      LocalDate thisSunday = THIS_MONDAY.plusDays(6);
      LocalDate windowStart = thisSunday.minusDays(7 * 26 - 1);
      Stats.ScopeStats stats =
          scope(
              List.of(
                  row(windowStart.minusDays(1), 1.0), // just outside
                  row(windowStart, 2.0),
                  row(thisSunday, 3.0),
                  row(thisSunday.plusDays(1), 4.0))); // future, outside
      assertThat(stats.days())
          .extracting(Stats.DayHours::date)
          .containsExactly(windowStart.toString(), thisSunday.toString());
    }

    @Test
    void isOrderedByDate() {
      Stats.ScopeStats stats =
          scope(
              List.of(row(TODAY, 1.0), row(TODAY.minusDays(3), 2.0), row(TODAY.minusDays(1), 3.0)));
      assertThat(stats.days())
          .extracting(Stats.DayHours::date)
          .containsExactly(
              TODAY.minusDays(3).toString(), TODAY.minusDays(1).toString(), TODAY.toString());
    }
  }

  @Nested
  class CategorySplit {

    @Test
    void splitsADaysHoursEvenlyAcrossItsCategories() {
      assertThat(scope(List.of(row(TODAY, 3.0, "java", "aws"))).categories())
          .extracting(Stats.CategoryHours::category, Stats.CategoryHours::hours)
          .containsExactlyInAnyOrder(tuple("java", 1.5), tuple("aws", 1.5));
    }

    @Test
    void sumsSharesAcrossDaysAndOrdersMostWorkedFirst() {
      assertThat(
              scope(List.of(row(TODAY, 2.0, "java", "aws"), row(TODAY.minusDays(1), 3.0, "aws")))
                  .categories())
          .extracting(Stats.CategoryHours::category, Stats.CategoryHours::hours)
          .containsExactly(tuple("aws", 4.0), tuple("java", 1.0));
    }

    @Test
    void uncategorizedDaysContributeNothing() {
      assertThat(scope(List.of(row(TODAY, 5.0), row(TODAY.minusDays(1), 1.0, "java"))).categories())
          .extracting(Stats.CategoryHours::category)
          .containsExactly("java");
    }

    @Test
    void categoryHoursAreRoundedToOneDecimal() {
      assertThat(scope(List.of(row(TODAY, 1.0, "a", "b", "c"))).categories())
          .allSatisfy(c -> assertThat(c.hours()).isEqualTo(0.3));
    }
  }

  @Nested
  class Totals {

    @Test
    void totalHoursIsTheRoundedSumAndDaysLoggedCountsAllRows() {
      Stats.ScopeStats stats =
          scope(
              List.of(row(TODAY, 0.1), row(TODAY.minusDays(1), 0.2), row(TODAY.minusDays(2), 0.0)));
      assertThat(stats.totalHours()).isEqualTo(0.3);
      assertThat(stats.daysLogged()).isEqualTo(3);
    }
  }

  @Nested
  class CombinedScope {

    @Test
    void keepsTheTwoScopesSeparateAndSumsThemIntoAll() {
      Stats stats = StatsService.compute(List.of(row(TODAY, 2.0)), List.of(row(TODAY, 6.0)), TODAY);

      assertThat(stats.study().totalHours()).isEqualTo(2.0);
      assertThat(stats.work().totalHours()).isEqualTo(6.0);
      assertThat(stats.all().totalHours()).isEqualTo(8.0);
    }

    @Test
    void aDayLoggedInBothScopesCountsOnceInAll() {
      Stats stats = StatsService.compute(List.of(row(TODAY, 2.0)), List.of(row(TODAY, 6.0)), TODAY);

      assertThat(stats.all().daysLogged()).isEqualTo(1);
      assertThat(stats.all().days())
          .extracting(Stats.DayHours::date, Stats.DayHours::hours)
          .containsExactly(tuple(TODAY.toString(), 8.0));
    }

    @Test
    void aDayLoggedInOnlyOneScopeStillAppearsInAll() {
      Stats stats =
          StatsService.compute(
              List.of(row(TODAY, 2.0)), List.of(row(TODAY.minusDays(1), 6.0)), TODAY);

      assertThat(stats.all().daysLogged()).isEqualTo(2);
      assertThat(stats.all().totalHours()).isEqualTo(8.0);
    }

    @Test
    void combinedStreakSpansDaysCoveredByEitherScope() {
      // Study on Wed only, work on Tue only. Each scope on its own is a 1-day streak (work's
      // counts from yesterday, since today isn't logged yet); together they cover both days.
      Stats stats =
          StatsService.compute(
              List.of(row(TODAY, 2.0)), List.of(row(TODAY.minusDays(1), 8.0)), TODAY);

      assertThat(stats.study().streak()).isEqualTo(1);
      assertThat(stats.work().streak()).isEqualTo(1);
      assertThat(stats.all().streak()).isEqualTo(2);
    }

    @Test
    void categoryTotalsAreSummedPerScopeNotSplitAcrossTheUnion() {
      // 2h study over one category and 6h work over two. Splitting the merged 8h day across all
      // three categories would give 2.67 each; the right answer keeps each scope's own split.
      Stats stats =
          StatsService.compute(
              List.of(row(TODAY, 2.0, "go")),
              List.of(row(TODAY, 6.0, "meetings", "code review")),
              TODAY);

      assertThat(stats.all().categories())
          .extracting(Stats.CategoryHours::category, Stats.CategoryHours::hours)
          .containsExactlyInAnyOrder(
              tuple("go", 2.0), tuple("meetings", 3.0), tuple("code review", 3.0));
    }

    @Test
    void aCategoryUsedByBothScopesIsSummed() {
      Stats stats =
          StatsService.compute(
              List.of(row(TODAY, 2.0, "learning")), List.of(row(TODAY, 3.0, "learning")), TODAY);

      assertThat(stats.all().categories())
          .extracting(Stats.CategoryHours::category, Stats.CategoryHours::hours)
          .containsExactly(tuple("learning", 5.0));
    }
  }

  @Test
  void computeReadsBothRepositories() {
    DailyLogRepository dailyLogs = mock(DailyLogRepository.class);
    WorkLogRepository workLogs = mock(WorkLogRepository.class);
    LocalDate longAgo = LocalDate.of(2020, 1, 1);

    DailyLog studyLog = new DailyLog(longAgo);
    studyLog.update(BigDecimal.valueOf(2.0), List.of(), null, null, null, null, null);
    WorkLog workLog = new WorkLog(longAgo.plusDays(1));
    workLog.update(BigDecimal.valueOf(3.0), List.of(), null, null, null, null, null);

    when(dailyLogs.findAll()).thenReturn(List.of(studyLog));
    when(workLogs.findAll()).thenReturn(List.of(workLog));

    Stats stats = new StatsService(dailyLogs, workLogs).compute();

    assertThat(stats.study().totalHours()).isEqualTo(2.0);
    assertThat(stats.work().totalHours()).isEqualTo(3.0);
    assertThat(stats.all().totalHours()).isEqualTo(5.0);
    assertThat(stats.all().daysLogged()).isEqualTo(2);
  }
}
