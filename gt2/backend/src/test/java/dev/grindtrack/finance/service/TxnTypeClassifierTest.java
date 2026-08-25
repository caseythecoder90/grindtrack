package dev.grindtrack.finance.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.grindtrack.finance.domain.AccountType;
import dev.grindtrack.finance.domain.TxnType;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * The double-counting guard. In the first pass over real statements, 78 of 947 rows were transfers
 * or card payments — including $15,500 of checking-to-savings moves and $6,623 of Wells Fargo card
 * payments. Misclassifying those as spending roughly doubles reported outgoings.
 */
class TxnTypeClassifierTest {

  private final TxnTypeClassifier classifier = new TxnTypeClassifier();

  private TxnType classify(String description, String amount, AccountType type) {
    return classifier.classify(description, new BigDecimal(amount), type);
  }

  @Test
  void cardPaymentsAreNotSpending() {
    assertThat(classify("Withdrawal from CAPITAL ONE CRCARDPMT", "-500.00", AccountType.CHECKING))
        .isEqualTo(TxnType.PAYMENT);
    assertThat(
            classify("Withdrawal from CHASE CREDIT CRD AUTOPAY", "-236.00", AccountType.CHECKING))
        .isEqualTo(TxnType.PAYMENT);
    assertThat(classify("Withdrawal from WELLS FARGO CARD", "-1100.00", AccountType.CHECKING))
        .isEqualTo(TxnType.PAYMENT);
  }

  @Test
  void theCreditSideOfACardPaymentIsAlsoNotIncome() {
    // Same payment seen from the card's side. If this were INCOME the payment would net to zero
    // across the two accounts and silently erase a real expense.
    assertThat(classify("CAPITAL ONE AUTOPAY PYMT", "57.40", AccountType.CREDIT_CARD))
        .isEqualTo(TxnType.PAYMENT);
    assertThat(classify("AUTOMATIC PAYMENT - THANK YOU", "1887.25", AccountType.CREDIT_CARD))
        .isEqualTo(TxnType.PAYMENT);
  }

  @Test
  void movesBetweenOwnAccountsAreTransfers() {
    assertThat(classify("Withdrawal to 360 Checking XXXXXXX5830", "-1000.00", AccountType.SAVINGS))
        .isEqualTo(TxnType.TRANSFER);
    assertThat(classify("Deposit from BigTimeSavings XXXXXXX3711", "1000.00", AccountType.CHECKING))
        .isEqualTo(TxnType.TRANSFER);
    assertThat(
            classify(
                "Overdraft Transfer from BigTimeSavings XXXXXXX3711",
                "95.71",
                AccountType.CHECKING))
        .isEqualTo(TxnType.TRANSFER);
  }

  @Test
  void payrollAndInterestAreIncome() {
    assertThat(
            classify("Deposit from JPMORGAN CHASE B PAYROLL DD", "1274.80", AccountType.CHECKING))
        .isEqualTo(TxnType.INCOME);
    assertThat(classify("Monthly Interest Paid", "191.44", AccountType.SAVINGS))
        .isEqualTo(TxnType.INCOME);
  }

  @Test
  void studentLoanServicerIsAPayment() {
    // The $572.40 debit is the real money movement; the per-loan rows in AllLoans.csv are a
    // sub-ledger of the same payment and must never be imported as separate spending.
    assertThat(classify("Withdrawal from ADVS ED SERV", "-572.40", AccountType.CHECKING))
        .isEqualTo(TxnType.PAYMENT);
  }

  @Test
  void ordinaryPurchasesRemainSpending() {
    assertThat(
            classify(
                "Digital Card Purchase - WINGSTOP 1861 813 725 9464 FL",
                "-24.13",
                AccountType.CHECKING))
        .isEqualTo(TxnType.SPEND);
    assertThat(classify("WAL-MART #925 SEFFNER FL", "-13.28", AccountType.CREDIT_CARD))
        .isEqualTo(TxnType.SPEND);
  }

  @Test
  void refundOnACardOffsetsSpendingRatherThanCountingAsIncome() {
    // A merchant refund is money returning to a card. Treating it as INCOME would inflate
    // earnings; treating it as SPEND lets it net against the original purchase.
    assertThat(classify("CREDIT BALANCE REFUND", "6.74", AccountType.CREDIT_CARD))
        .isEqualTo(TxnType.SPEND);
  }

  @Test
  void unrecognizedRowsStaySpendRatherThanBeingGuessedAway() {
    // Conservative on purpose: a wrongly-included transfer is visible in a category list, whereas
    // a wrongly-excluded expense just disappears.
    assertThat(classify("SOME NEW MERCHANT NOBODY HAS SEEN", "-42.00", AccountType.CHECKING))
        .isEqualTo(TxnType.SPEND);
  }

  @Test
  void nullDescriptionDoesNotThrow() {
    assertThat(classifier.classify(null, new BigDecimal("-10.00"), AccountType.CHECKING))
        .isEqualTo(TxnType.SPEND);
  }
}
