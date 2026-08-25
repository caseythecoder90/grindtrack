package dev.grindtrack.finance.domain;

/**
 * What kind of account this is, which decides how its balance is read.
 *
 * <p>{@link #CHECKING} and {@link #SAVINGS} hold a positive balance when they hold money. {@link
 * #CREDIT_CARD} and {@link #LOAN} hold a negative balance when money is owed, so a net-worth
 * roll-up can sum every account without special-casing sign per type.
 */
public enum AccountType {
  CHECKING,
  SAVINGS,
  CREDIT_CARD,
  LOAN;

  /** True for the account types where a balance represents debt rather than cash on hand. */
  public boolean isLiability() {
    return this == CREDIT_CARD || this == LOAN;
  }
}
