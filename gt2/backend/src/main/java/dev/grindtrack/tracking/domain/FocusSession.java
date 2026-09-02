package dev.grindtrack.tracking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Locale;

/**
 * One completed (or abandoned-early) pomodoro focus session. Recording a session also adds its
 * duration to that day's hours — to {@link DailyLog} for a {@link FocusKind#STUDY} session, or to
 * the work log for a {@link FocusKind#WORK} one — see FocusService.
 */
@Entity
@Table(name = "focus_sessions")
public class FocusSession {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "session_date", nullable = false)
  private LocalDate sessionDate;

  @Column(name = "started_at", nullable = false)
  private OffsetDateTime startedAt;

  @Column(name = "duration_minutes", nullable = false)
  private int durationMinutes;

  /** True if the full planned session ran; false if ended early (partial time still logged). */
  @Column(nullable = false)
  private boolean completed;

  /** Work folds into {@code work_logs}; every other kind into {@code daily_logs}. */
  @Column(nullable = false)
  private FocusKind kind = FocusKind.STUDY;

  /** The plan item this went into, when there is one. Null for a code review or a stray article. */
  @Column(name = "plan_item_id")
  private Long planItemId;

  /**
   * What this session was spent on, in words: a book's title, or a repo and area for a review.
   *
   * <p>Snapshotted rather than derived from {@link #planItemId}, so a rollup still reads correctly
   * after the workbook renames or drops the item — and so a code-review session, which has no plan
   * item at all, still has a subject to group by.
   */
  @Column(nullable = false)
  private String topic = "";

  /** The three sentences written afterwards. The step that turns reading into retention. */
  @Column(nullable = false)
  private String takeaway = "";

  @Column(name = "created_at", insertable = false, updatable = false)
  private OffsetDateTime createdAt;

  protected FocusSession() {}

  public FocusSession(
      LocalDate sessionDate,
      OffsetDateTime startedAt,
      int durationMinutes,
      boolean completed,
      FocusKind kind) {
    this.sessionDate = sessionDate;
    this.startedAt = startedAt;
    this.durationMinutes = durationMinutes;
    this.completed = completed;
    this.kind = kind == null ? FocusKind.STUDY : kind;
  }

  /** What this session went into. Either part may be absent; both are for a bare study block. */
  public void setSubject(Long planItemId, String topic) {
    this.planItemId = planItemId;
    this.topic = topic == null ? "" : topic.trim();
  }

  /**
   * Written after the fact, which is the only time it can be written — you do not know the takeaway
   * when you start the timer. Blank clears it rather than being rejected.
   */
  public void setTakeaway(String takeaway) {
    this.takeaway = takeaway == null ? "" : takeaway.trim();
  }

  /** The label a rollup groups by: the item when there is one, otherwise the typed topic. */
  public String subjectKey() {
    return planItemId != null ? "item:" + planItemId : "topic:" + topic.toLowerCase(Locale.ROOT);
  }

  public Long getId() {
    return id;
  }

  public LocalDate getSessionDate() {
    return sessionDate;
  }

  public OffsetDateTime getStartedAt() {
    return startedAt;
  }

  public int getDurationMinutes() {
    return durationMinutes;
  }

  public boolean isCompleted() {
    return completed;
  }

  public FocusKind getKind() {
    return kind;
  }

  public Long getPlanItemId() {
    return planItemId;
  }

  public String getTopic() {
    return topic;
  }

  public String getTakeaway() {
    return takeaway;
  }
}
