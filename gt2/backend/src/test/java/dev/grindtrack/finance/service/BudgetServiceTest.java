package dev.grindtrack.finance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.grindtrack.finance.domain.Budget;
import dev.grindtrack.finance.domain.BudgetExtra;
import dev.grindtrack.finance.domain.BudgetExtraRepository;
import dev.grindtrack.finance.domain.BudgetRepository;
import dev.grindtrack.finance.domain.BudgetSettings;
import dev.grindtrack.finance.domain.BudgetSettingsRepository;
import dev.grindtrack.finance.domain.CategoryTotal;
import dev.grindtrack.finance.domain.TransactionRepository;
import dev.grindtrack.finance.service.BudgetMonth.CategoryLine;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The budget, and specifically the separation it exists to maintain: the recurring plan and the
 * things that only happen this month are different numbers and must stay that way.
 */
class BudgetServiceTest {

  /** A finished month, so the pace logic is out of the way except where it is the subject. */
  private static final YearMonth PAST = YearMonth.of(2026, 7);

  private BudgetRepository budgets;
  private BudgetExtraRepository extras;
  private BudgetSettingsRepository settings;
  private TransactionRepository transactions;
  private BudgetService service;

  @BeforeEach
  void setUp() {
    budgets = mock(BudgetRepository.class);
    extras = mock(BudgetExtraRepository.class);
    settings = mock(BudgetSettingsRepository.class);
    transactions = mock(TransactionRepository.class);
    service = new BudgetService(budgets, extras, settings, transactions);

    when(budgets.findByActiveTrueOrderBySortOrderAscCategoryAsc()).thenReturn(List.of());
    when(budgets.save(any(Budget.class))).thenAnswer(i -> i.getArgument(0));
    when(budgets.findByCategoryIgnoreCase(any())).thenReturn(Optional.empty());
    when(extras.findByMonthOrderByIdAsc(any())).thenReturn(List.of());
    when(extras.save(any(BudgetExtra.class))).thenAnswer(i -> i.getArgument(0));
    when(settings.findById((short) 1)).thenReturn(Optional.of(BudgetSettings.initial()));
    when(settings.save(any(BudgetSettings.class))).thenAnswer(i -> i.getArgument(0));
    when(transactions.spendByCategoryBetween(any(), any())).thenReturn(List.of());
    when(transactions.sumSpendBetween(any(), any())).thenReturn(BigDecimal.ZERO);
    when(transactions.sumIncomeBetween(any(), any())).thenReturn(BigDecimal.ZERO);
  }

  private static Budget line(String category, String amount) {
    return new Budget(category, new BigDecimal(amount));
  }

  private static BudgetExtra extra(String label, String amount, String category) {
    return new BudgetExtra(PAST.atDay(1), label, new BigDecimal(amount), category);
  }

  private void givenSpend(String category, String amount) {
    when(transactions.spendByCategoryBetween(any(), any()))
        .thenReturn(List.of(new CategoryTotal(category, new BigDecimal(amount), 1)));
  }

  private static CategoryLine find(BudgetMonth view, String category) {
    return view.categories().stream()
        .filter(c -> c.category().equals(category))
        .findFirst()
        .orElseThrow();
  }

  // ---------- the basic question ----------

  @Test
  void aCategoryReportsWhatIsLeftOfIt() {
    when(budgets.findByActiveTrueOrderBySortOrderAscCategoryAsc())
        .thenReturn(List.of(line("Groceries", "600")));
    givenSpend("Groceries", "-410.55");
    when(transactions.sumSpendBetween(any(), any())).thenReturn(new BigDecimal("-410.55"));

    CategoryLine groceries = find(service.month(PAST), "Groceries");

    assertThat(groceries.spent()).isEqualByComparingTo("410.55");
    assertThat(groceries.left()).isEqualByComparingTo("189.45");
    assertThat(groceries.percentUsed()).isEqualTo(68);
  }

  @Test
  void goingOverShowsHowFarOverRatherThanClampingAtZero() {
    // "How far over" is the number that changes behaviour. Clamping at zero hides it.
    when(budgets.findByActiveTrueOrderBySortOrderAscCategoryAsc())
        .thenReturn(List.of(line("Dining", "200")));
    givenSpend("Dining", "-260");
    when(transactions.sumSpendBetween(any(), any())).thenReturn(new BigDecimal("-260"));

    BudgetMonth view = service.month(PAST);

    assertThat(find(view, "Dining").left()).isEqualByComparingTo("-60");
    assertThat(find(view, "Dining").pace()).isEqualTo("EXCEEDED");
    assertThat(view.leftToSpend()).isEqualByComparingTo("-60");
  }

  // ---------- the whole reason extras exist ----------

