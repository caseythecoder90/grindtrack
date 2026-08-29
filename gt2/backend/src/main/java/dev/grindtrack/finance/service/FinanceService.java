package dev.grindtrack.finance.service;

import dev.grindtrack.finance.domain.Account;
import dev.grindtrack.finance.domain.AccountRepository;
import dev.grindtrack.finance.domain.AccountType;
import dev.grindtrack.finance.domain.CategoryTotal;
import dev.grindtrack.finance.domain.Institution;
import dev.grindtrack.finance.domain.SavingsGoal;
import dev.grindtrack.finance.domain.SavingsGoalRepository;
import dev.grindtrack.finance.domain.Transaction;
import dev.grindtrack.finance.domain.TransactionRepository;
import dev.grindtrack.finance.domain.TxnType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads and writes for accounts, transactions and savings goals. */
@Service
public class FinanceService {

  private final AccountRepository accounts;
  private final TransactionRepository transactions;
  private final SavingsGoalRepository goals;
  private final MerchantNormalizer merchantNormalizer;
  private final TxnTypeClassifier classifier;
  private final CategoryRuleService categoryRules;

  public FinanceService(
      AccountRepository accounts,
      TransactionRepository transactions,
      SavingsGoalRepository goals,
      MerchantNormalizer merchantNormalizer,
      TxnTypeClassifier classifier,
      CategoryRuleService categoryRules) {
    this.accounts = accounts;
    this.transactions = transactions;
    this.goals = goals;
    this.merchantNormalizer = merchantNormalizer;
    this.classifier = classifier;
    this.categoryRules = categoryRules;
  }

  // ---------------------------------------------------------------- accounts

  public List<Account> listAccounts(boolean includeInactive) {
    return includeInactive
        ? accounts.findAllByOrderBySortOrderAscNameAsc()
        : accounts.findByActiveTrueOrderBySortOrderAscNameAsc();
  }

  public Account getAccount(Long id) {
    return accounts.findById(id).orElseThrow(() -> new NoSuchElementException("account " + id));
  }

  @Transactional
  public Account createAccount(
      String name,
      Institution institution,
      AccountType accountType,
      String last4,
      boolean countsTowardSavings,
      int sortOrder) {
    checkSavingsFlag(accountType, countsTowardSavings);
    Account account = new Account(name, institution, accountType);
    account.update(name, institution, accountType, last4, countsTowardSavings, true, sortOrder);
    return accounts.save(account);
  }

  /**
   * A savings goal means cash that could go toward a house, so only cash accounts may carry the
   * flag. An invariant rather than a form rule: it has to hold however the account is created, and
   * the damage — a progress bar reporting a third of a down payment that cannot be spent — is
   * silent rather than loud.
   */
  private static void checkSavingsFlag(AccountType type, boolean countsTowardSavings) {
    if (countsTowardSavings && !type.canCountTowardSavings()) {
      throw new IllegalArgumentException(
          "a "
              + type.name().toLowerCase().replace('_', ' ')
              + " account cannot count toward a savings goal — that figure is money available for a"
              + " down payment, and this is not");
    }
  }

  @Transactional
  public Account updateAccount(
      Long id,
      String name,
      Institution institution,
      AccountType accountType,
      String last4,
      boolean countsTowardSavings,
      boolean active,
      int sortOrder) {
    checkSavingsFlag(accountType, countsTowardSavings);
    Account account = getAccount(id);
    account.update(name, institution, accountType, last4, countsTowardSavings, active, sortOrder);
    return accounts.save(account);
  }

  /**
   * Records a balance reading. Liabilities are coerced negative so that summing every account
   * yields net worth directly — entering a card balance as a positive 1,200 owed would otherwise
   * make debt look like an asset.
   */
  @Transactional
  public Account recordBalance(Long id, BigDecimal balance, LocalDate asOf) {
    Account account = getAccount(id);
    BigDecimal signed = account.getAccountType().isLiability() ? balance.abs().negate() : balance;
    account.recordBalance(signed, asOf == null ? LocalDate.now() : asOf);
    return accounts.save(account);
  }

