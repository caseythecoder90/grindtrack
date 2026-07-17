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

  private final DailyLogRepository dailyLogs;

  public StatsService(DailyLogRepository dailyLogs) {
    this.dailyLogs = dailyLogs;
  }

  public Stats compute() {
    List<DailyLog> all = dailyLogs.findAll();
    double totalHours = all.stream().mapToDouble(l -> l.getHours().doubleValue()).sum();

    LocalDate today = LocalDate.now();
    LocalDate thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    LocalDate windowStart = thisMonday.minusWeeks(11);
    Map<LocalDate, Double> weekTotals = new TreeMap<>();
    for (LocalDate w = windowStart; !w.isAfter(thisMonday); w = w.plusWeeks(1)) {
      weekTotals.put(w, 0.0);
    }
    Map<String, Double> categoryTotals = new HashMap<>();
    Set<LocalDate> daysWithHours = new HashSet<>();

    for (DailyLog log : all) {
      double hours = log.getHours().doubleValue();
      if (hours > 0) {
        daysWithHours.add(log.getLogDate());
      }
      LocalDate monday = log.getLogDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
      weekTotals.computeIfPresent(monday, (k, v) -> v + hours);
      List<String> cats = log.categoryList();
      if (!cats.isEmpty()) {
        double share = hours / cats.size();
        for (String cat : cats) {
          categoryTotals.merge(cat, share, Double::sum);
        }
      }
    }

    int streak = 0;
    LocalDate cursor = daysWithHours.contains(today) ? today : today.minusDays(1);
    while (daysWithHours.contains(cursor)) {
      streak++;
      cursor = cursor.minusDays(1);
    }

    List<Stats.WeekHours> weeks =
        weekTotals.entrySet().stream()
            .map(e -> new Stats.WeekHours(e.getKey().toString(), round1(e.getValue())))
            .toList();
    List<Stats.CategoryHours> categories =
        categoryTotals.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()))
            .map(e -> new Stats.CategoryHours(e.getKey(), round1(e.getValue())))
            .toList();

    return new Stats(round1(totalHours), all.size(), streak, weeks, categories);
  }

  private static double round1(double value) {
    return Math.round(value * 10.0) / 10.0;
  }
}
