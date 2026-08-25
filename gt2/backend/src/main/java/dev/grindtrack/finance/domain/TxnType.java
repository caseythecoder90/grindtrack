package dev.grindtrack.finance.domain;

/**
 * What a transaction actually represents, which decides whether it counts as spending.
 *
 * <p>This distinction is the whole reason the finance feature can be trusted. A credit-card payment
 * is not an expense — the expense was the original purchase on the card. Counting the purchase
 * <em>and</em> the payment that settles it double-counts every dollar. The same goes for moving
 * money from checking into savings: nothing was spent, it just moved.
 *
 * <p>In the first pass over real statements, 78 of 947 rows were transfers or card payments. Left
 * as {@link #SPEND} they would have inflated reported spending by several thousand dollars.
 */
public enum TxnType {
  /** Money genuinely leaving the household: a purchase, a bill, a fee. */
  SPEND,
  /** Money genuinely arriving: payroll, interest, a refund from outside the household. */
  INCOME,
  /** Movement between two accounts that are both yours. Nets to zero, counts as neither. */
  TRANSFER,
  /** A payment to a card or loan. Settles debt that was already recorded as SPEND. */
  PAYMENT;

  /** True only for the two types that belong in spending and income rollups. */
  public boolean countsInRollups() {
    return this == SPEND || this == INCOME;
  }
}
