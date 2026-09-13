package dev.grindtrack.recovery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** One sitting: when it started, how long it was set for, whether the bell was reached. */
@Entity
@Table(name = "recovery_sessions")
public class MeditationSession {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "started_at", nullable = false)
  private OffsetDateTime startedAt;

  @Column(nullable = false)
  private int minutes;

  @Column(nullable = false)
  private boolean completed;

  protected MeditationSession() {}

  public MeditationSession(OffsetDateTime startedAt, int minutes, boolean completed) {
    this.startedAt = startedAt;
    this.minutes = minutes;
    this.completed = completed;
  }

  public Long getId() {
    return id;
  }

  public OffsetDateTime getStartedAt() {
    return startedAt;
  }

  public int getMinutes() {
    return minutes;
  }

  public boolean isCompleted() {
    return completed;
  }
}
