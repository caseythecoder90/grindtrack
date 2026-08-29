package dev.grindtrack.relationship.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Year;

/**
 * An anniversary or a birthday — the only part of this feature with an actual deadline.
 *
 * <p>The full original date is kept rather than just the month and day, so "our 4th anniversary"
 * can be worked out instead of typed in again every year.
 */
@Entity
@Table(name = "relationship_occasions")
public class Occasion {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String label;

  @Column(name = "occasion_date", nullable = false)
  private LocalDate occasionDate;

  @Column(nullable = false)
  private boolean recurring = true;

  /**
   * How far ahead this starts showing up.
   *
   * <p>A birthday needs more warning than a monthly dinner, and an idea that surfaces the day
   * before is not much use to anyone.
   */
  @Column(name = "lead_days", nullable = false)
  private int leadDays = 21;

  @Column(nullable = false)
  private String note = "";

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt = OffsetDateTime.now();

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt = OffsetDateTime.now();

  protected Occasion() {}

  public Occasion(String label, LocalDate occasionDate) {
    this.label = label.trim();
    this.occasionDate = occasionDate;
  }

  public void update(
      String label, LocalDate occasionDate, boolean recurring, int leadDays, String note) {
    this.label = label.trim();
    this.occasionDate = occasionDate;
    this.recurring = recurring;
    this.leadDays = Math.max(0, leadDays);
    this.note = note == null ? "" : note.trim();
    this.updatedAt = OffsetDateTime.now();
  }

  /**
   * The next time this comes round, on or after {@code from}.
   *
   * <p>February 29th rolls to the 28th in a common year rather than being skipped, which is the
   * behaviour anyone actually wants from an anniversary.
   */
  public LocalDate nextOccurrence(LocalDate from) {
    if (!recurring) {
      return occasionDate;
    }
    LocalDate candidate = onYear(from.getYear());
    return candidate.isBefore(from) ? onYear(from.getYear() + 1) : candidate;
  }

  private LocalDate onYear(int year) {
    int day = Math.min(occasionDate.getDayOfMonth(), occasionDate.getMonth().length(isLeap(year)));
    return LocalDate.of(year, occasionDate.getMonth(), day);
  }

  private static boolean isLeap(int year) {
    return Year.isLeap(year);
  }

  /** How many years old this occasion will be at its next occurrence; null when not recurring. */
  public Integer yearsAt(LocalDate next) {
    return recurring ? next.getYear() - occasionDate.getYear() : null;
  }

  public Long getId() {
    return id;
  }

  public String getLabel() {
    return label;
  }

  public LocalDate getOccasionDate() {
    return occasionDate;
  }

  public boolean isRecurring() {
    return recurring;
  }

  public int getLeadDays() {
    return leadDays;
  }

  public String getNote() {
    return note;
  }
}
