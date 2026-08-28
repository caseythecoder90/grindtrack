package dev.grindtrack.finance.domain;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetExtraRepository extends JpaRepository<BudgetExtra, Long> {

  List<BudgetExtra> findByMonthOrderByIdAsc(LocalDate month);

  /** From a given month forward, for the list of what is still coming. */
  List<BudgetExtra> findByMonthGreaterThanEqualOrderByMonthAscIdAsc(LocalDate from);
}
