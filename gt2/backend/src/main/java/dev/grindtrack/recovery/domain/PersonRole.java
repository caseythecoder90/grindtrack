package dev.grindtrack.recovery.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

/** What someone is to the person in recovery. */
public enum PersonRole {
  SPONSOR,
  /** Someone still to be asked. Sits at the top of the list until the asking is done. */
  PROSPECT,
  FRIEND;

  public String wireValue() {
    return name().toLowerCase(Locale.ROOT);
  }

  public static PersonRole of(String value) {
    return valueOf(value.trim().toUpperCase(Locale.ROOT));
  }

  @Converter(autoApply = true)
  public static class JpaConverter implements AttributeConverter<PersonRole, String> {

    @Override
    public String convertToDatabaseColumn(PersonRole role) {
      return role == null ? null : role.wireValue();
    }

    @Override
    public PersonRole convertToEntityAttribute(String column) {
      return column == null ? null : of(column);
    }
  }
}
