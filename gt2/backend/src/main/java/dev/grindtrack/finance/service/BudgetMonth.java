package dev.grindtrack.finance.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * One month, fully reconciled: the plan, what actually happened, and the gap.
 *
 * <p>A file of its own for the same reason as {@code RelationshipSummary} and {@code Stats} — four
 * record declarations sat between {@link BudgetService}'s constructor and its first method, so the
 * class read as a bag of shapes before it read as a service.
 *
 * <p>Every figure here is positive. Spending is stored negative (money out) and flipped exactly
 * once, on the way into this record, so nothing downstream has to know the sign convention.
 *
 * @param leftToSpend planned minus spent. Negative means over budget, and it is shown that way
 *     rather than clamped at zero, because "how far over" is the number that changes behaviour
 * @param expectedSpentByNow where a perfectly even month would be today, for the pace read
 * @param incomeIsEstimated true when income was inferred from a trailing average rather than set
 */
public record BudgetMonth(
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
    List<ExtraLine> extras) {

  /** Where one category stands this month. */
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

  /** A one-off cost or windfall attached to this month only. */
  public record ExtraLine(
      Long id, String month, String label, BigDecimal amount, String category, String note) {}
}
