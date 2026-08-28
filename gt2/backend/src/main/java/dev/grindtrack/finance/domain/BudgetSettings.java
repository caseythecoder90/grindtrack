package dev.grindtrack.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * The handful of budget figures that are global rather than per-category. One row, always id 1.
 *
 * <p>{@code expectedMonthlyIncome} is null by default, and that is the better setting: a trailing
 * average of income that actually arrived beats a number typed in once and never revisited. The
 * override exists for what the data cannot know yet — a raise that has not landed, or a month with
 * a bonus in it.
 */
@Entity
@Table(name = "finance_budget_settings")
public class BudgetSettings {

  @Id private Short id = 1;

  @Column(name = "expected_monthly_income")
  private BigDecimal expectedMonthlyIncome;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt = OffsetDateTime.now();

  protected BudgetSettings() {}

  /**
   * The row as the migration seeds it. A safety net only — if this ever has to run, the seed insert
   * did not, and creating the row is better than every budget screen failing over a missing
   * default.
   */
  public static BudgetSettings initial() {
    return new BudgetSettings();
  }

  public void setExpectedMonthlyIncome(BigDecimal value) {
    this.expectedMonthlyIncome = value == null || value.signum() <= 0 ? null : value;
    this.updatedAt = OffsetDateTime.now();
  }

  public Short getId() {
    return id;
  }

  public BigDecimal getExpectedMonthlyIncome() {
    return expectedMonthlyIncome;
  }
}
