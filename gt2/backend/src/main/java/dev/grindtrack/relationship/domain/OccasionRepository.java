package dev.grindtrack.relationship.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OccasionRepository extends JpaRepository<Occasion, Long> {

  /** Ordering by the stored date is meaningless for recurring rows; the service sorts by next. */
  List<Occasion> findAllByOrderByLabelAsc();
}
