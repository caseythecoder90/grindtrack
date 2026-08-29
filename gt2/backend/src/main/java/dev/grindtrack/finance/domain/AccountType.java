package dev.grindtrack.finance.domain;

/**
 * What kind of account this is, which decides how its balance is read.
 *
 * <p>{@link #CHECKING}, {@link #SAVINGS} and {@link #RETIREMENT} hold a positive balance when they
 * hold money. {@link #CREDIT_CARD} and {@link #LOAN} hold a negative balance when money is owed, so
 * a net-worth roll-up can sum every account without special-casing sign per type.
 */
public enum AccountType {
  CHECKING,
  SAVINGS,
  CREDIT_CARD,
  LOAN,
  /**
   * A 401k or IRA. An asset like savings, and deliberately not the same thing.
   *
   * <p>It counts toward net worth because it is money you own. It must never count toward a savings
   * goal, because a goal here means cash you could actually put down on a house, and this is not
   * that — see {@link #canCountTowardSavings()}.
   */
  RETIREMENT;

  /** True for the account types where a balance represents debt rather than cash on hand. */
  public boolean isLiability() {
    return this == CREDIT_CARD || this == LOAN;
  }

  /**
   * Whether an account of this type may be flagged as holding a savings goal.
   *
   * <p>Retirement is excluded because the savings figure answers one question — how much could go
   * toward a house right now — and a 401k cannot answer it without a penalty. Including $30k of it
   * would move the progress bar by a third of a down payment and none of that money is available.
   *
   * <p>Liabilities are excluded for the more obvious reason that a debt is not savings.
   */
  public boolean canCountTowardSavings() {
    return this == CHECKING || this == SAVINGS;
  }
}
