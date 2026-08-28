package dev.grindtrack.finance.service;

import dev.grindtrack.finance.domain.Budget;
import dev.grindtrack.finance.domain.BudgetExtra;
import dev.grindtrack.finance.domain.BudgetExtraRepository;
import dev.grindtrack.finance.domain.BudgetRepository;
import dev.grindtrack.finance.domain.BudgetSettings;
import dev.grindtrack.finance.domain.BudgetSettingsRepository;
import dev.grindtrack.finance.domain.CategoryTotal;
import dev.grindtrack.finance.domain.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The monthly budget, and the one question it exists to answer: what is left.
 *
 * <p>Two kinds of number go into that, and they are kept apart on purpose. {@link Budget} is the
 * recurring plan — rent every month, groceries every month. {@link BudgetExtra} is what only
 * happens this month — a vacation, a car repair, a bonus. Conflating them is what makes most budget
 * tools annoying: one holiday, and every following month believes it owes for it.
 *
 * <p>Everything here reads spending through the same queries the rollups use, so transfers and card
 * payments are already excluded. A budget that counted a credit-card payment as spending would
 * report roughly double, on top of the money the purchase already cost.
 */
@Service
public class BudgetService {

  /** How far back to look when income has to be inferred rather than declared. */
  private static final int INCOME_LOOKBACK_MONTHS = 3;

  /**
   * How far off the day-of-month pace counts as off-pace rather than noise. Spending is lumpy —
   * rent lands on the first — so a tight band would show a scary red badge every month on the 2nd.
   */
  private static final BigDecimal PACE_TOLERANCE = new BigDecimal("0.15");

