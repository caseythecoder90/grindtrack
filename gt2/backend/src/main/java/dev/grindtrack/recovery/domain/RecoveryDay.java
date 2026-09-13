package dev.grindtrack.recovery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * A day's marks. Keyed by the date, created the first time something is marked on it. The read
 * range is kept so the part that was read stays on screen after the cursor has moved past it.
 */
@Entity
@Table(name = "recovery_days")
public class RecoveryDay {

  @Id private LocalDate day;

  @Column(name = "read_done", nullable = false)
  private boolean readDone;

  @Column(name = "read_from")
  private Integer readFrom;

  @Column(name = "read_to")
  private Integer readTo;

  @Column(nullable = false)
  private boolean meditated;

  protected RecoveryDay() {}

  public RecoveryDay(LocalDate day) {
    this.day = day;
  }

  public LocalDate getDay() {
    return day;
  }

  public boolean isReadDone() {
    return readDone;
  }

  public Integer getReadFrom() {
    return readFrom;
  }

  public Integer getReadTo() {
    return readTo;
  }

  public boolean isMeditated() {
    return meditated;
  }

  public void markRead(int from, int to) {
    this.readDone = true;
    this.readFrom = from;
    this.readTo = to;
  }

  public void markMeditated() {
    this.meditated = true;
  }
}
