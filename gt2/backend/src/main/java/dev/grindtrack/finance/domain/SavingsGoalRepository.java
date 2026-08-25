package dev.grindtrack.finance.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavingsGoalRepository extends JpaRepository<SavingsGoal, Long> {

  List<SavingsGoal> findByActiveTrueOrderBySortOrderAscIdAsc();

  List<SavingsGoal> findAllByOrderBySortOrderAscIdAsc();
}
