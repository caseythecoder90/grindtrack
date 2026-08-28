package dev.grindtrack.finance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.grindtrack.finance.domain.Account;
import dev.grindtrack.finance.domain.AccountRepository;
import dev.grindtrack.finance.domain.AccountType;
import dev.grindtrack.finance.domain.ImportBatch;
import dev.grindtrack.finance.domain.ImportBatchRepository;
import dev.grindtrack.finance.domain.Institution;
import dev.grindtrack.finance.domain.Transaction;
import dev.grindtrack.finance.domain.TransactionRepository;
import dev.grindtrack.finance.domain.TxnType;
import dev.grindtrack.finance.service.StatementImportService.ImportResult;
import dev.grindtrack.finance.service.parse.AidvantageParser;
import dev.grindtrack.finance.service.parse.BankOfAmericaParser;
import dev.grindtrack.finance.service.parse.CapitalOneCreditParser;
import dev.grindtrack.finance.service.parse.CapitalOneDepositParser;
import dev.grindtrack.finance.service.parse.ChaseParser;
import dev.grindtrack.finance.service.parse.StatementParseException;
import dev.grindtrack.finance.service.parse.StatementParser;
import dev.grindtrack.finance.service.parse.WellsFargoParser;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StatementImportServiceTest {

  private static final Long ACCOUNT_ID = 7L;

  private AccountRepository accounts;
  private TransactionRepository transactions;
  private ImportBatchRepository batches;
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
    service =
        new StatementImportService(
            PARSERS,
            accounts,
            transactions,
            batches,
            new MerchantNormalizer(),
            new TxnTypeClassifier());

    when(batches.save(any(ImportBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(transactions.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  private void givenAccount(AccountType type, String last4) {
    Account account = new Account("Test", Institution.CAPITAL_ONE, type);
    account.update("Test", Institution.CAPITAL_ONE, type, last4, false, true, 0);
    when(accounts.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
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
    when(transactions.existsByAccountIdAndFingerprint(eq(ACCOUNT_ID), anyString()))
        .thenReturn(false);

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
    when(transactions.existsByAccountIdAndFingerprint(eq(ACCOUNT_ID), anyString()))
        .thenReturn(true);

    ImportResult result = service.importStatement(ACCOUNT_ID, "chase.csv", CHASE_CSV, false);

    assertThat(result.imported()).isZero();
    assertThat(result.duplicates()).isEqualTo(2);
  }

  @Test
  void aRowRepeatedInsideOneFileIsOnlyImportedOnce() {
    // Capital One's yearly and monthly exports overlap, so a single upload can contain the same
    // transaction twice. The database check alone would not catch that.
    givenAccount(AccountType.CREDIT_CARD, null);
    when(transactions.existsByAccountIdAndFingerprint(eq(ACCOUNT_ID), anyString()))
        .thenReturn(false);

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
    when(transactions.existsByAccountIdAndFingerprint(eq(ACCOUNT_ID), anyString()))
        .thenReturn(false);

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
    when(transactions.existsByAccountIdAndFingerprint(eq(ACCOUNT_ID), anyString()))
        .thenReturn(false);

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

    // A loan balance is a liability, so it must land negative for net worth to be right.
    ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
    verify(accounts).save(captor.capture());
    assertThat(captor.getValue().getCurrentBalance()).isEqualByComparingTo("-6447.36");
  }

  @Test
  void aDryRunReportsTheSameCountsButWritesNothing() {
    givenAccount(AccountType.CREDIT_CARD, null);
    when(transactions.existsByAccountIdAndFingerprint(eq(ACCOUNT_ID), anyString()))
        .thenReturn(false);

    ImportResult result = service.importStatement(ACCOUNT_ID, "chase.csv", CHASE_CSV, true);

    assertThat(result.dryRun()).isTrue();
    assertThat(result.imported()).isEqualTo(2);
    assertThat(result.batchId()).isNull();
    verify(transactions, never()).saveAll(any());
    verify(batches, never()).save(any());
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
