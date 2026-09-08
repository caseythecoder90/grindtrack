package dev.grindtrack.calendar.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

/** What a calendar entry is, which decides how it is coloured and what it may link to. */
public enum EventKind {
  /** A fixed commitment with someone else in it: an exam, a doctor, a call. */
  APPOINTMENT,
  /** A booked morning or weekend block, usually against a plan item. */
  STUDY_BLOCK,
  /** The day job, blocked out so the week reads honestly. */
  WORK_BLOCK,
  /** Everything else that is yours alone. */
  PERSONAL;

  /**
   * Whether a block of this kind can carry a plan item.
   *
   * <p>A dentist appointment pointing at "CKA course + labs" is not a thing, and letting it happen
   * would put hours against a plan item that no study ever went into — the same class of mistake as
   * a reading subject leaking into a study session.
   */
  public boolean canTrackPlanItem() {
    return this == STUDY_BLOCK;
  }

  /** The over-the-wire and in-database spelling. */
  public String wireValue() {
    return name().toLowerCase(Locale.ROOT);
  }

  /**
   * @throws IllegalArgumentException on an unknown kind, so a bad value is a 400 rather than a
   *     silent default
   */
  public static EventKind of(String value) {
    return valueOf(value.trim().toUpperCase(Locale.ROOT));
  }

  @Converter(autoApply = true)
  public static class JpaConverter implements AttributeConverter<EventKind, String> {

    @Override
    public String convertToDatabaseColumn(EventKind kind) {
      return kind == null ? null : kind.wireValue();
    }

    @Override
    public EventKind convertToEntityAttribute(String column) {
      return column == null ? null : of(column);
    }
  }
}
