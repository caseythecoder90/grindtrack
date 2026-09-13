package dev.grindtrack.recovery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * The one row of settings and cursors: where the reading is, how many minutes a day, how many times
 * through, the last meditation length, and the day the Bible plan started. Created on first read
 * with the defaults.
 */
@Entity
@Table(name = "recovery_settings")
public class RecoverySettings {

  public static final short THE_ROW = 1;

  @Id private Short id = THE_ROW;

  @Column(name = "read_cursor", nullable = false)
  private int readCursor;

  @Column(name = "read_minutes", nullable = false)
  private int readMinutes = 5;

  @Column(name = "read_throughs", nullable = false)
  private int readThroughs;

  @Column(name = "meditation_minutes", nullable = false)
  private int meditationMinutes = 10;

  @Column(name = "bible_plan_start", nullable = false)
  private LocalDate biblePlanStart = LocalDate.now();

  public RecoverySettings() {}

  public Short getId() {
    return id;
  }

  public int getReadCursor() {
    return readCursor;
  }

  public int getReadMinutes() {
    return readMinutes;
  }

  public int getReadThroughs() {
    return readThroughs;
  }

  public int getMeditationMinutes() {
    return meditationMinutes;
  }

  public LocalDate getBiblePlanStart() {
    return biblePlanStart;
  }

  /** The cursor moves past today's part; past the end it wraps and counts a read-through. */
  public void advanceReading(int nextSeq, int paragraphCount) {
    if (nextSeq >= paragraphCount) {
      readCursor = 0;
      readThroughs++;
    } else {
      readCursor = nextSeq;
    }
  }

  public void restartReading() {
    readCursor = 0;
  }

  public void setReadMinutes(int minutes) {
    this.readMinutes = minutes;
  }

  public void setMeditationMinutes(int minutes) {
    this.meditationMinutes = minutes;
  }

  public void restartBiblePlan(LocalDate today) {
    this.biblePlanStart = today;
  }
}
