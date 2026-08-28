package dev.grindtrack.finance.domain;

import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * How a {@link CategoryRule} decides whether it owns a transaction.
 *
 * <p>Three, in ascending order of rope. {@link #CONTAINS} covers almost everything, because bank
 * descriptions bury the merchant in noise. {@link #EQUALS} exists for the short names that would
 * otherwise catch too much — a CONTAINS rule for "BP" matches "BPM SUPPLY" and "SUBP". {@link
 * #REGEX} is the escape hatch, and the only one that can be written wrong in a way that throws, so
 * it is validated when the rule is saved rather than when it runs.
 */
public enum MatchType {
  CONTAINS {
    @Override
    public boolean matches(String pattern, String value) {
      return value.contains(pattern.toUpperCase(Locale.ROOT));
    }
  },
  EQUALS {
    @Override
    public boolean matches(String pattern, String value) {
      return value.equalsIgnoreCase(pattern);
    }
  },
  REGEX {
    @Override
    public boolean matches(String pattern, String value) {
      try {
        return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(value).find();
      } catch (PatternSyntaxException e) {
        // A rule saved before validation existed, or edited directly in the database. One broken
        // rule must not fail a whole import, so it simply matches nothing.
        return false;
      }
    }
  };

  /**
   * @param value already upper-cased by the caller, which does it once per transaction rather than
   *     once per rule per transaction
   */
  public abstract boolean matches(String pattern, String value);

  /**
   * @return null when the pattern is usable, or the reason it is not
   */
  public String validate(String pattern) {
    if (pattern == null || pattern.isBlank()) {
      return "a pattern is required";
    }
    if (this == REGEX) {
      try {
        Pattern.compile(pattern);
      } catch (PatternSyntaxException e) {
        return "that is not a valid regular expression: " + e.getDescription();
      }
    }
    return null;
  }
}
