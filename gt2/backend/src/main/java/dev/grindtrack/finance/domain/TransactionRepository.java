package dev.grindtrack.finance.domain;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

  boolean existsByAccountIdAndFingerprint(Long accountId, String fingerprint);

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

  /** The review inbox: anything automation could not confidently place. */
  @Query(
      "SELECT t FROM Transaction t "
          + "WHERE t.categorySource = dev.grindtrack.finance.domain.CategorySource.UNCATEGORIZED "
          + "AND t.txnType <> dev.grindtrack.finance.domain.TxnType.TRANSFER "
          + "ORDER BY t.postedDate DESC, t.id DESC")
  List<Transaction> findUncategorized();

  long countByAccountId(Long accountId);
}
