package dev.grindtrack.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Something that only happens in one month.
 *
 * <p>A vacation, a car repair, a wedding gift, a bonus. Real money that has to change what is left
 * this month without becoming part of the recurring plan.
 *
 * <p>{@code amount} is signed the same way transactions are: negative is money out, positive is
 * one-off money in — a bonus, a refund, the half of a holiday someone is paying back. Keeping the
 * convention identical means nothing downstream has to remember which way round this table runs.
 *
 * <p>{@code category} is optional and does real work when set: a $400 flight tagged Travel counts
 * against the Travel line for that month, so the category shows an $800 allowance in July rather
 * than looking $400 over on a $400 budget. Left null, it is a standalone item that moves only the
 * month total.
 */
@Entity
@Table(name = "finance_budget_extras")
public class BudgetExtra {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** Always the first of the month; {@link #firstOf} is the only way one gets set. */
  @Column(nullable = false)
  private LocalDate month;

  @Column(nullable = false)
  private String label;

  @Column(nullable = false)
  private BigDecimal amount;

  @Column private String category;

  @Column(nullable = false)
  private String note = "";

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt = OffsetDateTime.now();

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt = OffsetDateTime.now();

  protected BudgetExtra() {}

  public BudgetExtra(LocalDate anyDayInMonth, String label, BigDecimal amount, String category) {
    this.month = firstOf(anyDayInMonth);
    this.label = label.trim();
    this.amount = amount;
    this.category = blankToNull(category);
  }

  /** Normalizes any date to the first of its month, which is how the column is constrained. */
  public static LocalDate firstOf(LocalDate date) {
    return date.withDayOfMonth(1);
  }

  public void update(
      LocalDate anyDayInMonth, String label, BigDecimal amount, String category, String note) {
    this.month = firstOf(anyDayInMonth);
    this.label = label.trim();
    this.amount = amount;
    this.category = blankToNull(category);
    this.note = note == null ? "" : note;
    this.updatedAt = OffsetDateTime.now();
  }

  /** True for money going out, which is the common case and the one that reduces what is left. */
  public boolean isExpense() {
    return amount.signum() < 0;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  public Long getId() {
    return id;
  }

  public LocalDate getMonth() {
    return month;
  }

  public String getLabel() {
    return label;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getCategory() {
    return category;
  }

  public String getNote() {
    return note;
  }
}