  @Test
  void anExtraTaggedToACategoryRaisesThatCategoryForThisMonthOnly() {
    // A $400 flight in Travel should read as an $800 allowance this month, not as $400 over on a
    // $400 budget -- and next month must go straight back to $400.
    when(budgets.findByActiveTrueOrderBySortOrderAscCategoryAsc())
        .thenReturn(List.of(line("Travel", "400")));
    when(extras.findByMonthOrderByIdAsc(PAST.atDay(1)))
        .thenReturn(List.of(extra("Flights to Denver", "-400", "Travel")));
    givenSpend("Travel", "-750");
    when(transactions.sumSpendBetween(any(), any())).thenReturn(new BigDecimal("-750"));

    CategoryLine travel = find(service.month(PAST), "Travel");

    assertThat(travel.budget()).isEqualByComparingTo("400");
    assertThat(travel.extra()).isEqualByComparingTo("400");
    assertThat(travel.planned()).isEqualByComparingTo("800");
    assertThat(travel.left()).isEqualByComparingTo("50");
    assertThat(travel.pace()).isNotEqualTo("EXCEEDED");
    assertThat(travel.extraLabels()).containsExactly("Flights to Denver");
  }

  @Test
  void anUntaggedExtraStillHasToBePaidFor() {
    when(budgets.findByActiveTrueOrderBySortOrderAscCategoryAsc())
        .thenReturn(List.of(line("Groceries", "600")));
    when(extras.findByMonthOrderByIdAsc(PAST.atDay(1)))
        .thenReturn(List.of(extra("New brakes", "-450", null)));

    BudgetMonth view = service.month(PAST);

    assertThat(view.planned()).isEqualByComparingTo("1050");
    assertThat(view.extraExpenses()).isEqualByComparingTo("450");
  }

  @Test
  void everyExtraIsCountedExactlyOnceEvenWhenItsCategoryHasNoBudgetLine() {
    // The easy arithmetic bug here is double-counting a tagged extra, or dropping one whose
    // category was never budgeted.
    when(budgets.findByActiveTrueOrderBySortOrderAscCategoryAsc())
        .thenReturn(List.of(line("Groceries", "600")));
    when(extras.findByMonthOrderByIdAsc(PAST.atDay(1)))
        .thenReturn(
            List.of(
                extra("Vet bill", "-300", "Pets"), // no budget line for Pets
                extra("Wedding gift", "-150", null),
                extra("Extra groceries for guests", "-100", "Groceries")));

    BudgetMonth view = service.month(PAST);

    // 600 recurring + 300 + 150 + 100 of one-offs, each counted once.
    assertThat(view.planned()).isEqualByComparingTo("1150");
    assertThat(view.extraExpenses()).isEqualByComparingTo("550");
    assertThat(find(view, "Groceries").planned()).isEqualByComparingTo("700");
  }

  @Test
  void oneOffMoneyInCountsAsIncomeNotAsALargerLimit() {
    // A refund for half the vacation does not entitle you to spend more on groceries.
    when(budgets.findByActiveTrueOrderBySortOrderAscCategoryAsc())
        .thenReturn(List.of(line("Travel", "400")));
    when(extras.findByMonthOrderByIdAsc(PAST.atDay(1)))
        .thenReturn(List.of(extra("Half of the trip, paid back", "600", "Travel")));
    when(transactions.sumIncomeBetween(any(), any())).thenReturn(new BigDecimal("5000"));

    BudgetMonth view = service.month(PAST);

    assertThat(view.extraIncome()).isEqualByComparingTo("600");
    assertThat(find(view, "Travel").planned()).isEqualByComparingTo("400");
    assertThat(view.planned()).isEqualByComparingTo("400");
  }

  // ---------- the leak ----------

  @Test
  void spendingOutsideEveryBudgetLineIsShownNotFoldedIntoATotal() {
    when(budgets.findByActiveTrueOrderBySortOrderAscCategoryAsc())
        .thenReturn(List.of(line("Groceries", "600")));
    when(transactions.spendByCategoryBetween(any(), any()))
        .thenReturn(
            List.of(
                new CategoryTotal("Groceries", new BigDecimal("-400"), 12),
                new CategoryTotal("Shopping", new BigDecimal("-320"), 9),
                new CategoryTotal(null, new BigDecimal("-88"), 3)));

    BudgetMonth view = service.month(PAST);

    assertThat(view.unbudgeted()).hasSize(2);
    assertThat(view.unbudgeted().get(0).category()).isEqualTo("Shopping");
    assertThat(view.unbudgeted().get(0).spent()).isEqualByComparingTo("320");
    // Uncategorized spending is unbudgeted too, and must not be quietly dropped.
    assertThat(view.unbudgeted()).anyMatch(u -> u.category() == null);
  }

  // ---------- income ----------

  @Test
  void expectedIncomeFallsBackToATrailingAverageOfWhatActuallyArrived() {
    // Three complete months of deposits, averaged. Better than a number typed in once, because it
    // cannot silently go stale.
    when(transactions.sumIncomeBetween(any(), any())).thenReturn(new BigDecimal("24000"));

    assertThat(service.expectedIncome(PAST)).isEqualByComparingTo("8000");
    assertThat(service.month(PAST).incomeIsEstimated()).isTrue();
  }

