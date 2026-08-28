package dev.grindtrack.finance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.grindtrack.finance.domain.Transaction;
import dev.grindtrack.finance.domain.TransactionRepository;
import dev.grindtrack.finance.domain.TxnType;
import dev.grindtrack.finance.service.RecurringDetector.Recurring;
import dev.grindtrack.finance.service.RecurringDetector.RecurringReport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Recurring detection is deliberately conservative: a wrong entry here becomes a wrong budget line,
 * so anything ambiguous must be left out rather than presented as a commitment.
 */
class RecurringDetectorTest {

  private TransactionRepository transactions;
  private RecurringDetector detector;

  @BeforeEach
  void setUp() {
    transactions = mock(TransactionRepository.class);
    detector = new RecurringDetector(transactions);
    when(transactions.findCountableBetween(any(), any())).thenReturn(List.of());
  }

  private static Transaction txn(LocalDate date, String amount, String merchant) {
    Transaction t = new Transaction(1L, date, new BigDecimal(amount), merchant);
    t.applyImportedDetail(null, merchant, null, TxnType.SPEND, false);
    return t;
  }

  /** {@code count} charges ending today, one every {@code everyDays}. */
  private void given(String merchant, String amount, int everyDays, int count) {
    givenEndingAt(merchant, amount, everyDays, count, LocalDate.now());
  }

