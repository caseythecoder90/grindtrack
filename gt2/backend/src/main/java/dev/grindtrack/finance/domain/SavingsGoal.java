package dev.grindtrack.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * A number worth saving toward, with the reasoning attached.
 *
 * <p>Progress is not stored here. It is summed at read time from the accounts flagged {@code
 * countsTowardSavings}, so a goal can never drift out of step with the balances behind it.
 *
 * <p>{@code note} matters more than it looks: a bare "$230,000" is meaningless in a year, whereas
 * "$100k down on a $500k house, ~$15k closing, ~$12k move-in, $100k retained" still explains itself
 * and can be re-checked when the house price changes.
 */
@Entity
@Table(name = "finance_savings_goals")
public class SavingsGoal {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String name;

  @Column(name = "target_amount", nullable = false)
  private BigDecimal targetAmount;

  @Column(name = "target_date")
  private LocalDate targetDate;

  @Column(nullable = false)
  private String note = "";

  @Column(nullable = false)
  private boolean active = true;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder = 0;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt = OffsetDateTime.now();

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt = OffsetDateTime.now();

  protected SavingsGoal() {}

  public SavingsGoal(String name, BigDecimal targetAmount) {
    this.name = name;
    this.targetAmount = targetAmount;
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public BigDecimal getTargetAmount() {
    return targetAmount;
  }

  public LocalDate getTargetDate() {
    return targetDate;
  }

  public String getNote() {
    return note;
  }

  public boolean isActive() {
    return active;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  /**
   * How far along this goal is, 0-100 to one decimal.
   *
   * <p>Arithmetic over the goal's own target, so it lives on the goal. It sat on {@code
   * FinanceService} in two overloads, one of which nothing called — which is what happens to a
   * calculation parked in a service: it grows a convenience variant instead of a caller.
   *
   * @param savingsBalance the summed balance of the accounts that count toward savings; passed in
   *     because rendering a list of goals otherwise re-queries the same sum once per goal
   */
  public BigDecimal progressPercent(BigDecimal savingsBalance) {
    if (targetAmount == null || targetAmount.signum() <= 0) {
      return BigDecimal.ZERO;
    }
    return savingsBalance
        .multiply(BigDecimal.valueOf(100))
        .divide(targetAmount, 1, RoundingMode.HALF_UP);
  }

  /** What is still to be found. Never negative: a goal met is met, not over-met. */
  public BigDecimal remaining(BigDecimal savingsBalance) {
    return targetAmount.subtract(savingsBalance).max(BigDecimal.ZERO);
  }

  public void update(
      String name,
      BigDecimal targetAmount,
      LocalDate targetDate,
      String note,
      boolean active,
      int sortOrder) {
    this.name = name;
    this.targetAmount = targetAmount;
    this.targetDate = targetDate;
    this.note = note == null ? "" : note;
    this.active = active;
    this.sortOrder = sortOrder;
    this.updatedAt = OffsetDateTime.now();
  }
}
