package dev.grindtrack.relationship.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MomentRepository extends JpaRepository<Moment, Long> {

  /** The timeline, newest first. */
  List<Moment> findAllByOrderByOccurredOnDescIdDesc();

  /**
   * One page of the timeline, newest first.
   *
   * <p>A {@link Page} rather than a list because the screen has to say "12 of 48" — knowing there
   * are more is what makes an "older" button honest — and because skipping in Java means loading
   * every row to throw most of them away, which is fine at 48 moments and not at 4,800.
   */
  Page<Moment> findAllByOrderByOccurredOnDescIdDesc(Pageable pageable);

  /** The most recent of one kind, which is what every "when did we last" figure is. */
  Optional<Moment> findFirstByKindOrderByOccurredOnDescIdDesc(MomentKind kind);

  /** The last few of one kind, in dates rather than as a rate. */
  List<Moment> findByKindOrderByOccurredOnDescIdDesc(MomentKind kind, Limit limit);

  long countByKindAndOccurredOnBetween(MomentKind kind, LocalDate from, LocalDate to);

  List<Moment> findByOccurredOnBetweenOrderByOccurredOnDescIdDesc(LocalDate from, LocalDate to);
}
