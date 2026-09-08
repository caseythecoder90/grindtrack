package dev.grindtrack.calendar.api;

import dev.grindtrack.calendar.domain.CalendarEvent;
import dev.grindtrack.calendar.domain.RecurringTask;
import dev.grindtrack.calendar.domain.RecurringTaskCompletion;
import dev.grindtrack.calendar.service.UpkeepItem;
import java.util.List;

/** Request and response shapes for the calendar and upkeep APIs. */
public final class CalendarDtos {

  private CalendarDtos() {}

  /**
   * One event.
   *
   * @param startTime {@code null} for an all-day entry — {@code allDay} is derived from it rather
   *     than stored, so the two can never disagree
   */
  public record EventResponse(
      Long id,
      String title,
      String kind,
      String date,
      String startTime,
      String endTime,
      boolean allDay,
      Long planItemId,
      String notes) {

    public static EventResponse from(CalendarEvent e) {
      return new EventResponse(
          e.getId(),
          e.getTitle(),
          e.getKind().wireValue(),
          e.getEventDate().toString(),
          e.getStartTime() == null ? null : e.getStartTime().toString(),
          e.getEndTime() == null ? null : e.getEndTime().toString(),
          e.isAllDay(),
          e.getPlanItemId(),
          e.getNotes());
    }
  }

  public record EventCreateRequest(
      String title,
      String kind,
      String date,
      String startTime,
      String endTime,
      Long planItemId,
      String notes) {}

  public record EventUpdateRequest(
      String title,
      String kind,
      String date,
      String startTime,
      String endTime,
      Boolean clearTimes,
      Long planItemId,
      Boolean clearPlanItem,
      String notes) {}

  /**
   * An upkeep task with its derived urgency.
   *
   * @param daysOverdue negative before it is due, zero on the day, positive once it is late — one
   *     number the UI can render directly rather than three booleans it has to reconcile
   */
  public record UpkeepResponse(
      Long id,
      String title,
      String category,
      int intervalDays,
      String lastDoneOn,
      String nextDue,
      long daysOverdue,
      String state,
      String notes,
      boolean active) {

    public static UpkeepResponse from(UpkeepItem item) {
      RecurringTask t = item.task();
      return new UpkeepResponse(
          t.getId(),
          t.getTitle(),
          t.getCategory().wireValue(),
          t.getIntervalDays(),
          t.getLastDoneOn() == null ? null : t.getLastDoneOn().toString(),
          item.nextDue().toString(),
          item.daysOverdue(),
          item.state().wireValue(),
          t.getNotes(),
          t.isActive());
    }
  }

  public record UpkeepCreateRequest(
      String title, String category, Integer intervalDays, String lastDoneOn, String notes) {}

  public record UpkeepUpdateRequest(
      String title,
      String category,
      Integer intervalDays,
      String notes,
      Boolean active,
      String lastDoneOn) {}

  public record UpkeepDoneRequest(String doneOn) {}

  public record CompletionResponse(Long id, String doneOn) {

    public static CompletionResponse from(RecurringTaskCompletion c) {
      return new CompletionResponse(c.getId(), c.getDoneOn().toString());
    }
  }

  /** The upkeep screen in one request: what is due, and how much history each row has. */
  public record UpkeepListResponse(List<UpkeepResponse> due, List<UpkeepResponse> archived) {}
}
