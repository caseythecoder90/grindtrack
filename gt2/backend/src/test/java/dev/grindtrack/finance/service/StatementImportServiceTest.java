package dev.grindtrack.finance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.grindtrack.finance.domain.Account;
import dev.grindtrack.finance.domain.AccountRepository;
import dev.grindtrack.finance.domain.AccountType;
import dev.grindtrack.finance.domain.CategoryRule;
import dev.grindtrack.finance.domain.CategoryRuleRepository;
import dev.grindtrack.finance.domain.ImportBatch;
import dev.grindtrack.finance.domain.ImportBatchRepository;
import dev.grindtrack.finance.domain.Institution;
import dev.grindtrack.finance.domain.MatchType;
import dev.grindtrack.finance.domain.Transaction;
import dev.grindtrack.finance.domain.TransactionRepository;
import dev.grindtrack.finance.domain.TxnType;
import dev.grindtrack.finance.service.StatementImportService.ImportResult;
import dev.grindtrack.finance.service.parse.AidvantageParser;
import dev.grindtrack.finance.service.parse.BankOfAmericaParser;
import dev.grindtrack.finance.service.parse.CapitalOneCreditParser;
import dev.grindtrack.finance.service.parse.CapitalOneDepositParser;
import dev.grindtrack.finance.service.parse.ChaseParser;
import dev.grindtrack.finance.service.parse.OfxInvestmentParser;
import dev.grindtrack.finance.service.parse.StatementParseException;
import dev.grindtrack.finance.service.parse.StatementParser;
import dev.grindtrack.finance.service.parse.WellsFargoParser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StatementImportServiceTest {

  private static final Long ACCOUNT_ID = 7L;

  private AccountRepository accounts;
  private TransactionRepository transactions;
  private ImportBatchRepository batches;
  private CategoryRuleRepository categoryRules;
  private StatementImportService service;

  private static final List<StatementParser> PARSERS =
      List.of(
          new CapitalOneDepositParser(),
          new CapitalOneCreditParser(),
          new ChaseParser(),
          new BankOfAmericaParser(),
          new WellsFargoParser(),
          new AidvantageParser());

  @BeforeEach
  void setUp() {
    accounts = mock(AccountRepository.class);
    transactions = mock(TransactionRepository.class);
    batches = mock(ImportBatchRepository.class);
    categoryRules = mock(CategoryRuleRepository.class);
    service =
        new StatementImportService(
            PARSERS,
            accounts,
            transactions,
            batches,
            new MerchantNormalizer(),
            new TxnTypeClassifier(),
            new CategoryRuleService(categoryRules, transactions),
            new OfxInvestmentParser());

    when(batches.save(any(ImportBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(transactions.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(transactions.findFingerprintsByAccountId(anyLong())).thenReturn(Set.of());
    when(categoryRules.findByActiveTrueOrderByPriorityAscIdAsc()).thenReturn(List.of());
  }

  private Account givenAccount(AccountType type, String last4) {
    Account account = new Account("Test", Institution.CAPITAL_ONE, type);
    account.update("Test", Institution.CAPITAL_ONE, type, last4, false, true, 0);
    when(accounts.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
    return account;
  }

  private static final String CHASE_CSV =
      """
      Transaction Date,Post Date,Description,Category,Type,Amount,Memo
      08/23/2026,08/23/2026,EXAMPLE STORE,Shopping,Sale,-28.46,
      08/17/2026,08/17/2026,EXAMPLE CAFE,Food & Drink,Sale,-4.75,
      """;

  @Test
  void importsNewRowsAndReportsTheFormat() {
    givenAccount(AccountType.CREDIT_CARD, null);

    ImportResult result = service.importStatement(ACCOUNT_ID, "chase.csv", CHASE_CSV, false);

    assertThat(result.imported()).isEqualTo(2);
    assertThat(result.duplicates()).isZero();
    assertThat(result.format()).isEqualTo("Chase card");
    assertThat(result.periodStart()).isEqualTo("2026-08-17");
    assertThat(result.periodEnd()).isEqualTo("2026-08-23");
  }

  @Test
  void rowsAlreadyInTheDatabaseAreSkippedNotDuplicated() {
    // The whole point of re-importing an overlapping range: it should be a no-op.
    givenAccount(AccountType.CREDIT_CARD, null);
    when(transactions.findFingerprintsByAccountId(ACCOUNT_ID))
        .thenReturn(
            Set.of(
                Transaction.fingerprintOf(
                    ACCOUNT_ID,
                    LocalDate.of(2026, 8, 23),
                    new BigDecimal("-28.46"),
                    "EXAMPLE STORE"),
                Transaction.fingerprintOf(
                    ACCOUNT_ID,
                    LocalDate.of(2026, 8, 17),
                    new BigDecimal("-4.75"),
                    "EXAMPLE CAFE")));

    ImportResult result = service.importStatement(ACCOUNT_ID, "chase.csv", CHASE_CSV, false);

    assertThat(result.imported()).isZero();
    assertThat(result.duplicates()).isEqualTo(2);
  }

  @Test
  void aRowRepeatedInsideOneFileIsOnlyImportedOnce() {
    // Capital One's yearly and monthly exports overlap, so a single upload can contain the same
    // transaction twice. The database check alone would not catch that.
    givenAccount(AccountType.CREDIT_CARD, null);

    String withRepeat =
        """
        Transaction Date,Post Date,Description,Category,Type,Amount,Memo
        08/23/2026,08/23/2026,EXAMPLE STORE,Shopping,Sale,-28.46,
        08/23/2026,08/23/2026,EXAMPLE STORE,Shopping,Sale,-28.46,
        """;

    ImportResult result = service.importStatement(ACCOUNT_ID, "chase.csv", withRepeat, false);

    assertThat(result.imported()).isEqualTo(1);
    assertThat(result.duplicates()).isEqualTo(1);
  }

  @Test
  void transactionTypeIsClassifiedOnImport() {
    givenAccount(AccountType.CHECKING, null);

    service.importStatement(
        ACCOUNT_ID,
        "capitalone.csv",
        """
        Account Number,Transaction Description,Transaction Date,Transaction Type,Transaction Amount,Balance
        1234,Withdrawal from CAPITAL ONE CRCARDPMT,08/24/26,Debit,500.00,1000.00
        1234,Deposit from EXAMPLE EMPLOYER PAYROLL DD,08/22/26,Credit,1200.00,1500.00
        1234,Debit Card Purchase - EXAMPLE DINER 555 010 2030 FL,08/20/26,Debit,42.10,300.00
        """,
        false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Transaction>> captor = ArgumentCaptor.forClass(List.class);
    verify(transactions).saveAll(captor.capture());
    List<Transaction> saved = captor.getValue();

    assertThat(saved).hasSize(3);
    assertThat(saved.get(0).getTxnType()).isEqualTo(TxnType.PAYMENT);
    assertThat(saved.get(1).getTxnType()).isEqualTo(TxnType.INCOME);
    assertThat(saved.get(2).getTxnType()).isEqualTo(TxnType.SPEND);
    // And the merchant is normalized, not left as the raw bank string.
    assertThat(saved.get(2).getMerchant()).isEqualTo("EXAMPLE DINER");
  }

  @Test
  void aCardStatementCannotBeImportedIntoADifferentCard() {
    // Three Capital One cards makes this an easy mistake and a tedious one to unpick.
    givenAccount(AccountType.CREDIT_CARD, "6768");

    assertThatThrownBy(
            () ->
                service.importStatement(
                    ACCOUNT_ID,
                    "savor.csv",
                    """
                    Transaction Date,Posted Date,Card No.,Description,Category,Debit,Credit
                    2026-08-04,2026-08-05,7575,EXAMPLE SHOP,Merchandise,60.00,
                    """,
                    false))
        .isInstanceOf(StatementParseException.class)
        .hasMessageContaining("7575")
        .hasMessageContaining("6768");

    verify(transactions, never()).saveAll(any());
  }

  @Test
  void aMatchingCardNumberIsAccepted() {
    givenAccount(AccountType.CREDIT_CARD, "7575");

    ImportResult result =
        service.importStatement(
            ACCOUNT_ID,
            "savor.csv",
            """
            Transaction Date,Posted Date,Card No.,Description,Category,Debit,Credit
            2026-08-04,2026-08-05,7575,EXAMPLE SHOP,Merchandise,60.00,
            """,
            false);

    assertThat(result.imported()).isEqualTo(1);
  }

  // ---------- the format must belong to the account type ----------

  @Test
  void theLoanExportCannotBeImportedIntoACheckingAccount() {
    // The damage this prevents is specific: Aidvantage asserts a balance, so this upload would
    // replace the checking balance with the student-loan principal and import nothing at all.
    givenAccount(AccountType.CHECKING, null);

    assertThatThrownBy(
            () ->
                service.importStatement(
                    ACCOUNT_ID,
                    "AllLoans.csv",
                    """
                    Date,LoanName,Description,Principal,Interest,Fees,Total,UnpaidPrincipalBalanceValue,
                    07/28/2026,1-02 Example Loan,PAYMENT,-$27.89,-$9.39,$0.00,-$37.28,"$2,980.76",
                    """,
                    false))
        .isInstanceOf(StatementParseException.class)
        .hasMessageContaining("loan")
        .hasMessageContaining("checking");

    verify(accounts, never()).save(any());
    verify(transactions, never()).saveAll(any());
  }

  @Test
  void aCheckingExportCannotBeImportedIntoACreditCard() {
    // Signs mean the opposite on a card, so 420 rows would land inverted.
    givenAccount(AccountType.CREDIT_CARD, null);

    assertThatThrownBy(
            () ->
                service.importStatement(
                    ACCOUNT_ID,
                    "checking.csv",
                    """
                    Account Number,Transaction Description,Transaction Date,Transaction Type,Transaction Amount,Balance
                    1234,Debit Card Purchase - EXAMPLE DINER,08/24/26,Debit,42.10,300.00
                    """,
                    false))
        .isInstanceOf(StatementParseException.class)
        .hasMessageContaining("credit card");
  }

  // ---------- rows that could not be read are counted, never dropped in silence ----------

  @Test
  void unreadableRowsAreCountedAndReported() {
    givenAccount(AccountType.CREDIT_CARD, null);

    ImportResult result =
        service.importStatement(
            ACCOUNT_ID,
            "chase.csv",
            """
            Transaction Date,Post Date,Description,Category,Type,Amount,Memo
            08/23/2026,08/23/2026,EXAMPLE STORE,Shopping,Sale,-28.46,
            08/22/2026,not-a-date,EXAMPLE SHOP,Shopping,Sale,-10.00,
            08/21/2026,08/21/2026,EXAMPLE CAFE,Food,Sale,not-a-number,
            """,
            false);

    assertThat(result.imported()).isEqualTo(1);
    assertThat(result.skipped()).isEqualTo(2);
    // The counts have to account for every row in the file, or the import is quietly lying.
    assertThat(result.rowsInFile())
        .isEqualTo(result.imported() + result.duplicates() + result.pending() + result.skipped());
    assertThat(result.warnings()).anyMatch(w -> w.contains("could not be read"));
  }

  @Test
  void pendingRowsAreCountedSeparatelyFromUnreadableOnes() {
    givenAccount(AccountType.CREDIT_CARD, null);

    ImportResult result =
        service.importStatement(
            ACCOUNT_ID,
            "wf.csv",
            """
            "DATE","DESCRIPTION","AMOUNT","CHECK #","STATUS"
            "08/24/2026","EXAMPLE CAFE","-11.89",,"Posted"
            "08/25/2026","EXAMPLE FUEL","-45.00",,"Pending"
            """,
            false);

    assertThat(result.imported()).isEqualTo(1);
    assertThat(result.pending()).isEqualTo(1);
    assertThat(result.skipped()).isZero();
    assertThat(result.rowsInFile()).isEqualTo(2);
  }

  // ---------- balances ----------

  @Test
  void aidvantageUpdatesTheBalanceWithoutCreatingTransactions() {
    givenAccount(AccountType.LOAN, null);

    ImportResult result =
        service.importStatement(
            ACCOUNT_ID,
            "AllLoans.csv",
            """
            <!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.0 Transitional//EN">Date,LoanName,Description,Principal,Interest,Fees,Total,UnpaidPrincipalBalanceValue,
            07/28/2026,1-02 Example Loan,PAYMENT,-$27.89,-$9.39,$0.00,-$37.28,"$2,980.76",
            07/28/2026,1-03 Example Loan,PAYMENT,-$32.56,-$10.92,$0.00,-$43.48,"$3,466.60",
            """,
            false);

    assertThat(result.imported()).isZero();
    assertThat(result.balanceUpdate()).isEqualTo("6447.36");
    // Zero imported rows from a two-row file looks like a failure unless it says why.
    assertThat(result.warnings()).anyMatch(w -> w.contains("No transactions imported"));

    // A loan balance is a liability, so it must land negative for net worth to be right.
    ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
    verify(accounts).save(captor.capture());
    assertThat(captor.getValue().getCurrentBalance()).isEqualByComparingTo("-6447.36");
  }

  @Test
  void undoRestoresTheBalanceTheImportOverwrote() {
    // Undo used to remove the rows and leave the overwritten balance behind for good, which made
    // undoing a wrong-account upload a partial repair with no record of the original figure.
    Account account = givenAccount(AccountType.LOAN, null);
    account.recordBalance(new BigDecimal("-100.00"), LocalDate.of(2026, 1, 1));

    service.importStatement(
        ACCOUNT_ID,
        "AllLoans.csv",
        """
        Date,LoanName,Description,Principal,Interest,Fees,Total,UnpaidPrincipalBalanceValue,
        07/28/2026,1-02 Example Loan,PAYMENT,-$27.89,-$9.39,$0.00,-$37.28,"$2,980.76",
        """,
        false);
    assertThat(account.getCurrentBalance()).isEqualByComparingTo("-2980.76");

    ArgumentCaptor<ImportBatch> batchCaptor = ArgumentCaptor.forClass(ImportBatch.class);
    verify(batches, org.mockito.Mockito.atLeastOnce()).save(batchCaptor.capture());
    ImportBatch batch = batchCaptor.getValue();
    when(batches.findById(any())).thenReturn(Optional.of(batch));
    when(accounts.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));

    service.undo(1L);

    assertThat(batch.isBalanceOverwritten()).isTrue();
    assertThat(account.getCurrentBalance()).isEqualByComparingTo("-100.00");
    assertThat(account.getBalanceAsOf()).isEqualTo(LocalDate.of(2026, 1, 1));
  }

  // ---------- rules ----------

  @Test
  void categoryRulesAreAppliedAsRowsAreImported() {
    // Without this the importer files everything as UNCATEGORIZED and hands the user 800 rows.
    givenAccount(AccountType.CREDIT_CARD, null);
    when(categoryRules.findByActiveTrueOrderByPriorityAscIdAsc())
        .thenReturn(List.of(new CategoryRule("EXAMPLE CAFE", MatchType.CONTAINS, "Coffee", 100)));

    ImportResult result = service.importStatement(ACCOUNT_ID, "chase.csv", CHASE_CSV, false);

    assertThat(result.categorized()).isEqualTo(1);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Transaction>> captor = ArgumentCaptor.forClass(List.class);
    verify(transactions).saveAll(captor.capture());
    assertThat(captor.getValue())
        .filteredOn(t -> t.getRawDescription().equals("EXAMPLE CAFE"))
        .allMatch(t -> "Coffee".equals(t.getCategory()));
  }

  // ---------- dry run ----------

  @Test
  void aDryRunReportsTheSameCountsButWritesNothing() {
    givenAccount(AccountType.CREDIT_CARD, null);
    when(categoryRules.findByActiveTrueOrderByPriorityAscIdAsc())
        .thenReturn(List.of(new CategoryRule("EXAMPLE CAFE", MatchType.CONTAINS, "Coffee", 100)));

    ImportResult result = service.importStatement(ACCOUNT_ID, "chase.csv", CHASE_CSV, true);

    assertThat(result.dryRun()).isTrue();
    assertThat(result.imported()).isEqualTo(2);
    assertThat(result.categorized()).isEqualTo(1);
    assertThat(result.batchId()).isNull();
    verify(transactions, never()).saveAll(any());
    verify(batches, never()).save(any());
    // Rule hit counts are a write too, and the preview promises to change nothing.
    verify(categoryRules, never()).saveAll(any());
  }

  @Test
  void anUnrecognizedFileSaysSoRatherThanImportingNothingSilently() {
    givenAccount(AccountType.CHECKING, null);

    assertThatThrownBy(
            () -> service.importStatement(ACCOUNT_ID, "mystery.csv", "foo,bar\n1,2\n", false))
        .isInstanceOf(StatementParseException.class)
        .hasMessageContaining("Unrecognized statement format");
  }
}
