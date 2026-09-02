package dev.grindtrack.tracking.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

/**
 * What a focus session was: the 6-8am study block, the day job, or one of the two lunch kinds.
 *
 * <p>This was the string {@code "study"} or {@code "work"}, compared by hand in eight places across
 * the controller, the service and the entity. Every one of those comparisons treated an unknown
 * value as "study", so a typo in the request body silently logged work hours as study time instead
 * of failing — the one outcome the split between the two logs exists to prevent.
 *
 * <p>The stored and transmitted form stays lower-case so that neither the existing rows nor the
 * frontend's {@code FocusKind} union have to change; {@link Converter} handles the mapping.
 */
public enum FocusKind {
  /** The main block — 6-8am on a weekday, cert prep and course work. */
  STUDY,
  /** The day job. The only kind whose minutes land somewhere other than the personal daily log. */
  WORK,
  /** Books, papers and RFCs. The lunch slot. */
  READING,
  /** Reading your own code rather than someone else's prose. Also the lunch slot. */
  REVIEW;

  /** True for the one kind that folds into {@code work_logs} rather than {@code daily_logs}. */
  public boolean isDayJob() {
    return this == WORK;
  }

  /**
   * The lunch kinds, counted as their own streak.
   *
   * <p>Deliberately not "everything that isn't work": a long evening of cert prep must not be able
   * to satisfy a lunch streak, or the streak stops measuring the habit it was added to protect.
   */
  public boolean isLunch() {
    return this == READING || this == REVIEW;
  }

  /** The over-the-wire and in-database spelling. */
  public String wireValue() {
    return name().toLowerCase(Locale.ROOT);
  }

  /**
   * @throws IllegalArgumentException on anything that is not a known kind — deliberately, so a bad
   *     value is a 400 rather than a silent default
   */
  public static FocusKind of(String value) {
    return valueOf(value.trim().toUpperCase(Locale.ROOT));
  }

  @Converter(autoApply = true)
  public static class JpaConverter implements AttributeConverter<FocusKind, String> {

    @Override
    public String convertToDatabaseColumn(FocusKind kind) {
      return kind == null ? null : kind.wireValue();
    }

    @Override
    public FocusKind convertToEntityAttribute(String column) {
      return column == null ? null : of(column);
    }
  }
}
