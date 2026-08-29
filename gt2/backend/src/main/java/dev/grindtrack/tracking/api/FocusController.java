package dev.grindtrack.tracking.api;

import dev.grindtrack.tracking.api.TrackingDtos.FocusSessionRequest;
import dev.grindtrack.tracking.api.TrackingDtos.FocusSessionResponse;
import dev.grindtrack.tracking.domain.FocusKind;
import dev.grindtrack.tracking.service.FocusService;
import dev.grindtrack.web.BadRequestException;
import dev.grindtrack.web.Requests;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The pomodoro timer's record of what it ran.
 *
 * <p>Everything validated here is shape — a parseable date, a duration inside a day, a known kind.
 * That the minutes then land on the right log is {@link FocusService}'s rule, and it holds whether
 * the session arrives over HTTP or not.
 */
@RestController
@RequestMapping("/api/focus")
public class FocusController {

  /**
   * A session longer than a day is a bug in the caller, not a heroic study stretch. The lower bound
   * is 1 because a zero-minute session would add nothing and still show up in the day's list.
   */
  private static final int MIN_MINUTES = 1;

  private static final int MAX_MINUTES = 24 * 60;

  private final FocusService focusService;

  public FocusController(FocusService focusService) {
    this.focusService = focusService;
  }

  /** Records a finished (or ended-early) session; the day's hours are updated atomically. */
  @PostMapping("/sessions")
  public FocusSessionResponse record(@RequestBody FocusSessionRequest body) {
    return FocusSessionResponse.from(
        focusService.record(
            Requests.requireDate(body.date(), "date must be YYYY-MM-DD"),
            Requests.requireInstant(body.startedAt(), "startedAt must be an ISO-8601 timestamp"),
            requireDuration(body.durationMinutes()),
            Boolean.TRUE.equals(body.completed()),
            Requests.enumValue(FocusKind.class, body.kind(), "kind", FocusKind.STUDY)));
  }

  @GetMapping("/sessions")
  public List<FocusSessionResponse> list(
      @RequestParam String date, @RequestParam(required = false) String kind) {
    return focusService
        .sessionsOn(
            Requests.requireDate(date, "date must be YYYY-MM-DD"),
            Requests.optionalEnum(FocusKind.class, kind, "kind"))
        .stream()
        .map(FocusSessionResponse::from)
        .toList();
  }

  private static int requireDuration(Integer minutes) {
    if (minutes == null || minutes < MIN_MINUTES || minutes > MAX_MINUTES) {
      throw new BadRequestException("durationMinutes must be " + MIN_MINUTES + "-" + MAX_MINUTES);
    }
    return minutes;
  }
}
