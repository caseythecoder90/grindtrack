package dev.grindtrack.tracking.api;

import dev.grindtrack.tracking.api.TrackingDtos.DayResponse;
import dev.grindtrack.tracking.api.TrackingDtos.ExportResponse;
import dev.grindtrack.tracking.api.TrackingDtos.WeekResponse;
import dev.grindtrack.tracking.service.TrackingService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The full-data JSON backup download, separate from the day-to-day tracking endpoints.
 *
 * <p>Covers study logs and weekly reviews only. Work hours, finance and the relationship tab are
 * deliberately not here — an export is a file that ends up in a downloads folder, so what goes into
 * it is a decision rather than a default.
 */
@RestController
@RequestMapping("/api")
public class ExportController {

  private final TrackingService tracking;

  public ExportController(TrackingService tracking) {
    this.tracking = tracking;
  }

  @GetMapping("/export")
  public ResponseEntity<ExportResponse> export() {
    List<DayResponse> days = tracking.allDays().stream().map(DayResponse::from).toList();
    List<WeekResponse> weeks = tracking.allWeeks().stream().map(WeekResponse::from).toList();
    return ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename=\"grindtrack-export.json\"")
        .body(new ExportResponse(days, weeks));
  }
}
