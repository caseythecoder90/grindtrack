package dev.grindtrack.finance.domain;

/**
 * The bank or servicer an account belongs to.
 *
 * <p>This is not decoration: each institution exports statements in its own shape, so the value
 * here selects the parser in phase 2. The six below are the real set — Capital One (checking,
 * savings and three cards), Chase, Wells Fargo, Bank of America, and Aidvantage for the student
 * loans. {@link #OTHER} exists so a manually-tracked account never needs a schema change.
 */
public enum Institution {
  CAPITAL_ONE,
  CHASE,
  WELLS_FARGO,
  BANK_OF_AMERICA,
  AIDVANTAGE,
  OTHER
}
