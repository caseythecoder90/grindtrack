package dev.grindtrack.finance.domain;

/**
 * How a transaction got the category it has — and therefore whether anything is allowed to change
 * it automatically.
 *
 * <p>Concretely: an import brings in {@code AMZN MKTP US*2K4LM7} for $340 and a rule files it under
 * Shopping. It was actually a medical device, so it gets corrected by hand to Medical. The next
 * time an overlapping date range is imported, or the Amazon rule is edited, that correction must
 * survive. Without this field it would be silently reverted, and after that happens a few times the
 * tool stops being believable and stops being opened.
 *
 * <p>The rule is simply: {@link #MANUAL} is never overwritten by automation.
 */
public enum CategorySource {
  /** Nothing has classified this yet. It belongs in the review inbox. */
  UNCATEGORIZED,
  /** A rule assigned it. Safe for automation to reassign at any time. */
  RULE,
  /** A person decided. Automation must leave it alone. */
  MANUAL;

  /** True when automation is permitted to replace the current category. */
  public boolean isOverwritable() {
    return this != MANUAL;
  }
}
