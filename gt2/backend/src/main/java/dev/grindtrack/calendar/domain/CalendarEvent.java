package dev.grindtrack.calendar.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

/**
 * One thing on a day.
 *
 * <p>A date plus an optional time, not an instant: "09:00 on Sep 12" means wall clock on a personal
 * calendar, and it must not move when a server's zone does.
 */
@Entity
@Table(name = "calendar_events")
public class CalendarEvent {

  /** A day ahead. Further than that and the reminder is a todo, not a nudge. */
  public static final int MAX_REMIND_MINUTES = 1440;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false)
  private EventKind kind;

  @Column(name = "event_date", nullable = false)
  private LocalDate eventDate;

  /** Null is what "all day" means. There is no separate flag that could disagree with it. */
  @Column(name = "start_time")
  private LocalTime startTime;

  @Column(name = "end_time")
  private LocalTime endTime;

  /** The plan item this block is against, when it is a study block. */
  @Column(name = "plan_item_id")
  private Long planItemId;

  @Column(nullable = false)
  private String notes = "";

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt = OffsetDateTime.now();

  /** When the phone was told this block was about to start. Null: not yet, or never timed. */
  @Column(name = "reminded_at")
  private OffsetDateTime remindedAt;

  /**
   * Minutes ahead of the start the phone is told, for this block. Null: the configured default for
   * a kind that is reminded, none for one that is not; zero: not this one; at most a day.
   */
  @Column(name = "remind_minutes")
  private Integer remindMinutes;

  protected CalendarEvent() {}

  public CalendarEvent(
      String title, EventKind kind, LocalDate eventDate, LocalTime startTime, LocalTime endTime) {
    this.title = title;
    this.kind = kind;
    this.eventDate = eventDate;
    setTimes(startTime, endTime);
  }

  /**
   * The two invariants the schema also enforces, checked here so a service never has to reach for
   * the database to find out it built something impossible: an end needs a start, and it has to
   * come after one.
   */
  public void setTimes(LocalTime start, LocalTime end) {
    if (end != null && start == null) {
      throw new IllegalArgumentException("an event with an end time needs a start time");
    }
    if (end != null && !end.isAfter(start)) {
      throw new IllegalArgumentException("end time must be after the start time");
    }
    this.startTime = start;
    this.endTime = end;
    // A block moved to another time is reminded again at the new one.
    this.remindedAt = null;
    touch();
  }

  /**
   * Attaches this block to a plan item, or detaches it with null.
   *
   * <p>Dropped rather than rejected when the kind cannot carry one: the same rule a focus session
   * follows for its reading subject, and for the same reason — a stale link arriving from an older
   * client is not a mistake the user can act on, but silently filing hours against the wrong plan
   * item is one they cannot see at all.
   */
  public void setPlanItem(Long planItemId) {
    this.planItemId = kind.canTrackPlanItem() ? planItemId : null;
    touch();
  }

  public void setTitle(String title) {
    this.title = title;
    touch();
  }

  public void setNotes(String notes) {
    this.notes = notes == null ? "" : notes;
    touch();
  }

  public void setEventDate(LocalDate date) {
    this.eventDate = date;
    this.remindedAt = null;
    touch();
  }

  /** Changing the kind re-applies the plan-item rule, which may drop an existing link. */
  public void setKind(EventKind kind) {
    this.kind = kind;
    setPlanItem(this.planItemId);
  }

  /**
   * How far ahead this block wants its reminder, or null to go back to the default. A change
   * forgets that it was reminded, so a block whose lead grew from ten minutes to an hour at 5:15 is
   * announced at 5:15 rather than never.
   */
  public void setRemindMinutes(Integer minutes) {
    if (minutes != null && (minutes < 0 || minutes > MAX_REMIND_MINUTES)) {
      throw new IllegalArgumentException("a reminder is between 0 and 1440 minutes ahead");
    }
    this.remindMinutes = minutes;
    this.remindedAt = null;
    touch();
  }

  public Integer getRemindMinutes() {
    return remindMinutes;
  }

  /**
   * The lead the reminder scheduler uses: the block's own when set, else the configured default for
   * a kind that is reminded, else nothing. Zero means no reminder.
   */
  public int reminderLead(int defaultMinutes) {
    if (remindMinutes != null) {
      return remindMinutes;
    }
    return kind.remindsBeforeStart() ? defaultMinutes : 0;
  }

  public void markReminded() {
    this.remindedAt = OffsetDateTime.now();
  }

  public OffsetDateTime getRemindedAt() {
    return remindedAt;
  }

  /** True when there is no time of day — the row sorts to the top of its day. */
  public boolean isAllDay() {
    return startTime == null;
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

  public EventKind getKind() {
    return kind;
  }

  public LocalDate getEventDate() {
    return eventDate;
  }

  public LocalTime getStartTime() {
    return startTime;
  }

  public LocalTime getEndTime() {
    return endTime;
  }

  public Long getPlanItemId() {
    return planItemId;
  }

  public String getNotes() {
    return notes;
  }
}