  private static final DateTimeFormatter MONTH_KEY = DateTimeFormatter.ofPattern("yyyy-MM");
  private static final DateTimeFormatter MONTH_LABEL =
      DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US);

  private final BudgetRepository budgets;
  private final BudgetExtraRepository extras;
  private final BudgetSettingsRepository settings;
  private final TransactionRepository transactions;

  public BudgetService(
      BudgetRepository budgets,
      BudgetExtraRepository extras,
      BudgetSettingsRepository settings,
      TransactionRepository transactions) {
    this.budgets = budgets;
    this.extras = extras;
    this.settings = settings;
    this.transactions = transactions;
  }

  /**
   * Where one category stands this month. All figures positive — spending is flipped once, here.
   */
  public record CategoryLine(
      Long budgetId,
      String category,
      BigDecimal budget,
      BigDecimal extra,
      BigDecimal planned,
      BigDecimal spent,
      BigDecimal left,
      int percentUsed,
      String pace,
      List<String> extraLabels) {}

  /** Money that went somewhere with no budget line. The honest leak indicator. */
  public record UnbudgetedLine(String category, BigDecimal spent, long count) {}

  public record ExtraLine(
      Long id, String month, String label, BigDecimal amount, String category, String note) {}

  /**
   * One month, fully reconciled.
   *
   * @param leftToSpend planned minus spent. Negative means over budget, and it is shown that way
   *     rather than clamped at zero, because "how far over" is the number that changes behaviour
   * @param expectedSpentByNow where a perfectly even month would be today, for the pace read
   * @param incomeIsEstimated true when income was inferred from a trailing average rather than set
   */
  public record MonthView(
      String month,
      String monthLabel,
      int dayOfMonth,
      int daysInMonth,
      boolean currentMonth,
      BigDecimal expectedIncome,
      boolean incomeIsEstimated,
      BigDecimal incomeSoFar,
      BigDecimal planned,
      BigDecimal spent,
      BigDecimal leftToSpend,
      BigDecimal projectedNet,
      BigDecimal expectedSpentByNow,
      BigDecimal extraExpenses,
      BigDecimal extraIncome,
      List<CategoryLine> categories,
      List<UnbudgetedLine> unbudgeted,
      List<ExtraLine> extras) {}

  // ------------------------------------------------------------- the month

  public MonthView month(YearMonth target) {
    LocalDate first = target.atDay(1);
    LocalDate last = target.atEndOfMonth();
    YearMonth now = YearMonth.now();
    boolean isCurrent = target.equals(now);
    int dayOfMonth = isCurrent ? LocalDate.now().getDayOfMonth() : target.lengthOfMonth();

    List<Budget> lines = budgets.findByActiveTrueOrderBySortOrderAscCategoryAsc();
    List<BudgetExtra> monthExtras = extras.findByMonthOrderByIdAsc(first);

    // Actual spending, by category, already excluding transfers, card payments and pending rows.
    Map<String, CategoryTotal> spentByCategory = new HashMap<>();
    for (CategoryTotal total : transactions.spendByCategoryBetween(first, last)) {
      spentByCategory.put(key(total.label()), total);
    }

    // Extras split three ways: allowance added to a category, standalone costs, and money in.
    Map<String, BigDecimal> extraByCategory = new HashMap<>();
    Map<String, List<String>> extraLabelsByCategory = new HashMap<>();
    BigDecimal extraExpenses = BigDecimal.ZERO;
    BigDecimal extraIncome = BigDecimal.ZERO;

    for (BudgetExtra extra : monthExtras) {
      if (extra.isExpense()) {
        BigDecimal cost = extra.getAmount().abs();
        extraExpenses = extraExpenses.add(cost);
        if (extra.getCategory() != null) {
          extraByCategory.merge(key(extra.getCategory()), cost, BigDecimal::add);
          extraLabelsByCategory
              .computeIfAbsent(key(extra.getCategory()), k -> new ArrayList<>())
              .add(extra.getLabel());
        }
      } else {
        // One-off money in is income, wherever it was tagged. A refund does not raise a limit.
        extraIncome = extraIncome.add(extra.getAmount());
      }
    }

    BigDecimal totalBudgeted = BigDecimal.ZERO;
    BigDecimal totalPlanned = BigDecimal.ZERO;
    List<CategoryLine> categories = new ArrayList<>();

    for (Budget budget : lines) {
      String k = key(budget.getCategory());
      BigDecimal extra = extraByCategory.getOrDefault(k, BigDecimal.ZERO);
      BigDecimal planned = budget.getMonthlyAmount().add(extra);
      BigDecimal spent = positive(spentByCategory.get(k));

      totalBudgeted = totalBudgeted.add(budget.getMonthlyAmount());
      totalPlanned = totalPlanned.add(planned);

      categories.add(
          new CategoryLine(
              budget.getId(),
              budget.getCategory(),
              budget.getMonthlyAmount(),
              extra,
              planned,
              spent,
              planned.subtract(spent),
              percent(spent, planned),
              pace(spent, planned, dayOfMonth, target.lengthOfMonth(), isCurrent),
              extraLabelsByCategory.getOrDefault(k, List.of())));
    }

    // Every extra cost has to be paid for exactly once, whether or not it was tagged to a line.
    // Tagged ones are already inside totalPlanned via the category lines, so adding the full
    // extraExpenses to totalBudgeted -- rather than to totalPlanned -- counts each one exactly
    // once, including extras tagged to a category that has no budget line at all.
    BigDecimal planned = totalBudgeted.add(extraExpenses);

    // Anything spent outside a budget line. Shown, never folded into a total silently.
    List<UnbudgetedLine> unbudgeted = new ArrayList<>();
    for (CategoryTotal total : spentByCategory.values()) {
      boolean budgeted =
          lines.stream().anyMatch(b -> key(b.getCategory()).equals(key(total.label())));
      if (!budgeted) {
        unbudgeted.add(
            new UnbudgetedLine(
                total.label() == null ? null : total.label(), total.total().abs(), total.count()));
      }
    }
    unbudgeted.sort((a, b) -> b.spent().compareTo(a.spent()));

    BigDecimal spent = transactions.sumSpendBetween(first, last).abs();
    BigDecimal incomeSoFar = transactions.sumIncomeBetween(first, last);
    BigDecimal baseIncome = expectedIncome(target);
    BigDecimal expectedIncome = baseIncome.add(extraIncome);

    BigDecimal expectedByNow =
        isCurrent
            ? planned
                .multiply(BigDecimal.valueOf(dayOfMonth))
                .divide(BigDecimal.valueOf(target.lengthOfMonth()), 2, RoundingMode.HALF_UP)
            : planned;

    return new MonthView(
        target.format(MONTH_KEY),
        first.format(MONTH_LABEL),
        dayOfMonth,
        target.lengthOfMonth(),
        isCurrent,
        expectedIncome,
        settingsRow().getExpectedMonthlyIncome() == null,
        incomeSoFar,
        planned,
        spent,
        planned.subtract(spent),
        expectedIncome.subtract(planned),
        expectedByNow,
        extraExpenses,
        extraIncome,
        categories,
        unbudgeted,
        monthExtras.stream().map(BudgetService::toLine).toList());
  }

  /**
   * What to expect to come in this month.
   *
   * <p>Declared figure if there is one, otherwise the average of what actually arrived over the
   * last three complete months. The average is the better default: it is derived from real
   * deposits, and it cannot silently go stale the way a number typed in once does. The current
   * month is excluded from it because a month in progress is always short.
   */
  public BigDecimal expectedIncome(YearMonth target) {
    BigDecimal declared = settingsRow().getExpectedMonthlyIncome();
    if (declared != null) {
      return declared;
    }
    YearMonth lastComplete = target.minusMonths(1);
    LocalDate from = lastComplete.minusMonths(INCOME_LOOKBACK_MONTHS - 1L).atDay(1);
    LocalDate to = lastComplete.atEndOfMonth();
    BigDecimal total = transactions.sumIncomeBetween(from, to);
    if (total.signum() <= 0) {
      return BigDecimal.ZERO;
    }
    return total.divide(BigDecimal.valueOf(INCOME_LOOKBACK_MONTHS), 2, RoundingMode.HALF_UP);
  }

  // ------------------------------------------------------------ budget lines

  public List<Budget> list(boolean includeInactive) {
    return includeInactive
        ? budgets.findAllByOrderBySortOrderAscCategoryAsc()
        : budgets.findByActiveTrueOrderBySortOrderAscCategoryAsc();
  }

  @Transactional
  public Budget create(String category, BigDecimal monthlyAmount, String note, int sortOrder) {
    if (category == null || category.isBlank()) {
      throw new IllegalArgumentException("a category is required");
    }
    if (monthlyAmount == null || monthlyAmount.signum() < 0) {
      throw new IllegalArgumentException("a monthly amount of zero or more is required");
    }
    budgets
        .findByCategoryIgnoreCase(category.trim())
        .ifPresent(
            existing -> {
              throw new IllegalArgumentException(
                  existing.getCategory() + " already has a budget line");
            });
    Budget budget = new Budget(category, monthlyAmount);
    budget.update(category, monthlyAmount, note, true, sortOrder);
    return budgets.save(budget);
  }

  @Transactional
  public Budget update(
      Long id,
      String category,
      BigDecimal monthlyAmount,
      String note,
      boolean active,
      int sortOrder) {
    Budget budget =
        budgets.findById(id).orElseThrow(() -> new NoSuchElementException("budget " + id));
    if (monthlyAmount == null || monthlyAmount.signum() < 0) {
      throw new IllegalArgumentException("a monthly amount of zero or more is required");
    }
    budget.update(category, monthlyAmount, note, active, sortOrder);
    return budgets.save(budget);
  }

  @Transactional
  public void delete(Long id) {
    budgets.deleteById(id);
  }

  // ----------------------------------------------------------------- extras

  public List<BudgetExtra> extrasFrom(YearMonth from) {
    return extras.findByMonthGreaterThanEqualOrderByMonthAscIdAsc(from.atDay(1));
  }

  @Transactional
  public BudgetExtra addExtra(
      YearMonth month, String label, BigDecimal amount, String category, String note) {
    if (label == null || label.isBlank()) {
      throw new IllegalArgumentException(
          "a label is required, so the number explains itself later");
    }
    if (amount == null || amount.signum() == 0) {
      throw new IllegalArgumentException(
          "an amount is required — negative for a cost, positive for money in");
    }
    BudgetExtra extra = new BudgetExtra(month.atDay(1), label, amount, category);
    extra.update(month.atDay(1), label, amount, category, note);
    return extras.save(extra);
  }

  @Transactional
  public BudgetExtra updateExtra(
      Long id, YearMonth month, String label, BigDecimal amount, String category, String note) {
    BudgetExtra extra =
        extras.findById(id).orElseThrow(() -> new NoSuchElementException("extra " + id));
    if (amount == null || amount.signum() == 0) {
      throw new IllegalArgumentException("an amount is required");
    }
    extra.update(month.atDay(1), label, amount, category, note);
    return extras.save(extra);
  }

  @Transactional
  public void deleteExtra(Long id) {
    extras.deleteById(id);
  }

  // --------------------------------------------------------------- settings

  public BudgetSettings settingsRow() {
    return settings.findById((short) 1).orElseGet(() -> settings.save(BudgetSettings.initial()));
  }

  @Transactional
  public BudgetSettings setExpectedIncome(BigDecimal value) {
    BudgetSettings row = settingsRow();
    row.setExpectedMonthlyIncome(value);
    return settings.save(row);
  }

  // -------------------------------------------------------------- internals

  private static ExtraLine toLine(BudgetExtra e) {
    return new ExtraLine(
        e.getId(),
        e.getMonth().format(MONTH_KEY),
        e.getLabel(),
        e.getAmount(),
        e.getCategory(),
        e.getNote());
  }

  /** Null-safe, case-insensitive category key. Null is a real category here: "uncategorized". */
  private static String key(String category) {
    return category == null ? " uncategorized" : category.trim().toLowerCase(Locale.ROOT);
  }

  private static BigDecimal positive(CategoryTotal total) {
    return total == null ? BigDecimal.ZERO : total.total().abs();
  }

  private static int percent(BigDecimal spent, BigDecimal planned) {
    if (planned.signum() <= 0) {
      return spent.signum() > 0 ? 100 : 0;
    }
    return spent
        .multiply(BigDecimal.valueOf(100))
        .divide(planned, 0, RoundingMode.HALF_UP)
        .intValue();
  }

  /**
   * Where this category sits against the calendar, not just against the limit.
   *
   * <p>Being at 60% of groceries matters entirely differently on the 5th than on the 25th, and the
   * limit alone cannot tell you which. Only the current month gets a pace read; a finished month is
   * simply over or not.
   */
  private static String pace(
      BigDecimal spent, BigDecimal planned, int day, int daysInMonth, boolean currentMonth) {
    if (spent.compareTo(planned) > 0) {
      return "EXCEEDED";
    }
    if (!currentMonth) {
      return "WITHIN";
    }
    if (planned.signum() <= 0) {
      return "WITHIN";
    }
    BigDecimal expected =
        planned
            .multiply(BigDecimal.valueOf(day))
            .divide(BigDecimal.valueOf(daysInMonth), 2, RoundingMode.HALF_UP);
    BigDecimal tolerance = expected.multiply(PACE_TOLERANCE);
    if (spent.compareTo(expected.add(tolerance)) > 0) {
      return "AHEAD_OF_PACE";
    }
    if (spent.compareTo(expected.subtract(tolerance)) < 0) {
      return "UNDER";
    }
    return "ON_TRACK";
  }

  /** Month keys for a picker, newest first, covering everything with a transaction in it. */
  public List<String> monthsWithActivity(int limit) {
    Map<String, String> seen = new LinkedHashMap<>();
    YearMonth cursor = YearMonth.now();
    for (int i = 0; i < limit; i++) {
      seen.put(cursor.format(MONTH_KEY), cursor.format(MONTH_KEY));
      cursor = cursor.minusMonths(1);
    }
    return List.copyOf(seen.keySet());
  }
}
