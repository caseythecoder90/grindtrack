package dev.grindtrack.calendar.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

/** What part of life an upkeep task belongs to. Filtering only — nothing branches on it. */
public enum TaskCategory {
  PET,
  HOME,
  HEALTH,
  CAR,
  OTHER;

  public String wireValue() {
    return name().toLowerCase(Locale.ROOT);
  }

  public static TaskCategory of(String value) {
    return valueOf(value.trim().toUpperCase(Locale.ROOT));
  }

  @Converter(autoApply = true)
  public static class JpaConverter implements AttributeConverter<TaskCategory, String> {

    @Override
    public String convertToDatabaseColumn(TaskCategory category) {
      return category == null ? null : category.wireValue();
    }

    @Override
    public TaskCategory convertToEntityAttribute(String column) {
      return column == null ? null : of(column);
    }
  }
}
