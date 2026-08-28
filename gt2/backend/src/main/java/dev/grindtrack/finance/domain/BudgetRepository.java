package dev.grindtrack.finance.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

  List<Budget> findByActiveTrueOrderBySortOrderAscCategoryAsc();

  List<Budget> findAllByOrderBySortOrderAscCategoryAsc();

  /** Case-insensitive because the unique index is on lower(category) and the form is free text. */
  Optional<Budget> findByCategoryIgnoreCase(String category);
}
