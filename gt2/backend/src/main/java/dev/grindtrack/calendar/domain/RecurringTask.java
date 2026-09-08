package dev.grindtrack.calendar.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Something that comes round again: the dog's flea and tick, the HVAC filter, an oil change.
 *
 * <p>The question this answers is "when did I last", and the one it is asked is "what is overdue".
 * Both are the same fact — {@code lastDoneOn} — read two ways, which is why the next due date is
 * computed here and never stored. A stored copy is one write away from disagreeing with the field
 * it was derived from, and a reminder that is wrong once is not trusted again.
 */
@Entity
@Table(name = "recurring_tasks")
public class RecurringTask {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false)
  private TaskCategory category;

  @Column(name = "interval_days", nullable = false)
  private int intervalDays;

  /** Null means never done, which reads as due now — see {@link #nextDue()}. */
  @Column(name = "last_done_on")
  private LocalDate lastDoneOn;

  @Column(nullable = false)
  private boolean active = true;

  @Column(nullable = false)
  private String notes = "";

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt = OffsetDateTime.now();

  protected RecurringTask() {}

  public RecurringTask(
      String title, TaskCategory category, int intervalDays, LocalDate lastDoneOn) {
    this.title = title;
    this.category = category;
    this.intervalDays = intervalDays;
    this.lastDoneOn = lastDoneOn;
  }

  /**
   * When this is next due.
   *
   * <p>A task that has never been done is due today rather than at some epoch date. That keeps it
   * at the top of the list where a newly added task belongs, without letting it report a made-up
   * overdue count of several thousand days.
   */
  public LocalDate nextDue(LocalDate today) {
    return lastDoneOn == null ? today : lastDoneOn.plusDays(intervalDays);
  }

  /** Negative before it is due, zero on the day, positive once it is late. */
  public long daysOverdue(LocalDate today) {
    return ChronoUnit.DAYS.between(nextDue(today), today);
  }

  /**
   * Records a completion and moves the clock forward.
   *
   * <p>From the date it was actually done, not from the date it was due — otherwise a filter
   * changed three weeks late would claim its next change is due on the original schedule, and the
   * drift compounds every cycle.
   */
  public void markDone(LocalDate on) {
    if (lastDoneOn != null && on.isBefore(lastDoneOn)) {
      throw new IllegalArgumentException("that is earlier than the last time this was done");
    }
    this.lastDoneOn = on;
    touch();
  }

  public void update(String title, TaskCategory category, Integer intervalDays, String notes) {
    if (title != null) {
      this.title = title;
    }
    if (category != null) {
      this.category = category;
    }
    if (intervalDays != null) {
      this.intervalDays = intervalDays;
    }
    if (notes != null) {
      this.notes = notes;
    }
    touch();
  }

  public void setActive(boolean active) {
    this.active = active;
    touch();
  }

  /** Corrects the last-done date without logging a completion — for fixing a typo. */
  public void correctLastDone(LocalDate on) {
    this.lastDoneOn = on;
    touch();
  }

  private void touch() {
    this.updatedAt = OffsetDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public TaskCategory getCategory() {
    return category;
  }

  public int getIntervalDays() {
    return intervalDays;
  }

  public LocalDate getLastDoneOn() {
    return lastDoneOn;
  }

  public boolean isActive() {
    return active;
  }

  public String getNotes() {
    return notes;
  }
}
