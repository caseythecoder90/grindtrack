package dev.grindtrack.tracking.api;

import dev.grindtrack.tracking.api.Dtos.FocusSessionResponse;
import dev.grindtrack.tracking.service.FocusService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/focus")
public class FocusController {

  private final FocusService focusService;

  public FocusController(FocusService focusService) {
    this.focusService = focusService;
  }

  /** Records a finished (or ended-early) session; the day's hours are updated atomically. */
  @PostMapping("/sessions")
  public ResponseEntity<?> record(@RequestBody SessionRequest body) {
    LocalDate date = parseDate(body.date());
    if (date == null) {
      return badRequest("date must be YYYY-MM-DD");
    }
    OffsetDateTime startedAt = parseInstant(body.startedAt());
    if (startedAt == null) {
      return badRequest("startedAt must be an ISO-8601 timestamp");
    }
    if (body.durationMinutes() == null
        || body.durationMinutes() < 1
        || body.durationMinutes() > 1440) {
      return badRequest("durationMinutes must be 1-1440");
    }
    boolean completed = Boolean.TRUE.equals(body.completed());
    return ResponseEntity.ok(
        FocusSessionResponse.from(
            focusService.record(date, startedAt, body.durationMinutes(), completed)));
  }

  @GetMapping("/sessions")
  public ResponseEntity<?> list(@RequestParam String date) {
    LocalDate parsed = parseDate(date);
    if (parsed == null) {
      return badRequest("date must be YYYY-MM-DD");
    }
    return ResponseEntity.ok(
        focusService.sessionsOn(parsed).stream().map(FocusSessionResponse::from).toList());
  }

  private static LocalDate parseDate(String value) {
    try {
      return value == null ? null : LocalDate.parse(value);
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  private static OffsetDateTime parseInstant(String value) {
    try {
      return value == null ? null : OffsetDateTime.parse(value);
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  private static ResponseEntity<Map<String, String>> badRequest(String message) {
    return ResponseEntity.badRequest().body(Map.of("error", message));
  }

  public record SessionRequest(
      String date, String startedAt, Integer durationMinutes, Boolean completed) {}
}
