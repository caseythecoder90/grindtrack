package dev.grindtrack.tracking.api;

import dev.grindtrack.tracking.api.Dtos.DayRequest;
import dev.grindtrack.tracking.api.Dtos.DayResponse;
import dev.grindtrack.tracking.api.Dtos.WeekRequest;
import dev.grindtrack.tracking.api.Dtos.WeekResponse;
import dev.grindtrack.tracking.service.Stats;
import dev.grindtrack.tracking.service.StatsService;
import dev.grindtrack.tracking.service.TrackingService;
import dev.grindtrack.web.Requests;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The personal study tracker: daily logs, weekly reviews, and the stats rollup.
 *
 * <p>Two services rather than one because they are genuinely different capabilities that happen to
 * share a URL prefix: {@link TrackingService} writes, {@link StatsService} reads and aggregates.
 */
@RestController
@RequestMapping("/api")
public class TrackingController {

  private static final int MAX_TEXT_CHARS = 10_000;
  private static final String TEXT_TOO_LONG =
      "text fields are limited to " + MAX_TEXT_CHARS + " characters";
  private static final String INVALID_DATE = "invalid date";

  private final TrackingService tracking;
  private final StatsService statsService;

  public TrackingController(TrackingService tracking, StatsService statsService) {
    this.tracking = tracking;
    this.statsService = statsService;
  }

  // ---------- daily logs ----------

  @GetMapping("/days")
  public List<DayResponse> range(@RequestParam String from, @RequestParam String to) {
    String message = "from and to must be YYYY-MM-DD";
    return tracking
        .daysBetween(Requests.requireDate(from, message), Requests.requireDate(to, message))
        .stream()
        .map(DayResponse::from)
        .toList();
  }

  @GetMapping("/days/{date}")
  public ResponseEntity<?> day(@PathVariable String date) {
    return ResponseEntity.ok(
        tracking.day(Requests.requireDate(date, INVALID_DATE)).map(DayResponse::from).orElse(null));
  }

  @PutMapping("/days/{date}")
  public ResponseEntity<?> upsertDay(@PathVariable String date, @RequestBody DayRequest body) {
    LocalDate parsed = Requests.requireDate(date, INVALID_DATE);
    Requests.requireWithin(
        MAX_TEXT_CHARS, TEXT_TOO_LONG, body.focus(), body.did(), body.wins(), body.blockers());

    tracking.saveDay(
        parsed,
        body.hours(),
        body.categories(),
        body.focus(),
        body.did(),
        body.wins(),
        body.blockers(),
        body.energy());
    return ResponseEntity.ok(Map.of("saved", parsed.toString()));
  }

  @DeleteMapping("/days/{date}")
  public ResponseEntity<?> deleteDay(@PathVariable String date) {
    LocalDate parsed = Requests.requireDate(date, INVALID_DATE);
    tracking.deleteDay(parsed);
    return ResponseEntity.ok(Map.of("deleted", parsed.toString()));
  }

  // ---------- weekly reviews ----------

  @GetMapping("/weeks/{weekStart}")
  public ResponseEntity<?> week(@PathVariable String weekStart) {
    return ResponseEntity.ok(
        tracking
            .week(Requests.requireDate(weekStart, INVALID_DATE))
            .map(WeekResponse::from)
            .orElse(null));
  }

  @PutMapping("/weeks/{weekStart}")
  public ResponseEntity<?> upsertWeek(
      @PathVariable String weekStart, @RequestBody WeekRequest body) {
    LocalDate parsed = Requests.requireDate(weekStart, INVALID_DATE);
    Requests.requireWithin(
        MAX_TEXT_CHARS,
        TEXT_TOO_LONG,
        body.summary(),
        body.wins(),
        body.blockers(),
        body.adjustments(),
        body.nextFocus());

    tracking.saveWeek(
        parsed,
        body.summary(),
        body.wins(),
        body.blockers(),
        body.adjustments(),
        body.nextFocus(),
        body.onTrack());
    // Report the Monday the review was filed against, not the date that was sent: saving against a
    // Wednesday lands on that week's row, and echoing the raw input would hide that.
    return ResponseEntity.ok(Map.of("saved", TrackingService.mondayOf(parsed).toString()));
  }

  // ---------- stats ----------

  @GetMapping("/stats")
  public Stats stats() {
    return statsService.compute();
  }
}
