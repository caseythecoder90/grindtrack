package dev.grindtrack.finance.api;

import dev.grindtrack.finance.domain.Account;
import dev.grindtrack.finance.domain.CategoryRule;
import dev.grindtrack.finance.domain.SavingsGoal;
import dev.grindtrack.finance.domain.Transaction;
import dev.grindtrack.finance.service.FinanceService.TransactionPage;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Wire shapes for accounts, transactions, savings goals and category rules.
 *
 * <p>Nested records with a {@code static from()} each, like every other {@code <Feature>Dtos} here.
 * The budget and statement-import surfaces have their own files rather than swelling this one.
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

    /**
     * @param savings the summed savings balance. Read once by the caller and handed down: every
     *     goal needs it twice, and re-querying it per goal made the dashboard issue the same SUM
     *     four or five times per load.
     */
    public static GoalResponse from(SavingsGoal g, BigDecimal savings) {
      return new GoalResponse(
          g.getId(),
          g.getName(),
          g.getTargetAmount(),
          g.getTargetDate() == null ? null : g.getTargetDate().toString(),
          g.getNote(),
          g.isActive(),
          g.getSortOrder(),
          savings,
          g.remaining(savings),
          g.progressPercent(savings));
    }
  }

  // ------------------------------------------------------------------ rules

  public record RuleRequest(
      String pattern, String matchType, String category, Integer priority, Boolean active) {}

  /** Applying a category by hand, optionally teaching the app to do it next time. */
  public record CategorizeAndLearnRequest(String category, Boolean createRule) {}

  public record RuleResponse(
      Long id,
      String pattern,
      String matchType,
      String category,
      int priority,
      boolean active,
      int hitCount,
      String lastApplied) {

    public static RuleResponse from(CategoryRule r) {
      return new RuleResponse(
          r.getId(),
          r.getPattern(),
          r.getMatchType().name(),
          r.getCategory(),
          r.getPriority(),
          r.isActive(),
          r.getHitCount(),
          r.getLastApplied() == null ? null : r.getLastApplied().toString());
    }
  }

  /**
   * One page of transactions.
   *
   * <p>This was assembled as a {@code LinkedHashMap} in the controller, which meant the paging
   * contract — the part a client has to get right to iterate — was five {@code put} calls with no
   * type behind them.
   */
  public record TransactionPageResponse(
      List<TransactionResponse> items, int page, int size, long totalElements, int totalPages) {

    public static TransactionPageResponse from(TransactionPage found) {
      return new TransactionPageResponse(
          found.items().stream().map(TransactionResponse::from).toList(),
          found.page(),
          found.size(),
          found.totalElements(),
          found.totalPages());
    }
  }

  /**
   * The review inbox's answer: the row as filed, plus what was learned from it.
   *
   * @param rule the rule that was created, or null when none was asked for or one already existed
   * @param ruleExisted true when a rule for that merchant was already on file, so nothing new was
   *     created and nothing is wrong
   */
  public record CategorizeAndLearnResponse(
      TransactionResponse transaction, RuleResponse rule, boolean ruleExisted) {

    public static CategorizeAndLearnResponse of(Transaction saved) {
      return new CategorizeAndLearnResponse(TransactionResponse.from(saved), null, false);
    }

    public static CategorizeAndLearnResponse of(Transaction saved, Optional<CategoryRule> rule) {
      return new CategorizeAndLearnResponse(
          TransactionResponse.from(saved),
          rule.map(RuleResponse::from).orElse(null),
          rule.isEmpty());
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
