package dev.grindtrack.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * What one category is expected to cost in a normal month.
 *
 * <p>Deliberately the recurring part only. Anything that happens in exactly one month — a vacation,
 * a car repair — is a {@link BudgetExtra} instead, so that editing this month's one-offs can never
 * leave next month believing it owes for a holiday that already happened.
 *
 * <p>{@code monthlyAmount} is positive. Transactions store spending as negative because they are
 * movements; a budget is a limit, and a limit of minus six hundred reads as nonsense on a form. The
 * one place the two conventions meet is the month view, which flips once and says so.
 */
@Entity
@Table(name = "finance_budgets")
public class Budget {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String category;

  @Column(name = "monthly_amount", nullable = false)
  private BigDecimal monthlyAmount;

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

  protected Budget() {}

  public Budget(String category, BigDecimal monthlyAmount) {
    this.category = category.trim();
    this.monthlyAmount = monthlyAmount.abs();
  }

  public void update(
      String category, BigDecimal monthlyAmount, String note, boolean active, int sortOrder) {
    this.category = category.trim();
    this.monthlyAmount = monthlyAmount.abs();
    this.note = note == null ? "" : note;
    this.active = active;
    this.sortOrder = sortOrder;
    this.updatedAt = OffsetDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public String getCategory() {
    return category;
  }

  public BigDecimal getMonthlyAmount() {
    return monthlyAmount;
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
}
