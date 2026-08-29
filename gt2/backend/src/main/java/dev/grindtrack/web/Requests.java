package dev.grindtrack.web;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Set;

/**
 * Turning request strings into typed values, in one place.
 *
 * <p>Three controllers had their own copy of {@code optionalDate} and four had their own enum
 * parsing, each with a slightly different error message for the same mistake. A user who sends a
 * bad date should get the same answer whichever endpoint they sent it to.
 *
 * <p>Everything here throws {@link BadRequestException} on failure, so a controller can parse
 * without an {@code if} after every call.
 */
public final class Requests {

  private Requests() {}

  /**
   * @throws BadRequestException with {@code message} when the value is absent or unparseable
   */
  public static LocalDate requireDate(String value, String message) {
    LocalDate parsed = optionalDate(value, message);
    if (parsed == null) {
      throw new BadRequestException(message);
    }
    return parsed;
  }

  /** Blank means absent, which is different from invalid: absent is allowed, invalid is a 400. */
  public static LocalDate optionalDate(String value, String message) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(value.trim());
    } catch (DateTimeParseException e) {
      throw new BadRequestException(message);
    }
  }

  public static LocalDate optionalDate(String value) {
    return optionalDate(value, "dates must be YYYY-MM-DD");
  }

  public static OffsetDateTime optionalInstant(String value, String message) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return OffsetDateTime.parse(value.trim());
    } catch (DateTimeParseException e) {
      throw new BadRequestException(message);
    }
  }

  /**
   * A {@code yyyy-MM} month, falling back to the current one when absent.
   *
   * <p>Absent means "the month I am looking at now" on every budget endpoint, so the fallback is
   * here rather than repeated at each call site — a caller that omits {@code month} and a caller
   * that sends this month must not be able to diverge.
   */
  public static YearMonth monthOrNow(String value) {
    if (value == null || value.isBlank()) {
      return YearMonth.now();
    }
    try {
      return YearMonth.parse(value.trim());
    } catch (DateTimeParseException e) {
      throw new BadRequestException("month must be yyyy-MM, for example 2026-08");
    }
  }

  /**
   * A trimmed, non-blank string within {@code max} characters.
   *
   * @param requirement completes the sentence "a ...", so pass a phrase like {@code "todo needs a
   *     title"} to produce "a todo needs a title (max 300 chars)". Naming the field beats a generic
   *     "invalid input", because the caller usually cannot see which one you meant.
   */
  public static String requireText(String value, String requirement, int max) {
    String text = value == null ? "" : value.trim();
    if (text.isBlank() || text.length() > max) {
      throw new BadRequestException("a " + requirement + " (max " + max + " chars)");
    }
    return text;
  }

  /**
   * Membership of a fixed set of strings.
   *
   * <p>For the handful of fields that are a closed vocabulary in the database but were never made
   * into a Java enum -- a todo's kind, a skill's status. New code should prefer a real enum and
   * {@link #enumValue}; this exists so those two do not need a schema change to be validated in one
   * place.
   */
  public static String requireOneOf(String value, Set<String> allowed, String message) {
    if (value == null || !allowed.contains(value)) {
      throw new BadRequestException(message);
    }
    return value;
  }

  /** Rejects only over-length; blank is allowed and comes back as null. */
  public static String optionalText(String value, String field, int max) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String text = value.trim();
    if (text.length() > max) {
      throw new BadRequestException(field + " is limited to " + max + " characters");
    }
    return text;
  }

  /** Guards a batch of free-text fields against a shared column limit. */
  public static void requireWithin(int max, String message, String... values) {
    for (String value : values) {
      if (value != null && value.length() > max) {
        throw new BadRequestException(message);
      }
    }
  }

  /**
   * Case-insensitive enum lookup.
   *
   * <p>The message lists the valid constants rather than saying "invalid value", because the caller
   * usually cannot see the enum and guessing is a poor use of their afternoon.
   */
  public static <E extends Enum<E>> E enumValue(Class<E> type, String value, String field) {
    if (value == null || value.isBlank()) {
      throw new BadRequestException(field + " must be one of " + options(type));
    }
    try {
      return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new BadRequestException(field + " must be one of " + options(type));
    }
  }

  /** Same, but a blank value yields {@code fallback} instead of a 400. */
  public static <E extends Enum<E>> E enumValue(
      Class<E> type, String value, String field, E fallback) {
    return value == null || value.isBlank() ? fallback : enumValue(type, value, field);
  }

  /** Same, but a blank value yields null — for genuinely optional enum filters. */
  public static <E extends Enum<E>> E optionalEnum(Class<E> type, String value, String field) {
    return value == null || value.isBlank() ? null : enumValue(type, value, field);
  }

  private static String options(Class<? extends Enum<?>> type) {
    StringBuilder out = new StringBuilder();
    for (Enum<?> constant : type.getEnumConstants()) {
      if (out.length() > 0) {
        out.append(", ");
      }
      out.append(constant.name());
    }
    return out.toString();
  }
}
