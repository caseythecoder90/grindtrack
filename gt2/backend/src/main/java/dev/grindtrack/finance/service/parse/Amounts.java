package dev.grindtrack.finance.service.parse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Money and date parsing shared by every statement parser, because the five institutions manage to
 * disagree about both.
 *
 * <p>Dates arrive in three shapes: {@code 2026-08-04} (Capital One credit), {@code 08/24/26}
 * (Capital One deposit — two-digit year), and {@code 08/23/2026} (Chase, Bank of America, Wells
 * Fargo).
 *
 * <p>Amounts arrive as bare decimals, as {@code -$27.89}, and as {@code "$2,980.76"} with the
 * thousands separator inside quotes.
 */
public final class Amounts {

  private Amounts() {}

  private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private static final DateTimeFormatter US_LONG = DateTimeFormatter.ofPattern("M/d/yyyy");
  private static final DateTimeFormatter US_SHORT = DateTimeFormatter.ofPattern("M/d/yy");

  /**
   * @return the parsed date, or null when the field is blank or unrecognized
   */
  public static LocalDate date(String raw) {
    if (raw == null) {
      return null;
    }
    String s = raw.trim();
    if (s.isEmpty()) {
      return null;
    }
    DateTimeFormatter fmt = s.contains("-") ? ISO : (s.length() <= 8 ? US_SHORT : US_LONG);
    try {
      return LocalDate.parse(s, fmt);
    } catch (DateTimeParseException e) {
      // Two-digit and four-digit years are not always the length they look like ("1/5/2026" is 8
      // characters but has a four-digit year), so fall back rather than rejecting the row.
      for (DateTimeFormatter alt : new DateTimeFormatter[] {US_LONG, US_SHORT, ISO}) {
        try {
          return LocalDate.parse(s, alt);
        } catch (DateTimeParseException ignored) {
          // try the next shape
        }
      }
      return null;
    }
  }

  /**
   * @return the parsed amount, or null when the field is blank or unparseable. Blank is meaningful
   *     rather than exceptional: Capital One's credit exports leave whichever of Debit/Credit does
   *     not apply completely empty.
   */
  public static BigDecimal money(String raw) {
    if (raw == null) {
      return null;
    }
    String s = raw.replace("$", "").replace(",", "").replace("\"", "").trim();
    if (s.isEmpty()) {
      return null;
    }
    boolean negative = s.startsWith("-") || (s.startsWith("(") && s.endsWith(")"));
    s = s.replace("-", "").replace("(", "").replace(")", "").trim();
    if (s.isEmpty()) {
      return null;
    }
    try {
      BigDecimal value = new BigDecimal(s);
      return negative ? value.negate() : value;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /** Forces a sign regardless of how the source expressed it. */
  public static BigDecimal outflow(BigDecimal value) {
    return value == null ? null : value.abs().negate();
  }

  public static BigDecimal inflow(BigDecimal value) {
    return value == null ? null : value.abs();
  }
}
