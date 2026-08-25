package dev.grindtrack.finance.service;

import dev.grindtrack.finance.domain.Account;
import dev.grindtrack.finance.domain.AccountRepository;
import dev.grindtrack.finance.domain.AccountType;
import dev.grindtrack.finance.domain.Institution;
import dev.grindtrack.finance.domain.SavingsGoal;
import dev.grindtrack.finance.domain.SavingsGoalRepository;
import dev.grindtrack.finance.domain.Transaction;
import dev.grindtrack.finance.domain.TransactionRepository;
import dev.grindtrack.finance.domain.TxnType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
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

  public FinanceService(
      AccountRepository accounts,
      TransactionRepository transactions,
      SavingsGoalRepository goals,
      MerchantNormalizer merchantNormalizer,
      TxnTypeClassifier classifier) {
    this.accounts = accounts;
    this.transactions = transactions;
    this.goals = goals;
    this.merchantNormalizer = merchantNormalizer;
    this.classifier = classifier;
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
    Account account = new Account(name, institution, accountType);
    account.update(name, institution, accountType, last4, countsTowardSavings, true, sortOrder);
    return accounts.save(account);
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
    if (goal.getTargetAmount() == null || goal.getTargetAmount().signum() <= 0) {
      return BigDecimal.ZERO;
    }
    return savingsBalance()
        .multiply(BigDecimal.valueOf(100))
        .divide(goal.getTargetAmount(), 1, RoundingMode.HALF_UP);
  }
}
