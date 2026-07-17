package dev.grindtrack.tracking.api;

import dev.grindtrack.tracking.api.Dtos.DayResponse;
import dev.grindtrack.tracking.api.Dtos.WeekResponse;
import dev.grindtrack.tracking.domain.DailyLog;
import dev.grindtrack.tracking.domain.DailyLogRepository;
import dev.grindtrack.tracking.domain.WeeklyReview;
import dev.grindtrack.tracking.domain.WeeklyReviewRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Serves the full-data JSON backup download, separate from day-to-day tracking endpoints. */
@RestController
@RequestMapping("/api")
public class ExportController {

  private final DailyLogRepository dailyLogs;
  private final WeeklyReviewRepository weeklyReviews;

  public ExportController(DailyLogRepository dailyLogs, WeeklyReviewRepository weeklyReviews) {
    this.dailyLogs = dailyLogs;
    this.weeklyReviews = weeklyReviews;
  }

  @GetMapping("/export")
  public ResponseEntity<Map<String, Object>> export() {
    List<DayResponse> days =
        dailyLogs.findAll().stream()
            .sorted(Comparator.comparing(DailyLog::getLogDate))
            .map(DayResponse::from)
            .toList();
    List<WeekResponse> weeks =
        weeklyReviews.findAll().stream()
            .sorted(Comparator.comparing(WeeklyReview::getWeekStart))
            .map(WeekResponse::from)
            .toList();
    return ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename=\"grindtrack-export.json\"")
        .body(Map.of("dailyLogs", days, "weeklyReviews", weeks));
  }
}
