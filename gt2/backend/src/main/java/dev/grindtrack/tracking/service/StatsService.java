package dev.grindtrack.tracking.service;

import dev.grindtrack.tracking.domain.DailyLogRepository;
import dev.grindtrack.work.domain.WorkLogRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

/**
 * Aggregations computed in-process: at ~350 rows/year for the life of the plan, loading and folding
 * in Java is simpler and plenty fast — no SQL gymnastics over comma-separated categories.
 *
 * <p>The same folds run over three scopes: study (daily_logs), work (work_logs), and the two
 * combined. Reading the work feature's repository from here is deliberate, matching {@code
 * FocusService} — a study/work split is only useful if something can see both sides.
 */
@Service
public class StatsService {

  private static final int WEEKS_SHOWN = 12;
  private static final int HEATMAP_WEEKS = 26;

  private final DailyLogRepository dailyLogs;
  private final WorkLogRepository workLogs;

  public StatsService(DailyLogRepository dailyLogs, WorkLogRepository workLogs) {
    this.dailyLogs = dailyLogs;
    this.workLogs = workLogs;
  }

  /**
   * A day's hours and categories. Both tables are keyed by date and carry hours plus a category
   * list, so the folds below are written against this rather than either entity.
   */
  record DayRow(LocalDate date, double hours, List<String> categories) {}

  public Stats compute() {
    List<DayRow> study =
        dailyLogs.findAll().stream()
            .map(l -> new DayRow(l.getLogDate(), l.getHours().doubleValue(), l.categoryList()))
            .toList();
    List<DayRow> work =
        workLogs.findAll().stream()
            .map(l -> new DayRow(l.getLogDate(), l.getHours().doubleValue(), l.categoryList()))
            .toList();
    return compute(study, work, LocalDate.now());
  }

  /** Package-private and clock-explicit so the aggregation logic is testable without Spring. */
  static Stats compute(List<DayRow> study, List<DayRow> work, LocalDate today) {
    return new Stats(
        scopeStats(study, today), scopeStats(work, today), combined(study, work, today));
  }

  static Stats.ScopeStats scopeStats(List<DayRow> rows, LocalDate today) {
    return scopeStats(rows, today, categoryTotals(rows));
  }

  private static Stats.ScopeStats scopeStats(
      List<DayRow> rows, LocalDate today, Map<String, Double> categoryTotals) {
    return new Stats.ScopeStats(
        round1(rows.stream().mapToDouble(DayRow::hours).sum()),
        rows.size(),
        currentStreak(rows, today),
        daysThisMonth(rows, today),
        weeklyHours(rows, today),
        toCategoryHours(categoryTotals),
        heatmapDays(rows, today));
  }

  /**
   * Study and work merged. Hours are summed per date, but category totals are the sum of the two
   * scopes' <em>category maps</em> rather than a fold over the merged rows: {@link #categoryTotals}
   * splits a day's hours evenly across its categories, so merging rows first would spread the
   * combined hours across the union of that day's study and work categories and mis-attribute both
   * sides.
   */
  private static Stats.ScopeStats combined(List<DayRow> study, List<DayRow> work, LocalDate today) {
    Map<LocalDate, Double> hoursByDate = new TreeMap<>();
    Stream.concat(study.stream(), work.stream())
        .forEach(r -> hoursByDate.merge(r.date(), r.hours(), Double::sum));
    List<DayRow> merged =
        hoursByDate.entrySet().stream()
            .map(e -> new DayRow(e.getKey(), e.getValue(), List.of()))
            .toList();

    Map<String, Double> categories = new HashMap<>(categoryTotals(study));
    categoryTotals(work)
        .forEach((category, hours) -> categories.merge(category, hours, Double::sum));

    return scopeStats(merged, today, categories);
  }

  /**
   * Hours per week for the {@value #WEEKS_SHOWN} weeks ending with the current one. Every Monday in
   * the window appears (empty weeks as 0.0); rows outside the window are dropped.
   */
  private static List<Stats.WeekHours> weeklyHours(List<DayRow> rows, LocalDate today) {
    LocalDate thisMonday = mondayOf(today);
    Map<LocalDate, Double> totals = new TreeMap<>();
    for (LocalDate w = thisMonday.minusWeeks(WEEKS_SHOWN - 1L);
        !w.isAfter(thisMonday);
        w = w.plusWeeks(1)) {
      totals.put(w, 0.0);
    }
    for (DayRow row : rows) {
      totals.computeIfPresent(mondayOf(row.date()), (k, v) -> v + row.hours());
    }
    return totals.entrySet().stream()
        .map(e -> new Stats.WeekHours(e.getKey().toString(), round1(e.getValue())))
        .toList();
  }

  /**
   * The heatmap window: {@value #HEATMAP_WEEKS} weeks ending with the Sunday of the current week.
   * Rows outside it are dropped and the client renders any missing date as zero.
   */
  private static List<Stats.DayHours> heatmapDays(List<DayRow> rows, LocalDate today) {
    LocalDate end = mondayOf(today).plusDays(6);
    LocalDate start = end.minusDays(7L * HEATMAP_WEEKS - 1);
    return rows.stream()
        .filter(r -> !r.date().isBefore(start) && !r.date().isAfter(end))
        .sorted(Comparator.comparing(DayRow::date))
        .map(r -> new Stats.DayHours(r.date().toString(), round1(r.hours())))
        .toList();
  }

  /**
   * Total hours per category. A day's hours are split evenly across its categories; days without
   * categories contribute nothing. Left unrounded so the combined scope can sum two maps without
   * compounding rounding error.
   */
  private static Map<String, Double> categoryTotals(List<DayRow> rows) {
    Map<String, Double> totals = new HashMap<>();
    for (DayRow row : rows) {
      List<String> categories = row.categories();
      if (categories.isEmpty()) {
        continue;
      }
      double share = row.hours() / categories.size();
      for (String category : categories) {
        totals.merge(category, share, Double::sum);
      }
    }
    return totals;
  }

  /** Category totals as the wire shape, most-worked first. */
  private static List<Stats.CategoryHours> toCategoryHours(Map<String, Double> totals) {
    return totals.entrySet().stream()
        .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()))
        .map(e -> new Stats.CategoryHours(e.getKey(), round1(e.getValue())))
        .toList();
  }

  /**
   * Consecutive days with logged hours, counting back from today — or from yesterday, so a streak
   * isn't shown as broken before today's work is logged. A day with zero hours breaks it.
   */
  private static int currentStreak(List<DayRow> rows, LocalDate today) {
    Set<LocalDate> daysWithHours = new HashSet<>();
    for (DayRow row : rows) {
      if (row.hours() > 0) {
        daysWithHours.add(row.date());
      }
    }
    int streak = 0;
    LocalDate cursor = daysWithHours.contains(today) ? today : today.minusDays(1);
    while (daysWithHours.contains(cursor)) {
      streak++;
      cursor = cursor.minusDays(1);
    }
    return streak;
  }

  /**
   * Distinct days in the current calendar month with hours on them. This is what the work scope
   * shows in place of a streak, which weekends off would reset every Saturday.
   */
  private static long daysThisMonth(List<DayRow> rows, LocalDate today) {
    YearMonth month = YearMonth.from(today);
    return rows.stream()
        .filter(r -> r.hours() > 0 && YearMonth.from(r.date()).equals(month))
        .map(DayRow::date)
        .distinct()
        .count();
  }

  private static LocalDate mondayOf(LocalDate date) {
    return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
  }

  private static double round1(double value) {
    return Math.round(value * 10.0) / 10.0;
  }
}
