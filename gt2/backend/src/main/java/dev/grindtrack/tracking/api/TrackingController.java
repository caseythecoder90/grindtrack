package dev.grindtrack.tracking.api;

import dev.grindtrack.tracking.api.Dtos.DayRequest;
import dev.grindtrack.tracking.api.Dtos.DayResponse;
import dev.grindtrack.tracking.api.Dtos.WeekRequest;
import dev.grindtrack.tracking.api.Dtos.WeekResponse;
import dev.grindtrack.tracking.domain.DailyLog;
import dev.grindtrack.tracking.domain.DailyLogRepository;
import dev.grindtrack.tracking.domain.WeeklyReview;
import dev.grindtrack.tracking.domain.WeeklyReviewRepository;
import dev.grindtrack.tracking.service.Stats;
import dev.grindtrack.tracking.service.StatsService;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TrackingController {

  private static final int MAX_TEXT_CHARS = 10_000;
  private static final int MAX_CATEGORIES = 50;

  private final DailyLogRepository dailyLogs;
  private final WeeklyReviewRepository weeklyReviews;
  private final StatsService statsService;

  public TrackingController(
      DailyLogRepository dailyLogs,
      WeeklyReviewRepository weeklyReviews,
      StatsService statsService) {
    this.dailyLogs = dailyLogs;
    this.weeklyReviews = weeklyReviews;
    this.statsService = statsService;
  }

  // ---------- daily logs ----------

  @GetMapping("/days")
  public ResponseEntity<?> range(@RequestParam String from, @RequestParam String to) {
    LocalDate fromDate = requireDate(from, "from and to must be YYYY-MM-DD");
    LocalDate toDate = requireDate(to, "from and to must be YYYY-MM-DD");
    List<DayResponse> body =
        dailyLogs.findByLogDateBetweenOrderByLogDate(fromDate, toDate).stream()
            .map(DayResponse::from)
            .toList();
    return ResponseEntity.ok(body);
  }

  @GetMapping("/days/{date}")
  public ResponseEntity<?> day(@PathVariable String date) {
    LocalDate parsed = requireDate(date, "invalid date");
    return ResponseEntity.ok(dailyLogs.findById(parsed).map(DayResponse::from).orElse(null));
  }

  @PutMapping("/days/{date}")
  public ResponseEntity<?> upsertDay(@PathVariable String date, @RequestBody DayRequest body) {
    LocalDate parsed = requireDate(date, "invalid date");
    BigDecimal hours = body.hours() == null ? BigDecimal.ZERO : body.hours();
    validateDayFields(hours, body);
    DailyLog log = dailyLogs.findById(parsed).orElseGet(() -> new DailyLog(parsed));
    log.update(
        hours,
        body.categories(),
        body.focus(),
        body.did(),
        body.wins(),
        body.blockers(),
        body.energy());
    dailyLogs.save(log);
    return ResponseEntity.ok(Map.of("saved", parsed.toString()));
  }

  @DeleteMapping("/days/{date}")
  public ResponseEntity<?> deleteDay(@PathVariable String date) {
    LocalDate parsed = requireDate(date, "invalid date");
    dailyLogs.deleteById(parsed);
    return ResponseEntity.ok(Map.of("deleted", parsed.toString()));
  }

  // ---------- weekly reviews ----------

  @GetMapping("/weeks/{weekStart}")
  public ResponseEntity<?> week(@PathVariable String weekStart) {
    LocalDate monday = mondayOf(weekStart);
    return ResponseEntity.ok(weeklyReviews.findById(monday).map(WeekResponse::from).orElse(null));
  }

  @PutMapping("/weeks/{weekStart}")
  public ResponseEntity<?> upsertWeek(
      @PathVariable String weekStart, @RequestBody WeekRequest body) {
    LocalDate monday = mondayOf(weekStart);
    if (tooLong(
        body.summary(), body.wins(), body.blockers(), body.adjustments(), body.nextFocus())) {
      throw new BadRequest("text fields are limited to " + MAX_TEXT_CHARS + " characters");
    }
    WeeklyReview review = weeklyReviews.findById(monday).orElseGet(() -> new WeeklyReview(monday));
    review.update(
        body.summary(),
        body.wins(),
        body.blockers(),
        body.adjustments(),
        body.nextFocus(),
        body.onTrack());
    weeklyReviews.save(review);
    return ResponseEntity.ok(Map.of("saved", monday.toString()));
  }

  // ---------- stats ----------

  @GetMapping("/stats")
  public Stats stats() {
    return statsService.compute();
  }

  // ---------- validation ----------

  private static void validateDayFields(BigDecimal hours, DayRequest body) {
    if (hours.signum() < 0 || hours.doubleValue() > 24) {
      throw new BadRequest("hours must be 0-24");
    }
    if (body.energy() != null && (body.energy() < 1 || body.energy() > 5)) {
      throw new BadRequest("energy must be 1-5");
    }
    if (body.categories() != null
        && (body.categories().size() > MAX_CATEGORIES
            || body.categories().stream().anyMatch(c -> c == null || c.length() > 100))) {
      throw new BadRequest("too many categories, or a category name over 100 chars");
    }
    if (tooLong(body.focus(), body.did(), body.wins(), body.blockers())) {
      throw new BadRequest("text fields are limited to " + MAX_TEXT_CHARS + " characters");
    }
  }

  private static boolean tooLong(String... values) {
    for (String value : values) {
      if (value != null && value.length() > MAX_TEXT_CHARS) {
        return true;
      }
    }
    return false;
  }

  private static LocalDate requireDate(String value, String message) {
    try {
      return LocalDate.parse(value);
    } catch (DateTimeParseException e) {
      throw new BadRequest(message);
    }
  }

  private static LocalDate mondayOf(String value) {
    return requireDate(value, "invalid date")
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
  }

  @ExceptionHandler(BadRequest.class)
  ResponseEntity<Map<String, String>> onBadRequest(BadRequest e) {
    return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
  }

  private static final class BadRequest extends RuntimeException {
    BadRequest(String message) {
      super(message);
    }
  }
}