  private void givenEndingAt(
      String merchant, String amount, int everyDays, int count, LocalDate last) {
    List<Transaction> rows = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      rows.add(txn(last.minusDays((long) everyDays * i), amount, merchant));
    }
    when(transactions.findCountableBetween(any(), any())).thenReturn(rows);
  }

  private static Optional<Recurring> pick(RecurringReport report, String merchant) {
    return report.items().stream().filter(r -> r.merchant().equals(merchant)).findFirst();
  }

  @Test
  void aMonthlyChargeIsFound() {
    given("EXAMPLE STREAMING", "-15.99", 30, 6);

    Recurring found = pick(detector.detect(), "EXAMPLE STREAMING").orElseThrow();

    assertThat(found.cadence()).isEqualTo("MONTHLY");
    assertThat(found.occurrences()).isEqualTo(6);
    assertThat(found.typicalAmount()).isEqualByComparingTo("15.99");
    assertThat(found.monthlyEquivalent()).isEqualByComparingTo("16.22");
    assertThat(found.lapsed()).isFalse();
    assertThat(found.variable()).isFalse();
  }

  @Test
  void twoChargesAreNotEnoughToCallSomethingRecurring() {
    // Two points make a line through any two dates. Three is the first number that means anything.
    given("EXAMPLE SHOP", "-40.00", 30, 2);
    assertThat(detector.detect().items()).isEmpty();
  }

  @Test
  void anIrregularMerchantIsNotReportedAsACommitment() {
    // Groceries every few days is real spending, but it is not a subscription and must not be
    // offered as a budget line with a due date.
    List<Transaction> rows =
        List.of(
            txn(LocalDate.now().minusDays(1), "-40", "EXAMPLE GROCER"),
            txn(LocalDate.now().minusDays(4), "-95", "EXAMPLE GROCER"),
            txn(LocalDate.now().minusDays(31), "-12", "EXAMPLE GROCER"),
            txn(LocalDate.now().minusDays(33), "-140", "EXAMPLE GROCER"));
    when(transactions.findCountableBetween(any(), any())).thenReturn(rows);

    assertThat(detector.detect().items()).isEmpty();
  }

  @Test
  void gapsThatAverageOutButAreNotConsistentAreRejected() {
    // A plausible median gap is not the same as a rhythm.
    List<Transaction> rows =
        List.of(
            txn(LocalDate.now().minusDays(0), "-20", "EXAMPLE THING"),
            txn(LocalDate.now().minusDays(2), "-20", "EXAMPLE THING"),
            txn(LocalDate.now().minusDays(32), "-20", "EXAMPLE THING"),
            txn(LocalDate.now().minusDays(120), "-20", "EXAMPLE THING"));
    when(transactions.findCountableBetween(any(), any())).thenReturn(rows);

    assertThat(detector.detect().items()).isEmpty();
  }

  @Test
  void aQuarterlyBillIsExpressedPerMonthSoItCanBeAddedToMonthlyOnes() {
    given("EXAMPLE INSURANCE", "-300", 91, 3);

    Recurring found = pick(detector.detect(), "EXAMPLE INSURANCE").orElseThrow();

    assertThat(found.cadence()).isEqualTo("QUARTERLY");
    assertThat(found.typicalAmount()).isEqualByComparingTo("300");
    assertThat(found.monthlyEquivalent()).isEqualByComparingTo("100.35");
  }

  @Test
  void aChargeThatStoppedIsFlaggedRatherThanCountedAsLive() {
    // Either it was cancelled, or it is about to surprise you. Both are worth seeing.
    givenEndingAt("EXAMPLE GYM", "-45", 30, 4, LocalDate.now().minusDays(100));

    RecurringReport report = detector.detect();
    Recurring found = pick(report, "EXAMPLE GYM").orElseThrow();

    assertThat(found.lapsed()).isTrue();
    assertThat(report.liveCount()).isZero();
    assertThat(report.lapsedCount()).isEqualTo(1);
    assertThat(report.monthlyCommitment()).isEqualByComparingTo("0");
  }

  @Test
  void anAmountThatMovesAroundIsMarkedVariable() {
    // A utility, not a subscription. Budgeting its typical figure needs to be an informed choice.
    List<Transaction> rows =
        List.of(
            txn(LocalDate.now(), "-520", "EXAMPLE POWER"),
            txn(LocalDate.now().minusDays(30), "-310", "EXAMPLE POWER"),
            txn(LocalDate.now().minusDays(60), "-180", "EXAMPLE POWER"),
            txn(LocalDate.now().minusDays(90), "-240", "EXAMPLE POWER"));
    when(transactions.findCountableBetween(any(), any())).thenReturn(rows);

    Recurring found = pick(detector.detect(), "EXAMPLE POWER").orElseThrow();

    assertThat(found.variable()).isTrue();
    assertThat(found.lowest()).isEqualByComparingTo("180");
    assertThat(found.highest()).isEqualByComparingTo("520");
  }

  @Test
  void theReportTotalsTheLiveCommitmentsIntoOneNumber() {
    // The fixed monthly nut, which is the figure a budget starts from.
    List<Transaction> rows = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      rows.add(txn(LocalDate.now().minusDays(30L * i), "-2724.96", "EXAMPLE LANDLORD"));
      rows.add(txn(LocalDate.now().minusDays(30L * i), "-307.45", "EXAMPLE AUTO"));
    }
    when(transactions.findCountableBetween(any(), any())).thenReturn(rows);

    RecurringReport report = detector.detect();

    assertThat(report.liveCount()).isEqualTo(2);
    // Biggest first, because that is the order you would act on them in.
    assertThat(report.items().get(0).merchant()).isEqualTo("EXAMPLE LANDLORD");
    assertThat(report.monthlyCommitment()).isEqualByComparingTo("3076.89");
  }

  @Test
  void incomeAndTransfersAreNeverOfferedAsRecurringSpending() {
    Transaction payroll = new Transaction(1L, LocalDate.now(), new BigDecimal("4000"), "PAYROLL");
    payroll.applyImportedDetail(null, "PAYROLL", null, TxnType.INCOME, false);
    Transaction move = new Transaction(1L, LocalDate.now(), new BigDecimal("-500"), "TO SAVINGS");
    move.applyImportedDetail(null, "TO SAVINGS", null, TxnType.TRANSFER, false);
    when(transactions.findCountableBetween(any(), any()))
        .thenReturn(List.of(payroll, payroll, payroll, move, move, move));

    assertThat(detector.detect().items()).isEmpty();
  }
}
