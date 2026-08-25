package dev.grindtrack.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * One real account at one institution: a checking account, a savings account, a credit card, or a
 * student loan.
 *
 * <p>Balances are stored signed by {@link AccountType} convention — cash accounts positive, cards
 * and loans negative when money is owed — so {@code SUM(current_balance)} is net worth without any
 * per-type branching.
 *
 * <p>In phase 1 the balance is typed in by hand; from phase 2 it is derived from imported
 * statements. {@code balanceAsOf} exists so a stale number is visibly stale rather than quietly
 * wrong.
 */
@Entity
@Table(name = "finance_accounts")
public class Account {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Institution institution;

  @Enumerated(EnumType.STRING)
  @Column(name = "account_type", nullable = false)
  private AccountType accountType;

  /** Text, not a number — leading zeros are real (card 0948). */
  @Column private String last4;

  @Column(name = "current_balance", nullable = false)
  private BigDecimal currentBalance = BigDecimal.ZERO;

  @Column(name = "balance_as_of")
  private LocalDate balanceAsOf;

  @Column(name = "counts_toward_savings", nullable = false)
  private boolean countsTowardSavings = false;

  @Column(nullable = false)
  private boolean active = true;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder = 0;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt = OffsetDateTime.now();

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt = OffsetDateTime.now();

  protected Account() {}

  public Account(String name, Institution institution, AccountType accountType) {
    this.name = name;
    this.institution = institution;
    this.accountType = accountType;
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public Institution getInstitution() {
    return institution;
  }

  public AccountType getAccountType() {
    return accountType;
  }

  public String getLast4() {
    return last4;
  }

  public BigDecimal getCurrentBalance() {
    return currentBalance;
  }

  public LocalDate getBalanceAsOf() {
    return balanceAsOf;
  }

  public boolean isCountsTowardSavings() {
    return countsTowardSavings;
  }

  public boolean isActive() {
    return active;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  /**
   * Records a new balance reading. Kept separate from {@link #update} because a balance refresh
   * happens on every import while the descriptive fields almost never change.
   */
  public void recordBalance(BigDecimal balance, LocalDate asOf) {
    this.currentBalance = balance == null ? BigDecimal.ZERO : balance;
    this.balanceAsOf = asOf;
    this.updatedAt = OffsetDateTime.now();
  }

  public void update(
      String name,
      Institution institution,
      AccountType accountType,
      String last4,
      boolean countsTowardSavings,
      boolean active,
      int sortOrder) {
    this.name = name;
    this.institution = institution;
    this.accountType = accountType;
    this.last4 = last4 == null || last4.isBlank() ? null : last4.trim();
    this.countsTowardSavings = countsTowardSavings;
    this.active = active;
    this.sortOrder = sortOrder;
    this.updatedAt = OffsetDateTime.now();
  }
}
