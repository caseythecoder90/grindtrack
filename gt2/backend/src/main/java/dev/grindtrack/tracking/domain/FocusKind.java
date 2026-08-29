package dev.grindtrack.tracking.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

/**
 * Which day a focus session's minutes are added to: the personal study log or the day-job work log.
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
  STUDY,
  WORK;

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
