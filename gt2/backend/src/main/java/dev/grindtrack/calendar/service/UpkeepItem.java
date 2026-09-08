package dev.grindtrack.calendar.service;

import dev.grindtrack.calendar.domain.RecurringTask;
import java.time.LocalDate;

/**
 * A recurring task with the two things a list of them is read for: when it is next due, and how
 * that compares to today.
 *
 * <p>Its own file rather than a nested record, per the convention: a computed result that a screen
 * renders directly is a type the frontend depends on, and burying it inside the service makes it
 * look like an implementation detail of one method.
 */
public record UpkeepItem(RecurringTask task, LocalDate nextDue, long daysOverdue, State state) {

  /**
   * How urgent this is.
   *
   * <p>Three buckets rather than a number, because the screen groups by them and the boundary
   * belongs in one place. {@code DUE_SOON} is a week, which is roughly how far ahead you can
   * usefully act on "the filter needs changing".
   */
  public enum State {
    OVERDUE,
    DUE_SOON,
    LATER;

    public String wireValue() {
      return name().toLowerCase(java.util.Locale.ROOT);
    }
  }

  private static final int DUE_SOON_DAYS = 7;

  public static UpkeepItem of(RecurringTask task, LocalDate today) {
    LocalDate due = task.nextDue(today);
    long overdue = task.daysOverdue(today);
    State state =
        overdue >= 0 ? State.OVERDUE : (overdue >= -DUE_SOON_DAYS ? State.DUE_SOON : State.LATER);
    return new UpkeepItem(task, due, overdue, state);
  }
}
