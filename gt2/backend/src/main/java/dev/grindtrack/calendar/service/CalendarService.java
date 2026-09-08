package dev.grindtrack.calendar.service;

import dev.grindtrack.calendar.domain.CalendarEvent;
import dev.grindtrack.calendar.domain.CalendarEventRepository;
import dev.grindtrack.calendar.domain.EventKind;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Events on days. Thin by design — the invariants live on {@link CalendarEvent}. */
@Service
public class CalendarService {

  private final CalendarEventRepository events;

  public CalendarService(CalendarEventRepository events) {
    this.events = events;
  }

  /**
   * Everything in a month, in day order.
   *
   * <p>The whole month in one request rather than a day at a time: the grid needs a density marker
   * on every cell, so fetching per-day would be thirty round trips to render one screen.
   */
  public List<CalendarEvent> month(YearMonth month) {
    return events.findInRange(month.atDay(1), month.atEndOfMonth());
  }

  public List<CalendarEvent> range(LocalDate from, LocalDate to) {
    return events.findInRange(from, to);
  }

  public List<CalendarEvent> day(LocalDate date) {
    return range(date, date);
  }

  @Transactional
  public CalendarEvent create(
      String title,
      EventKind kind,
      LocalDate date,
      LocalTime start,
      LocalTime end,
      Long planItemId,
      String notes) {

    CalendarEvent event = new CalendarEvent(title, kind, date, start, end);
    event.setPlanItem(planItemId);
    event.setNotes(notes);
    return events.save(event);
  }

  /**
   * Partial update; null means "leave alone".
   *
   * <p>{@code clearPlanItem} exists for the same reason a todo's {@code clearDueDate} does: null
   * cannot mean both "leave it" and "remove it", and a block genuinely needs unlinking.
   *
   * <p>The kind is applied before the plan item so that changing a study block to an appointment
   * drops the link in the same call, rather than leaving hours pointed at a dentist.
   */
  @Transactional
  public Optional<CalendarEvent> update(
      Long id,
      String title,
      EventKind kind,
      LocalDate date,
      LocalTime start,
      LocalTime end,
      boolean clearTimes,
      Long planItemId,
      boolean clearPlanItem,
      String notes) {

    return events
        .findById(id)
        .map(
            event -> {
              if (title != null) {
                event.setTitle(title);
              }
              if (kind != null) {
                event.setKind(kind);
              }
              if (date != null) {
                event.setEventDate(date);
              }
              if (clearTimes) {
                event.setTimes(null, null);
              } else if (start != null || end != null) {
                event.setTimes(
                    start != null ? start : event.getStartTime(),
                    end != null ? end : event.getEndTime());
              }
              if (clearPlanItem) {
                event.setPlanItem(null);
              } else if (planItemId != null) {
                event.setPlanItem(planItemId);
              }
              if (notes != null) {
                event.setNotes(notes);
              }
              return events.save(event);
            });
  }

  @Transactional
  public void delete(Long id) {
    events.deleteById(id);
  }
}
