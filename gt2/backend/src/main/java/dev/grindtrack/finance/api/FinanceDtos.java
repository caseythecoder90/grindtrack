package dev.grindtrack.finance.api;

import dev.grindtrack.finance.domain.Account;
import dev.grindtrack.finance.domain.SavingsGoal;
import dev.grindtrack.finance.domain.Transaction;
import java.math.BigDecimal;
import java.util.List;

/**
 * Wire shapes for the finance feature. Mirrors {@code TodoDtos} — nested records, static from().
 */
public final class FinanceDtos {

  private FinanceDtos() {}

  // ---------------------------------------------------------------- accounts

  public record AccountRequest(
      String name,
      String institution,
      String accountType,
      String last4,
      Boolean countsTowardSavings,
      Boolean active,
      Integer sortOrder) {}

  public record BalanceRequest(BigDecimal balance, String asOf) {}

  public record AccountResponse(
      Long id,
      String name,
      String institution,
      String accountType,
      String last4,
      BigDecimal currentBalance,
      String balanceAsOf,
      boolean countsTowardSavings,
      boolean active,
      int sortOrder,
      long transactionCount) {

    public static AccountResponse from(Account a, long transactionCount) {
      return new AccountResponse(
          a.getId(),
          a.getName(),
          a.getInstitution().name(),
          a.getAccountType().name(),
          a.getLast4(),
          a.getCurrentBalance(),
          a.getBalanceAsOf() == null ? null : a.getBalanceAsOf().toString(),
          a.isCountsTowardSavings(),
          a.isActive(),
          a.getSortOrder(),
          transactionCount);
    }
  }

  // ------------------------------------------------------------ transactions

  public record TransactionRequest(
      Long accountId,
      String postedDate,
      String transactionDate,
      BigDecimal amount,
      String description,
      String txnType,
      String notes) {}

  public record CategorizeRequest(String category) {}

  public record ReclassifyRequest(String txnType) {}

  public record TransactionResponse(
      Long id,
      Long accountId,
      String postedDate,
      String transactionDate,
      BigDecimal amount,
      String description,
      String merchant,
      String txnType,
      String category,
      String issuerCategory,
      String categorySource,
      boolean pending,
      String notes) {

    public static TransactionResponse from(Transaction t) {
      return new TransactionResponse(
          t.getId(),
          t.getAccountId(),
          t.getPostedDate().toString(),
          t.getTransactionDate() == null ? null : t.getTransactionDate().toString(),
          t.getAmount(),
          t.getRawDescription(),
          t.getMerchant(),
          t.getTxnType().name(),
          t.getCategory(),
          t.getIssuerCategory(),
          t.getCategorySource().name(),
          t.isPending(),
          t.getNotes());
    }
  }

  // ------------------------------------------------------------------ goals

  public record GoalRequest(
      String name,
      BigDecimal targetAmount,
      String targetDate,
      String note,
      Boolean active,
      Integer sortOrder) {}

  public record GoalResponse(
      Long id,
      String name,
      BigDecimal targetAmount,
      String targetDate,
      String note,
      boolean active,
      int sortOrder,
      BigDecimal currentAmount,
      BigDecimal remaining,
      BigDecimal progressPercent) {

    public static GoalResponse from(SavingsGoal g, BigDecimal current, BigDecimal percent) {
      BigDecimal remaining = g.getTargetAmount().subtract(current).max(BigDecimal.ZERO);
      return new GoalResponse(
          g.getId(),
          g.getName(),
          g.getTargetAmount(),
          g.getTargetDate() == null ? null : g.getTargetDate().toString(),
          g.getNote(),
          g.isActive(),
          g.getSortOrder(),
          current,
          remaining,
          percent);
    }
  }

  /** Everything the dashboard card needs in one call. */
  public record SummaryResponse(
      BigDecimal savingsBalance,
      BigDecimal netWorth,
      List<GoalResponse> goals,
      List<AccountResponse> accounts,
      int uncategorizedCount) {}
}
