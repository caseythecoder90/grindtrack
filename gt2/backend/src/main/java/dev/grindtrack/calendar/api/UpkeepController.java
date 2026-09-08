package dev.grindtrack.calendar.api;

import dev.grindtrack.calendar.api.CalendarDtos.CompletionResponse;
import dev.grindtrack.calendar.api.CalendarDtos.UpkeepCreateRequest;
import dev.grindtrack.calendar.api.CalendarDtos.UpkeepDoneRequest;
import dev.grindtrack.calendar.api.CalendarDtos.UpkeepListResponse;
import dev.grindtrack.calendar.api.CalendarDtos.UpkeepResponse;
import dev.grindtrack.calendar.api.CalendarDtos.UpkeepUpdateRequest;
import dev.grindtrack.calendar.domain.TaskCategory;
import dev.grindtrack.calendar.service.UpkeepItem;
import dev.grindtrack.calendar.service.UpkeepService;
import dev.grindtrack.web.BadRequestException;
import dev.grindtrack.web.Requests;
import dev.grindtrack.web.Responses.Deleted;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Recurring upkeep: what is due, and recording that it was done. */
@RestController
@RequestMapping("/api/upkeep")
public class UpkeepController {

  private static final int MAX_TITLE_CHARS = 200;
  private static final int MAX_NOTES_CHARS = 2_000;
  private static final int MAX_INTERVAL_DAYS = 3_650;

  private final UpkeepService upkeep;

  public UpkeepController(UpkeepService upkeep) {
    this.upkeep = upkeep;
  }

  /** Everything the screen needs: what is due, most overdue first, plus what has been retired. */
  @GetMapping
  public UpkeepListResponse list() {
    LocalDate today = LocalDate.now();
    return new UpkeepListResponse(
        upkeep.due(today).stream().map(UpkeepResponse::from).toList(),
        upkeep.archived().stream()
            .map(task -> UpkeepResponse.from(UpkeepItem.of(task, today)))
            .toList());
  }

  @GetMapping("/{id}/history")
  public List<CompletionResponse> history(@PathVariable Long id) {
    return upkeep.history(id).stream().map(CompletionResponse::from).toList();
  }

  @PostMapping
  public UpkeepResponse create(@RequestBody UpkeepCreateRequest body) {
    LocalDate today = LocalDate.now();
    return UpkeepResponse.from(
        UpkeepItem.of(
            upkeep.create(
                Requests.requireText(body.title(), "recurring task needs a title", MAX_TITLE_CHARS),
                Requests.enumValue(TaskCategory.class, body.category(), "category"),
                interval(body.intervalDays()),
                lastDone(body.lastDoneOn(), today),
                Requests.optionalText(body.notes(), "notes", MAX_NOTES_CHARS)),
            today));
  }

  /**
   * One tap: it was done today, or on the date given.
   *
   * <p>Its own endpoint rather than a PATCH of {@code lastDoneOn}, because the two are different
   * acts: this logs a completion, and correcting a mistyped date must not.
   */
  @PostMapping("/{id}/done")
  public UpkeepResponse markDone(
      @PathVariable Long id, @RequestBody(required = false) UpkeepDoneRequest body) {

    LocalDate today = LocalDate.now();
    LocalDate given = body == null ? null : lastDone(body.doneOn(), today);
    LocalDate on = given == null ? today : given;
    return upkeep
        .markDone(id, on)
        .map(task -> UpkeepResponse.from(UpkeepItem.of(task, today)))
        .orElseThrow(() -> new NoSuchElementException("upkeep task " + id));
  }

  @PatchMapping("/{id}")
  public UpkeepResponse update(@PathVariable Long id, @RequestBody UpkeepUpdateRequest body) {
    LocalDate today = LocalDate.now();
    return upkeep
        .update(
            id,
            body.title() == null
                ? null
                : Requests.requireText(
                    body.title(), "recurring task needs a title", MAX_TITLE_CHARS),
            Requests.optionalEnum(TaskCategory.class, body.category(), "category"),
            body.intervalDays() == null ? null : interval(body.intervalDays()),
            Requests.optionalText(body.notes(), "notes", MAX_NOTES_CHARS),
            body.active(),
            lastDone(body.lastDoneOn(), today))
        .map(task -> UpkeepResponse.from(UpkeepItem.of(task, today)))
        .orElseThrow(() -> new NoSuchElementException("upkeep task " + id));
  }

  @DeleteMapping("/{id}")
  public Deleted delete(@PathVariable Long id) {
    upkeep.delete(id);
    return Deleted.of(id);
  }

  /** The schema's CHECK, stated here so the answer is a 400 and not a constraint violation. */
  private static int interval(Integer days) {
    if (days == null || days < 1 || days > MAX_INTERVAL_DAYS) {
      throw new BadRequestException("intervalDays must be between 1 and " + MAX_INTERVAL_DAYS);
    }
    return days;
  }

  /**
   * A completion cannot be in the future.
   *
   * <p>Not a schema constraint, because "today" is not a thing the database should decide — but a
   * task marked done next Tuesday would sit quietly at the bottom of the list until then.
   */
  private static LocalDate lastDone(String value, LocalDate today) {
    LocalDate parsed = Requests.optionalDate(value, "dates must be YYYY-MM-DD");
    if (parsed != null && parsed.isAfter(today)) {
      throw new BadRequestException("that date is in the future");
    }
    return parsed;
  }
}
