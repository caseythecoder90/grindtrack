package dev.grindtrack.tracking.service;

import dev.grindtrack.tracking.domain.DailyLog;
import dev.grindtrack.tracking.domain.DailyLogRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

/**
 * Aggregations computed in-process: at ~350 rows/year for the life of the plan, loading and folding
 * in Java is simpler and plenty fast — no SQL gymnastics over comma-separated categories.
 */
@Service
public class StatsService {

  private static final int WEEKS_SHOWN = 12;

  private final DailyLogRepository dailyLogs;

  public StatsService(DailyLogRepository dailyLogs) {
    this.dailyLogs = dailyLogs;
  }

  public Stats compute() {
    return compute(dailyLogs.findAll(), LocalDate.now());
  }

  /** Package-private and clock-explicit so the aggregation logic is testable without Spring. */
  static Stats compute(List<DailyLog> logs, LocalDate today) {
    double totalHours = logs.stream().mapToDouble(l -> l.getHours().doubleValue()).sum();
    return new Stats(
        round1(totalHours),
        logs.size(),
        currentStreak(logs, today),
        weeklyHours(logs, today),
        categoryHours(logs));
  }

  /**
   * Hours per week for the {@value #WEEKS_SHOWN} weeks ending with the current one. Every Monday in
   * the window appears (empty weeks as 0.0); logs outside the window are dropped.
   */
  private static List<Stats.WeekHours> weeklyHours(List<DailyLog> logs, LocalDate today) {
    LocalDate thisMonday = mondayOf(today);
    Map<LocalDate, Double> totals = new TreeMap<>();
    for (LocalDate w = thisMonday.minusWeeks(WEEKS_SHOWN - 1);
        !w.isAfter(thisMonday);
        w = w.plusWeeks(1)) {
      totals.put(w, 0.0);
    }
    for (DailyLog log : logs) {
      totals.computeIfPresent(
          mondayOf(log.getLogDate()), (k, v) -> v + log.getHours().doubleValue());
    }
    return totals.entrySet().stream()
        .map(e -> new Stats.WeekHours(e.getKey().toString(), round1(e.getValue())))
        .toList();
  }

  /**
   * Total hours per category, most-worked first. A day's hours are split evenly across its
   * categories; days without categories contribute nothing.
   */
  private static List<Stats.CategoryHours> categoryHours(List<DailyLog> logs) {
    Map<String, Double> totals = new HashMap<>();
    for (DailyLog log : logs) {
      List<String> categories = log.categoryList();
      if (categories.isEmpty()) {
        continue;
      }
      double share = log.getHours().doubleValue() / categories.size();
      for (String category : categories) {
        totals.merge(category, share, Double::sum);
      }
    }
    return totals.entrySet().stream()
        .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()))
        .map(e -> new Stats.CategoryHours(e.getKey(), round1(e.getValue())))
        .toList();
  }

  /**
   * Consecutive days with logged hours, counting back from today — or from yesterday, so a streak
   * isn't shown as broken before today's work is logged. A day with zero hours breaks it.
   */
  private static int currentStreak(List<DailyLog> logs, LocalDate today) {
    Set<LocalDate> daysWithHours = new HashSet<>();
    for (DailyLog log : logs) {
      if (log.getHours().signum() > 0) {
        daysWithHours.add(log.getLogDate());
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

  private static LocalDate mondayOf(LocalDate date) {
    return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
  }

  private static double round1(double value) {
    return Math.round(value * 10.0) / 10.0;
  }
}
