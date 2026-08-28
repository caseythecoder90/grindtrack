package dev.grindtrack.relationship.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MomentRepository extends JpaRepository<Moment, Long> {

  /** The timeline, newest first. */
  List<Moment> findAllByOrderByOccurredOnDescIdDesc();

  /** The most recent of one kind, which is what every "when did we last" figure is. */
  Optional<Moment> findFirstByKindOrderByOccurredOnDescIdDesc(MomentKind kind);

  /** The last few of one kind, in dates rather than as a rate. */
  List<Moment> findByKindOrderByOccurredOnDescIdDesc(MomentKind kind, Limit limit);

  long countByKindAndOccurredOnBetween(MomentKind kind, LocalDate from, LocalDate to);

  List<Moment> findByOccurredOnBetweenOrderByOccurredOnDescIdDesc(LocalDate from, LocalDate to);
}
