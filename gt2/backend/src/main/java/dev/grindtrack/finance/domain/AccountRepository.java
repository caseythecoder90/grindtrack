package dev.grindtrack.finance.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AccountRepository extends JpaRepository<Account, Long> {

  List<Account> findByActiveTrueOrderBySortOrderAscNameAsc();

  List<Account> findAllByOrderBySortOrderAscNameAsc();

  Optional<Account> findByInstitutionAndLast4(Institution institution, String last4);

  /** Total across the accounts flagged as holding the savings — the goal progress numerator. */
  @Query(
      "SELECT COALESCE(SUM(a.currentBalance), 0) FROM Account a "
          + "WHERE a.countsTowardSavings = true AND a.active = true")
  BigDecimal sumSavingsBalance();

  /** Everything, signed by account type, so cards and loans subtract. */
  @Query("SELECT COALESCE(SUM(a.currentBalance), 0) FROM Account a WHERE a.active = true")
  BigDecimal sumNetWorth();
}
