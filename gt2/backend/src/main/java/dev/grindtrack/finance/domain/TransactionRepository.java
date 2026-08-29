package dev.grindtrack.finance.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

  boolean existsByAccountIdAndFingerprint(Long accountId, String fingerprint);

  /**
   * Every fingerprint on an account, for dedupe during an import.
   *
   * <p>One query instead of one per row. A 420-row checking export was doing 420 round trips to
   * answer a question a single {@code Set} answers in memory.
   */
  @Query("SELECT t.fingerprint FROM Transaction t WHERE t.accountId = :accountId")
  Set<String> findFingerprintsByAccountId(@Param("accountId") Long accountId);

  List<Transaction> findByAccountIdOrderByPostedDateDesc(Long accountId);

  Page<Transaction> findByPostedDateBetweenOrderByPostedDateDescIdDesc(
      LocalDate from, LocalDate to, Pageable pageable);

  /**
   * Spend and income only, over a window.
   *
   * <p>TRANSFER and PAYMENT are excluded here rather than at the call site so that no future rollup
   * can forget to do it and quietly double-count card payments.
   */
  @Query(
      "SELECT t FROM Transaction t "
          + "WHERE t.postedDate BETWEEN :from AND :to "
          + "AND t.txnType IN (dev.grindtrack.finance.domain.TxnType.SPEND, "
          + "                  dev.grindtrack.finance.domain.TxnType.INCOME) "
          + "AND t.pending = false "
          + "ORDER BY t.postedDate DESC, t.id DESC")
  List<Transaction> findCountableBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

  /**
   * The review inbox: spending that automation could not confidently place.
   *
   * <p>SPEND only, and that is the whole point. A category exists to answer "where did the money
   * go", and only spending goes anywhere -- every rollup in the app filters on SPEND. Asking for a
   * category on a paycheck, a card payment or a transfer between your own accounts is asking for
   * data nothing will ever read, and it pads a list whose length is the thing that makes it feel
   * unfinishable.
   */
  @Query(
      "SELECT t FROM Transaction t "
          + "WHERE t.categorySource = dev.grindtrack.finance.domain.CategorySource.UNCATEGORIZED "
          + "AND t.txnType = dev.grindtrack.finance.domain.TxnType.SPEND "
          + "ORDER BY t.postedDate DESC, t.id DESC")
  List<Transaction> findUncategorized();

  /**
   * Every transaction, filtered and paged, for browsing rather than reviewing.
   *
   * <p>Each filter is skipped when its parameter is null, so one query serves "everything", "just
   * this account", "just income" and any combination -- rather than a method per shape. Ordering
   * comes from the {@link Pageable}, since sorting by amount and by date are both useful and
   * neither belongs baked into the query.
   */
  @Query(
      "SELECT t FROM Transaction t "
          + "WHERE (:accountId IS NULL OR t.accountId = :accountId) "
          + "AND (:txnType IS NULL OR t.txnType = :txnType) "
          + "AND (:categorySource IS NULL OR t.categorySource = :categorySource)")
  Page<Transaction> search(
      @Param("accountId") Long accountId,
      @Param("txnType") TxnType txnType,
      @Param("categorySource") CategorySource categorySource,
      Pageable pageable);

  long countByAccountId(Long accountId);

  /**
   * Everything automation is still allowed to touch, for a re-run of the rules over existing rows.
   * Rows a person categorized by hand are excluded here rather than filtered later, so a backfill
   * cannot undo a correction even by mistake.
   */
  List<Transaction> findByCategorySourceNotOrderByIdAsc(CategorySource source);

  /**
   * Spending by category over a window.
   *
   * <p>Uses the same exclusions as {@link #findCountableBetween} — transfers, card payments and
   * unsettled rows are all out — because a category total that includes a credit-card payment is
   * exactly the lie this feature exists to avoid.
   */
  @Query(
      "SELECT new dev.grindtrack.finance.domain.CategoryTotal("
          + "  t.category, SUM(t.amount), COUNT(t)) "
          + "FROM Transaction t "
          + "WHERE t.postedDate BETWEEN :from AND :to "
          + "AND t.txnType = dev.grindtrack.finance.domain.TxnType.SPEND "
          + "AND t.pending = false "
          + "GROUP BY t.category "
          + "ORDER BY SUM(t.amount) ASC")
  List<CategoryTotal> spendByCategoryBetween(
      @Param("from") LocalDate from, @Param("to") LocalDate to);

  /**
   * Total spending over a window, negative, with the same exclusions as every other rollup.
   *
   * <p>Returns zero rather than null for an empty window, so callers never have to null-check a
   * figure they are about to subtract from a budget.
   */
  @Query(
      "SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t "
          + "WHERE t.postedDate BETWEEN :from AND :to "
          + "AND t.txnType = dev.grindtrack.finance.domain.TxnType.SPEND "
          + "AND t.pending = false")
  BigDecimal sumSpendBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

  /** Total income over a window, positive. Drives the trailing average behind expected income. */
  @Query(
      "SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t "
          + "WHERE t.postedDate BETWEEN :from AND :to "
          + "AND t.txnType = dev.grindtrack.finance.domain.TxnType.INCOME "
          + "AND t.pending = false")
  BigDecimal sumIncomeBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

  /** Merchant totals inside one category, so a surprising category can be opened up. */
  @Query(
      "SELECT new dev.grindtrack.finance.domain.CategoryTotal("
          + "  t.merchant, SUM(t.amount), COUNT(t)) "
          + "FROM Transaction t "
          + "WHERE t.postedDate BETWEEN :from AND :to "
          + "AND t.txnType = dev.grindtrack.finance.domain.TxnType.SPEND "
          + "AND t.pending = false "
          + "GROUP BY t.merchant "
          + "ORDER BY SUM(t.amount) ASC")
  List<CategoryTotal> spendByMerchantBetween(
      @Param("from") LocalDate from, @Param("to") LocalDate to);

  /**
   * Undoing an import removes only the rows it created. Hand-entered rows have a null batch and are
   * never touched, and a row the user has since re-categorized still belongs to the batch — undoing
   * means "this file should not have been imported", which includes those edits.
   */
  long deleteByImportBatchId(Long importBatchId);

  long countByImportBatchId(Long importBatchId);
}
