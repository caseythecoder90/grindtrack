package dev.grindtrack.calendar.api;

import dev.grindtrack.calendar.api.CalendarDtos.EventCreateRequest;
import dev.grindtrack.calendar.api.CalendarDtos.EventResponse;
import dev.grindtrack.calendar.api.CalendarDtos.EventUpdateRequest;
import dev.grindtrack.calendar.domain.EventKind;
import dev.grindtrack.calendar.service.CalendarService;
import dev.grindtrack.web.BadRequestException;
import dev.grindtrack.web.Requests;
import dev.grindtrack.web.Responses.Deleted;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Events on days.
 *
 * <p>Validation here is all shape — a title within its column, a kind from the enum, a parseable
 * date and time. Whether an end time may exist without a start is a fact about an event and lives
 * on the entity.
 */
@RestController
@RequestMapping("/api/calendar")
public class CalendarController {

  private static final int MAX_TITLE_CHARS = 300;
  private static final int MAX_NOTES_CHARS = 4_000;
  private static final String TIME_MESSAGE = "times must be HH:mm";

  private final CalendarService calendar;

  public CalendarController(CalendarService calendar) {
    this.calendar = calendar;
  }

  /**
   * A month, or an explicit range.
   *
   * <p>{@code month} defaults to the current one, which is what the grid opens on; {@code from} and
   * {@code to} exist for the week view and for the assistant, which asks about "the next 14 days"
   * rather than about a calendar month.
   */
  @GetMapping
  public List<EventResponse> list(
      @RequestParam(required = false) String month,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to) {

    if (from != null || to != null) {
      LocalDate start = Requests.requireDate(from, "from must be YYYY-MM-DD");
      LocalDate end = Requests.requireDate(to, "to must be YYYY-MM-DD");
      if (end.isBefore(start)) {
        throw new BadRequestException("to must not be before from");
      }
      return calendar.range(start, end).stream().map(EventResponse::from).toList();
    }
    YearMonth window = Requests.monthOrNow(month);
    return calendar.month(window).stream().map(EventResponse::from).toList();
  }

  @PostMapping
  public EventResponse create(@RequestBody EventCreateRequest body) {
    return EventResponse.from(
        calendar.create(
            Requests.requireText(body.title(), "calendar event needs a title", MAX_TITLE_CHARS),
            Requests.enumValue(EventKind.class, body.kind(), "kind"),
            Requests.requireDate(body.date(), "date must be YYYY-MM-DD"),
            Requests.optionalTime(body.startTime(), TIME_MESSAGE),
            Requests.optionalTime(body.endTime(), TIME_MESSAGE),
            body.planItemId(),
            Requests.optionalText(body.notes(), "notes", MAX_NOTES_CHARS)));
  }

  @PatchMapping("/{id}")
  public EventResponse update(@PathVariable Long id, @RequestBody EventUpdateRequest body) {
    return calendar
        .update(
            id,
            body.title() == null
                ? null
                : Requests.requireText(
                    body.title(), "calendar event needs a title", MAX_TITLE_CHARS),
            Requests.optionalEnum(EventKind.class, body.kind(), "kind"),
            Requests.optionalDate(body.date(), "date must be YYYY-MM-DD"),
            Requests.optionalTime(body.startTime(), TIME_MESSAGE),
            Requests.optionalTime(body.endTime(), TIME_MESSAGE),
            Boolean.TRUE.equals(body.clearTimes()),
            body.planItemId(),
            Boolean.TRUE.equals(body.clearPlanItem()),
            Requests.optionalText(body.notes(), "notes", MAX_NOTES_CHARS))
        .map(EventResponse::from)
        .orElseThrow(() -> new NoSuchElementException("event " + id));
  }

  @DeleteMapping("/{id}")
  public Deleted delete(@PathVariable Long id) {
    calendar.delete(id);
    return Deleted.of(id);
  }
}