  @Test
  void aDeclaredIncomeOverridesTheAverage() {
    BudgetSettings row = BudgetSettings.initial();
    row.setExpectedMonthlyIncome(new BigDecimal("9500"));
    when(settings.findById((short) 1)).thenReturn(Optional.of(row));
    when(transactions.sumIncomeBetween(any(), any())).thenReturn(new BigDecimal("24000"));

    assertThat(service.expectedIncome(PAST)).isEqualByComparingTo("9500");
    assertThat(service.month(PAST).incomeIsEstimated()).isFalse();
  }

  @Test
  void projectedNetIsIncomeMinusEverythingPlanned() {
    BudgetSettings row = BudgetSettings.initial();
    row.setExpectedMonthlyIncome(new BigDecimal("8000"));
    when(settings.findById((short) 1)).thenReturn(Optional.of(row));
    when(budgets.findByActiveTrueOrderBySortOrderAscCategoryAsc())
        .thenReturn(List.of(line("Rent", "2725"), line("Groceries", "600")));
    when(extras.findByMonthOrderByIdAsc(PAST.atDay(1)))
        .thenReturn(List.of(extra("Vacation", "-1200", null)));

    BudgetMonth view = service.month(PAST);

    assertThat(view.planned()).isEqualByComparingTo("4525");
    assertThat(view.projectedNet()).isEqualByComparingTo("3475");
  }

  // ---------- pace ----------

  @Test
  void aFinishedMonthIsSimplyWithinOrExceededWithNoPaceRead() {
    when(budgets.findByActiveTrueOrderBySortOrderAscCategoryAsc())
        .thenReturn(List.of(line("Groceries", "600")));
    givenSpend("Groceries", "-100");

    BudgetMonth view = service.month(PAST);

    assertThat(view.currentMonth()).isFalse();
    assertThat(find(view, "Groceries").pace()).isEqualTo("WITHIN");
  }

  @Test
  void theCurrentMonthIsMeasuredAgainstHowFarThroughItWeAre() {
    // Being at 60% of groceries means something completely different on the 5th than the 25th, so
    // the current month carries a pace target rather than only a limit.
    when(budgets.findByActiveTrueOrderBySortOrderAscCategoryAsc())
        .thenReturn(List.of(line("Groceries", "600")));
    YearMonth current = YearMonth.now();

    BudgetMonth view = service.month(current);

    assertThat(view.currentMonth()).isTrue();
    assertThat(view.dayOfMonth()).isEqualTo(LocalDate.now().getDayOfMonth());

    BigDecimal expected =
        new BigDecimal("600")
            .multiply(BigDecimal.valueOf(view.dayOfMonth()))
            .divide(BigDecimal.valueOf(current.lengthOfMonth()), 2, java.math.RoundingMode.HALF_UP);
    assertThat(view.expectedSpentByNow()).isEqualByComparingTo(expected);
    // Nothing spent yet is under pace by definition, on any day of any month.
    assertThat(find(view, "Groceries").pace()).isEqualTo("UNDER");
  }

  @Test
  void goingPastTheLimitBeatsAnyPaceRead() {
    // Late in the month, "ahead of pace" and "over the limit" converge. Over always wins, because
    // it is the one that is unambiguously true.
    when(budgets.findByActiveTrueOrderBySortOrderAscCategoryAsc())
        .thenReturn(List.of(line("Groceries", "600")));
    when(transactions.spendByCategoryBetween(any(), any()))
        .thenReturn(List.of(new CategoryTotal("Groceries", new BigDecimal("-640"), 20)));

    assertThat(find(service.month(YearMonth.now()), "Groceries").pace()).isEqualTo("EXCEEDED");
  }

  // ---------- validation ----------

  @Test
  void twoBudgetsForOneCategoryIsRejected() {
    when(budgets.findByCategoryIgnoreCase("Groceries"))
        .thenReturn(Optional.of(line("Groceries", "600")));

    assertThatThrownBy(() -> service.create("Groceries", new BigDecimal("700"), "", 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("already has a budget line");
  }

  @Test
  void anExtraNeedsALabelSoTheNumberExplainsItselfLater() {
    assertThatThrownBy(() -> service.addExtra(PAST, "  ", new BigDecimal("-400"), null, ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("label is required");
  }

  @Test
  void anExtraOfZeroIsRejected() {
    assertThatThrownBy(() -> service.addExtra(PAST, "Nothing", BigDecimal.ZERO, null, ""))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void anExtraIsAlwaysStoredAgainstTheFirstOfItsMonth() {
    BudgetExtra saved =
        service.addExtra(YearMonth.of(2026, 9), "Vacation", new BigDecimal("-1200"), "Travel", "");
    assertThat(saved.getMonth()).isEqualTo(LocalDate.of(2026, 9, 1));
  }
}