  @Transactional
  public void deleteAccount(Long id) {
    accounts.deleteById(id);
  }

  // ------------------------------------------------------------ transactions

  /**
   * How many transactions an account holds.
   *
   * <p>Here rather than in the controller: FinanceController used to inject TransactionRepository
   * solely for this one call, which meant "how do you count an account's rows" had two answers
   * depending on which file you opened.
   */
  public long transactionCount(Long accountId) {
    return transactions.countByAccountId(accountId);
  }

  public List<Transaction> listByAccount(Long accountId) {
    return transactions.findByAccountIdOrderByPostedDateDesc(accountId);
  }

  public List<Transaction> listUncategorized() {
    return transactions.findUncategorized();
  }

  /**
   * Adds a transaction by hand.
   *
   * <p>Runs the same normalization and classification the importers will, so a manually-entered row
   * is indistinguishable from an imported one, and returns empty when the fingerprint already
   * exists rather than creating a duplicate.
   */
  @Transactional
  public Optional<Transaction> addTransaction(
      Long accountId,
      LocalDate postedDate,
      LocalDate transactionDate,
      BigDecimal amount,
      String rawDescription,
      TxnType explicitType,
      String notes) {
    Account account = getAccount(accountId);

    String fingerprint = Transaction.fingerprintOf(accountId, postedDate, amount, rawDescription);
    if (transactions.existsByAccountIdAndFingerprint(accountId, fingerprint)) {
      return Optional.empty();
    }

    Transaction txn = new Transaction(accountId, postedDate, amount, rawDescription);
    TxnType type =
        explicitType != null
            ? explicitType
            : classifier.classify(rawDescription, amount, account.getAccountType());
    txn.applyImportedDetail(
        transactionDate, merchantNormalizer.normalize(rawDescription), null, type, false);
    // A row typed in by hand goes through the same rules an imported one does, so the two are
    // genuinely indistinguishable afterwards rather than only nearly so.
    categoryRules.applyTo(List.of(txn));
    if (notes != null && !notes.isBlank()) {
      txn.update(
          postedDate,
          transactionDate,
          amount,
          rawDescription,
          merchantNormalizer.normalize(rawDescription),
          type,
          notes);
    }
    return Optional.of(transactions.save(txn));
  }

  @Transactional
  public Transaction categorize(Long txnId, String category) {
    Transaction txn =
        transactions.findById(txnId).orElseThrow(() -> new NoSuchElementException("txn " + txnId));
    txn.categorizeManually(category);
    return transactions.save(txn);
  }

  /** Corrects what a row represents — the "this was a transfer, not spending" fix. */
  @Transactional
  public Transaction reclassify(Long txnId, TxnType type) {
    Transaction txn =
        transactions.findById(txnId).orElseThrow(() -> new NoSuchElementException("txn " + txnId));
    txn.reclassify(type);
    return transactions.save(txn);
  }

  @Transactional
  public void deleteTransaction(Long id) {
    transactions.deleteById(id);
  }

  // ------------------------------------------------------------------ goals

  public List<SavingsGoal> listGoals(boolean includeInactive) {
    return includeInactive
        ? goals.findAllByOrderBySortOrderAscIdAsc()
        : goals.findByActiveTrueOrderBySortOrderAscIdAsc();
  }

  @Transactional
  public SavingsGoal createGoal(
      String name, BigDecimal target, LocalDate targetDate, String note, int sortOrder) {
    SavingsGoal goal = new SavingsGoal(name, target);
    goal.update(name, target, targetDate, note, true, sortOrder);
    return goals.save(goal);
  }

  @Transactional
  public SavingsGoal updateGoal(
      Long id,
      String name,
      BigDecimal target,
      LocalDate targetDate,
      String note,
      boolean active,
      int sortOrder) {
    SavingsGoal goal =
        goals.findById(id).orElseThrow(() -> new NoSuchElementException("goal " + id));
    goal.update(name, target, targetDate, note, active, sortOrder);
    return goals.save(goal);
  }

