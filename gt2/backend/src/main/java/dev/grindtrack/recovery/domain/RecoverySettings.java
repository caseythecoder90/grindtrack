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

  @Column(name = "pages_per_day", nullable = false)
  private int pagesPerDay = 2;

  /** The last day the reading was done; the carry-over is counted from here. Null: never. */
  @Column(name = "read_last_done")
  private LocalDate readLastDone;

  /** Where the reader was last opened, a paragraph seq, so either device picks up there. */
  @Column(name = "reading_place")
  private Integer readingPlace;

  @Column(name = "read_throughs", nullable = false)
  private int readThroughs;

  @Column(name = "meditation_minutes", nullable = false)
  private int meditationMinutes = 10;

  @Column(name = "bible_plan_start", nullable = false)
  private LocalDate biblePlanStart = LocalDate.now();

  /** Which passage of the plan is today's. Null: not yet set, so it is computed from the start. */
  @Column(name = "bible_cursor")
  private Integer bibleCursor;

  /** The day the cursor was last set, so a new day moves it on once. */
  @Column(name = "bible_shown_on")
  private LocalDate bibleShownOn;

  public RecoverySettings() {}

  public Short getId() {
    return id;
  }

  public int getReadCursor() {
    return readCursor;
  }

  public int getPagesPerDay() {
    return pagesPerDay;
  }

  public LocalDate getReadLastDone() {
    return readLastDone;
  }

  public Integer getReadingPlace() {
    return readingPlace;
  }

  public void setReadingPlace(Integer seq) {
    this.readingPlace = seq;
  }

  /** How many pages are owed today: the daily count for every day since the last one done. */
  public int pagesDue(LocalDate today, LocalDate fallbackStart) {
    LocalDate since = readLastDone == null ? fallbackStart : readLastDone;
    long days = java.time.temporal.ChronoUnit.DAYS.between(since, today);
    return (int) Math.max(0, Math.min(days, 365)) * pagesPerDay;
  }

  /** Forgives the backlog: tomorrow owes the daily count again. */
  public void catchUp(LocalDate today) {
    this.readLastDone = today.minusDays(1);
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
  public void advanceReading(int nextSeq, int paragraphCount, LocalDate today) {
    if (nextSeq >= paragraphCount) {
      readCursor = 0;
      readThroughs++;
    } else {
      readCursor = nextSeq;
    }
    readLastDone = today;
  }

  public void restartReading() {
    readCursor = 0;
    readLastDone = null;
    readingPlace = null;
  }

  public void setPagesPerDay(int pages) {
    this.pagesPerDay = pages;
  }

  public void setMeditationMinutes(int minutes) {
    this.meditationMinutes = minutes;
  }

  /**
   * Today's passage: the cursor, moved on once when the day has changed since it was set. The first
   * time, before there is a cursor, it is {@code initial} — the passage the old date-counted plan
   * would have shown — so nobody's place jumps.
   */
  public int biblePassage(LocalDate today, int initial, int planSize) {
    if (planSize <= 0) {
      return 0;
    }
    if (bibleCursor == null) {
      bibleCursor = Math.floorMod(initial, planSize);
      bibleShownOn = today;
    } else if (bibleShownOn == null || bibleShownOn.isBefore(today)) {
      bibleCursor = (bibleCursor + 1) % planSize;
      bibleShownOn = today;
    }
    return bibleCursor % planSize;
  }

  /** Another passage today: the cursor moves on now, and tomorrow moves on from there. */
  public int nextBiblePassage(LocalDate today, int initial, int planSize) {
    int current = biblePassage(today, initial, planSize);
    bibleCursor = (current + 1) % planSize;
    bibleShownOn = today;
    return bibleCursor;
  }

  public void restartBiblePlan(LocalDate today) {
    this.biblePlanStart = today;
    this.bibleCursor = 0;
    this.bibleShownOn = today;
  }
}
