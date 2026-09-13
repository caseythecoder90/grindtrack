package dev.grindtrack.config;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The {@code grindtrack.recovery.*} block.
 *
 * <p>One value for now: the sobriety date, which the assistant's context carries as a day count so
 * the morning's line can be grounded in it. It comes from the environment rather than this file
 * because the repository is public and the date is personal. Absent means the context simply has no
 * recovery section — nothing else in the app changes.
 *
 * <p>The recovery page, when it exists, will own this in the database; this is the smallest thing
 * that lets the brief know the number today.
 *
 * @param sobrietyDate ISO date from {@code SOBRIETY_DATE}, or blank
 */
@ConfigurationProperties(prefix = "grindtrack.recovery")
public record RecoveryProperties(String sobrietyDate) {

  public Optional<LocalDate> sobrietyDay() {
    if (sobrietyDate == null || sobrietyDate.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(LocalDate.parse(sobrietyDate.trim()));
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException("SOBRIETY_DATE must be YYYY-MM-DD, got: " + sobrietyDate);
    }
  }
}
