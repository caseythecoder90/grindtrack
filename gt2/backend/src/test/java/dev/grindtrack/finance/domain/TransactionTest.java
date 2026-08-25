package dev.grindtrack.finance.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TransactionTest {

  private static final Long ACCOUNT = 1L;
  private static final LocalDate DATE = LocalDate.of(2026, 8, 24);

  private Transaction txn(String amount, String description) {
    return new Transaction(ACCOUNT, DATE, new BigDecimal(amount), description);
  }

  // ---------- fingerprinting: the re-import guarantee ----------

  @Test
  void identicalRowsProduceTheSameFingerprint() {
    assertThat(txn("-11.89", "GET COVERED LLC NEW YORK NY").getFingerprint())
        .isEqualTo(txn("-11.89", "GET COVERED LLC NEW YORK NY").getFingerprint());
  }

  @Test
  void fingerprintIgnoresCaseAndWhitespaceDrift() {
    // The same statement re-exported can differ in spacing. It is still the same transaction.
    assertThat(txn("-13.28", "WAL-MART  #925   SEFFNER FL").getFingerprint())
        .isEqualTo(txn("-13.28", "wal-mart #925 seffner fl").getFingerprint());
  }

  @Test
  void differentAmountsProduceDifferentFingerprints() {
    assertThat(txn("-10.30", "SALLY BEAUTY #0396").getFingerprint())
        .isNotEqualTo(txn("-10.31", "SALLY BEAUTY #0396").getFingerprint());
  }

  @Test
  void trailingZeroesDoNotChangeIdentity() {
    // 12.50 and 12.5 are the same money and must not import twice.
    assertThat(txn("-12.50", "JIMMY JOHNS -1175").getFingerprint())
        .isEqualTo(txn("-12.5", "JIMMY JOHNS -1175").getFingerprint());
  }

  @Test
  void bankSuppliedReferenceReplacesTheComputedHash() {
    Transaction t = txn("-68.74", "ROSS STORES #304 BRANDON FL");
    t.useExternalReference("24610436126004040633249");
    assertThat(t.getFingerprint()).isEqualTo("24610436126004040633249");
  }

  @Test
  void blankExternalReferenceLeavesTheHashAlone() {
    Transaction t = txn("-68.74", "ROSS STORES #304 BRANDON FL");
    String original = t.getFingerprint();
    t.useExternalReference("   ");
    assertThat(t.getFingerprint()).isEqualTo(original);
  }

  // ---------- sticky categorization ----------

  @Test
  void aRuleCanCategorizeSomethingUncategorized() {
    Transaction t = txn("-340.00", "AMZN MKTP US*2K4LM7");
    assertThat(t.categorizeByRule("Shopping")).isTrue();
    assertThat(t.getCategory()).isEqualTo("Shopping");
    assertThat(t.getCategorySource()).isEqualTo(CategorySource.RULE);
  }

  @Test
  void aRuleMayReplaceAnotherRulesGuess() {
    Transaction t = txn("-340.00", "AMZN MKTP US*2K4LM7");
    t.categorizeByRule("Shopping");
    assertThat(t.categorizeByRule("Merchandise")).isTrue();
    assertThat(t.getCategory()).isEqualTo("Merchandise");
  }

  @Test
  void aRuleNeverOverwritesAHumanDecision() {
    // The scenario this whole mechanism exists for: an Amazon order that was actually a medical
    // device, corrected by hand, then a later re-import tries to file it under Shopping again.
    Transaction t = txn("-340.00", "AMZN MKTP US*2K4LM7");
    t.categorizeManually("Medical");

    assertThat(t.categorizeByRule("Shopping")).isFalse();
    assertThat(t.getCategory()).isEqualTo("Medical");
    assertThat(t.getCategorySource()).isEqualTo(CategorySource.MANUAL);
  }

  @Test
  void aHumanCanStillOverrideAHumanDecision() {
    Transaction t = txn("-340.00", "AMZN MKTP US*2K4LM7");
    t.categorizeManually("Medical");
    t.categorizeManually("Health & Wellness");
    assertThat(t.getCategory()).isEqualTo("Health & Wellness");
  }

  @Test
  void clearingACategoryByHandReturnsItToTheInbox() {
    Transaction t = txn("-340.00", "AMZN MKTP US*2K4LM7");
    t.categorizeManually("Medical");
    t.categorizeManually("");
    assertThat(t.getCategory()).isEmpty();
    assertThat(t.getCategorySource()).isEqualTo(CategorySource.UNCATEGORIZED);
  }

  @Test
  void reclassifyingTypeDoesNotDisturbTheCategory() {
    Transaction t = txn("-500.00", "Withdrawal from CAPITAL ONE CRCARDPMT");
    t.categorizeManually("Debt");
    t.reclassify(TxnType.PAYMENT);
    assertThat(t.getTxnType()).isEqualTo(TxnType.PAYMENT);
    assertThat(t.getCategory()).isEqualTo("Debt");
    assertThat(t.getCategorySource()).isEqualTo(CategorySource.MANUAL);
  }
}
