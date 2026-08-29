package dev.grindtrack.finance.api;

import dev.grindtrack.finance.domain.Budget;
import dev.grindtrack.finance.domain.BudgetExtra;
import java.math.BigDecimal;
import java.time.YearMonth;

/** Request/response shapes for the budget API. */
public final class BudgetDtos {

  private BudgetDtos() {}

  // -------------------------------------------------------------- the plan

  public record LineRequest(
      String category, BigDecimal monthlyAmount, String note, Boolean active, Integer sortOrder) {}

  public record LineResponse(
      Long id,
      String category,
      BigDecimal monthlyAmount,
      String note,
      boolean active,
      int sortOrder) {

    public static LineResponse from(Budget b) {
      return new LineResponse(
          b.getId(),
          b.getCategory(),
          b.getMonthlyAmount(),
          b.getNote(),
          b.isActive(),
          b.getSortOrder());
    }
  }

  // ------------------------------------------------------- this month only

  /**
   * @param amount negative for a one-off cost, positive for one-off money in
   * @param category optional; when set, the cost counts against that category for the month
   */
  public record ExtraRequest(
      String month, String label, BigDecimal amount, String category, String note) {}

  public record ExtraResponse(
      Long id, String month, String label, BigDecimal amount, String category, String note) {

    public static ExtraResponse from(BudgetExtra e) {
      return new ExtraResponse(
          e.getId(),
          YearMonth.from(e.getMonth()).toString(),
          e.getLabel(),
          e.getAmount(),
          e.getCategory(),
          e.getNote());
    }
  }

  // ---------------------------------------------------------------- income

  public record IncomeRequest(BigDecimal expectedMonthlyIncome) {}

  /**
   * @param expectedMonthlyIncome empty string when no override is set, which the frontend puts
   *     straight into a text input
   * @param estimated true when the figure shown is the trailing average of real deposits rather
   *     than a number that was typed in
   */
  public record IncomeResponse(String expectedMonthlyIncome, boolean estimated) {

    public static IncomeResponse of(BigDecimal saved) {
      return new IncomeResponse(saved == null ? "" : saved.toPlainString(), saved == null);
    }
  }
}
