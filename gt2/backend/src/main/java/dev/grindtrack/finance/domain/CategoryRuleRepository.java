package dev.grindtrack.finance.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRuleRepository extends JpaRepository<CategoryRule, Long> {

  /**
   * Every active rule in the order they must be evaluated. Ties break on id so the order is total —
   * two rules at the same priority always resolve the same way run to run.
   */
  List<CategoryRule> findByActiveTrueOrderByPriorityAscIdAsc();

  List<CategoryRule> findAllByOrderByPriorityAscIdAsc();

  Optional<CategoryRule> findByPatternIgnoreCaseAndMatchType(String pattern, MatchType matchType);
}
