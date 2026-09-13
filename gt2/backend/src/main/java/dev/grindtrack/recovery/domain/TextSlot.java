package dev.grindtrack.recovery.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

/**
 * The three places an imported book can go. One book per slot; importing again replaces it.
 *
 * <p>The stored and transmitted spelling is lower-case with an underscore, matching the check
 * constraint in the migration and the path segment in the API.
 */
public enum TextSlot {
  /** Read in order, a few minutes a day, then again from the start. */
  BIG_BOOK("Big Book"),
  /** One entry per calendar day, shown on the today card. */
  REFLECTION("Daily Reflections"),
  /** One entry per calendar day, shown under the timer. Optional. */
  MEDITATION("Meditation book");

  private final String defaultTitle;

  TextSlot(String defaultTitle) {
    this.defaultTitle = defaultTitle;
  }

  /** What the slot is called until an import names the book. */
  public String defaultTitle() {
    return defaultTitle;
  }

  /** True for the slots read by date rather than in order. */
  public boolean isDaily() {
    return this != BIG_BOOK;
  }

  public String wireValue() {
    return name().toLowerCase(Locale.ROOT);
  }

  /**
   * @throws IllegalArgumentException on anything that is not a slot, so a bad path segment is a 400
   *     rather than a silent default
   */
  public static TextSlot of(String value) {
    return valueOf(value.trim().toUpperCase(Locale.ROOT));
  }

  @Converter(autoApply = true)
  public static class JpaConverter implements AttributeConverter<TextSlot, String> {

    @Override
    public String convertToDatabaseColumn(TextSlot slot) {
      return slot == null ? null : slot.wireValue();
    }

    @Override
    public TextSlot convertToEntityAttribute(String column) {
      return column == null ? null : of(column);
    }
  }
}
