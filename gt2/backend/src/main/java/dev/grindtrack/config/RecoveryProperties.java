package dev.grindtrack.config;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The {@code grindtrack.recovery.*} block.
 *
 * <p>The sobriety date comes from the environment rather than this file because the repository is
 * public and the date is personal. Absent means the page has no number and the assistant's context
 * has no recovery section — nothing else changes.
 *
 * @param sobrietyDate ISO date from {@code SOBRIETY_DATE}, or blank
 * @param readingsCron when the day's readings are pushed to the phone, for reading together later.
 *     Spring cron, in the assistant's zone.
 * @param bible the built-in Bible: which resource file, what to call it, and the order the books
 *     are read in
 */
@ConfigurationProperties(prefix = "grindtrack.recovery")
public record RecoveryProperties(String sobrietyDate, String readingsCron, Bible bible) {

  /**
   * @param file classpath resource, gzipped JSON lines as written by {@code
   *     tools/recovery/usfx_to_verses.py}
   * @param name the translation's full name, shown once on the your-books screen
   * @param abbrev the short name printed after every reference
   * @param books USFM codes in reading order; books left out are appended in canonical order
   */
  public record Bible(String file, String name, String abbrev, List<String> books) {}

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
