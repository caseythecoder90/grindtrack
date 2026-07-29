package dev.grindtrack.tracking.api;

import dev.grindtrack.tracking.api.Dtos.PublicDay;
import dev.grindtrack.tracking.api.Dtos.PublicStats;
import dev.grindtrack.tracking.service.Stats;
import dev.grindtrack.tracking.service.StatsService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The public landing page's data: heatmap hours, streak, and totals only. Deliberately excludes
 * every text field — notes, wins, and blockers never leave the authenticated API.
 *
 * <p>Equally deliberately, this serves the <strong>study scope only</strong>. Day-job hours and
 * project names are not public, so the work and combined scopes stay behind {@code /api/stats}.
 */
@RestController
@RequestMapping("/api/public")
public class PublicController {

  private final StatsService statsService;

  public PublicController(StatsService statsService) {
    this.statsService = statsService;
  }

  @GetMapping("/stats")
  public PublicStats publicStats() {
    Stats.ScopeStats study = statsService.compute().study();
    List<PublicDay> days =
        study.days().stream().map(d -> new PublicDay(d.date(), d.hours())).toList();
    return new PublicStats(study.streak(), study.totalHours(), study.daysLogged(), days);
  }
}
