package dev.grindtrack.tracking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * One completed (or abandoned-early) pomodoro focus session. Recording a session also adds its
 * duration to that day's hours — to {@link DailyLog} for a {@code study} session, or to the work
 * log for a {@code work} session — see FocusService.
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

  /** "study" (folds into daily_logs) or "work" (folds into work_logs). */
  @Column(nullable = false)
  private String kind = "study";

  @Column(name = "created_at", insertable = false, updatable = false)
  private OffsetDateTime createdAt;

  protected FocusSession() {}

  public FocusSession(
      LocalDate sessionDate,
      OffsetDateTime startedAt,
      int durationMinutes,
      boolean completed,
      String kind) {
    this.sessionDate = sessionDate;
    this.startedAt = startedAt;
    this.durationMinutes = durationMinutes;
    this.completed = completed;
    this.kind = kind == null ? "study" : kind;
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

  public String getKind() {
    return kind;
  }
}