  @Transactional
  public void deleteGoal(Long id) {
    goals.deleteById(id);
  }

  /** Current total across accounts flagged as holding the savings. */
  public BigDecimal savingsBalance() {
    BigDecimal sum = accounts.sumSavingsBalance();
    return sum == null ? BigDecimal.ZERO : sum;
  }

  public BigDecimal netWorth() {
    BigDecimal sum = accounts.sumNetWorth();
    return sum == null ? BigDecimal.ZERO : sum;
  }

  /** Percent of a goal reached, 0-100, rounded to one decimal. */
  public BigDecimal progressPercent(SavingsGoal goal) {
    return progressPercent(goal, savingsBalance());
  }

  /**
   * @param current the savings balance, passed in when the caller already has it. Rendering a list
   *     of goals otherwise re-queries the same sum twice per goal.
   */
  public BigDecimal progressPercent(SavingsGoal goal, BigDecimal current) {
    if (goal.getTargetAmount() == null || goal.getTargetAmount().signum() <= 0) {
      return BigDecimal.ZERO;
    }
    return current
        .multiply(BigDecimal.valueOf(100))
        .divide(goal.getTargetAmount(), 1, RoundingMode.HALF_UP);
  }

  // --------------------------------------------------------------- rollups

  /**
   * Where the money went over a window.
   *
   * <p>Every figure here excludes transfers, card payments and unsettled rows, so the totals mean
   * what they say. That exclusion is the difference between this being useful and it being a number
   * roughly twice the truth — in the first pass over real statements, 78 of 947 rows were money
   * moving between accounts rather than leaving.
   *
   * @param totalSpend negative, keeping the app's convention that money out is negative
   */
  public record SpendSummary(
      String from,
      String to,
      BigDecimal totalSpend,
      BigDecimal totalIncome,
      BigDecimal net,
      int transactionCount,
      List<CategoryTotal> byCategory,
      List<CategoryTotal> topMerchants) {}

  /**
   * One month of totals, for comparing months against each other.
   *
   * @param spend positive, unlike the stored convention — a bar chart of negative numbers is a
   *     needless puzzle, and this record exists only to be displayed
   */
  public record MonthTotal(
      String month, BigDecimal spend, BigDecimal income, BigDecimal net, boolean partial) {}

  /**
   * The last {@code count} months, oldest first.
   *
   * <p>The current month is marked partial rather than omitted. Dropping it hides where you are
   * right now, and including it unmarked makes every month look like a collapse in spending on the
   * 3rd.
   */
  public List<MonthTotal> monthlyTotals(int count) {
    YearMonth current = YearMonth.now();
    List<MonthTotal> out = new ArrayList<>();
    for (int i = count - 1; i >= 0; i--) {
      YearMonth m = current.minusMonths(i);
      BigDecimal spend = transactions.sumSpendBetween(m.atDay(1), m.atEndOfMonth()).abs();
      BigDecimal income = transactions.sumIncomeBetween(m.atDay(1), m.atEndOfMonth());
      out.add(
          new MonthTotal(m.toString(), spend, income, income.subtract(spend), m.equals(current)));
    }
    return out;
  }

  public SpendSummary spendBetween(LocalDate from, LocalDate to) {
    List<Transaction> countable = transactions.findCountableBetween(from, to);

    BigDecimal spend = BigDecimal.ZERO;
    BigDecimal income = BigDecimal.ZERO;
    for (Transaction t : countable) {
      if (t.getTxnType() == TxnType.SPEND) {
        spend = spend.add(t.getAmount());
      } else if (t.getTxnType() == TxnType.INCOME) {
        income = income.add(t.getAmount());
      }
    }

    List<CategoryTotal> merchants = transactions.spendByMerchantBetween(from, to);
    return new SpendSummary(
        from.toString(),
        to.toString(),
        spend,
        income,
        spend.add(income),
        countable.size(),
        transactions.spendByCategoryBetween(from, to),
        merchants.size() > 15 ? List.copyOf(merchants.subList(0, 15)) : merchants);
  }
}
