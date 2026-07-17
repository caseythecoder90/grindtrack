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
 * duration to that day's {@link DailyLog} hours — see FocusService.
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

  @Column(name = "created_at", insertable = false, updatable = false)
  private OffsetDateTime createdAt;

  protected FocusSession() {}

  public FocusSession(
      LocalDate sessionDate, OffsetDateTime startedAt, int durationMinutes, boolean completed) {
    this.sessionDate = sessionDate;
    this.startedAt = startedAt;
    this.durationMinutes = durationMinutes;
    this.completed = completed;
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
}
