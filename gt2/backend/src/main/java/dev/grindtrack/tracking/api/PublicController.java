package dev.grindtrack.tracking.api;

import dev.grindtrack.tracking.api.Dtos.PublicDay;
import dev.grindtrack.tracking.api.Dtos.PublicStats;
import dev.grindtrack.tracking.domain.DailyLogRepository;
import dev.grindtrack.tracking.service.Stats;
import dev.grindtrack.tracking.service.StatsService;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The public landing page's data: heatmap hours, streak, and totals only. Deliberately excludes
 * every text field — notes, wins, and blockers never leave the authenticated API.
 */
@RestController
@RequestMapping("/api/public")
public class PublicController {

  private final DailyLogRepository dailyLogs;
  private final StatsService statsService;

  public PublicController(DailyLogRepository dailyLogs, StatsService statsService) {
    this.dailyLogs = dailyLogs;
    this.statsService = statsService;
  }

  @GetMapping("/stats")
  public PublicStats publicStats() {
    Stats stats = statsService.compute();
    LocalDate end =
        LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusDays(6);
    LocalDate start = end.minusDays(7 * 26 - 1);
    List<PublicDay> days =
        dailyLogs.findByLogDateBetweenOrderByLogDate(start, end).stream()
            .map(l -> new PublicDay(l.getLogDate().toString(), l.getHours().doubleValue()))
            .toList();
    return new PublicStats(stats.streak(), stats.totalHours(), stats.daysLogged(), days);
  }
}
