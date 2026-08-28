package dev.grindtrack.finance.service.parse;

/**
 * The statement shapes this app can read. Six, not four — Capital One alone exports two completely
 * different layouts depending on whether the account is a deposit account or a card.
 */
public enum StatementFormat {
  /**
   * {@code Account Number,Transaction Description,Transaction Date,Transaction Type,...,Balance}
   */
  CAPITAL_ONE_DEPOSIT("Capital One checking/savings"),
  /** {@code Transaction Date,Posted Date,Card No.,Description,Category,Debit,Credit} */
  CAPITAL_ONE_CREDIT("Capital One card"),
  /** {@code Transaction Date,Post Date,Description,Category,Type,Amount,Memo} */
  CHASE("Chase card"),
  /** {@code Posted Date,Reference Number,Payee,Address,Amount} */
  BANK_OF_AMERICA("Bank of America card"),
  /** {@code DATE,DESCRIPTION,AMOUNT,CHECK #,STATUS} */
  WELLS_FARGO("Wells Fargo card"),
  /** {@code Date,LoanName,Description,Principal,Interest,Fees,Total,UnpaidPrincipalBalanceValue} */
  AIDVANTAGE("Aidvantage student loans");

  private final String label;

  StatementFormat(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }
}
