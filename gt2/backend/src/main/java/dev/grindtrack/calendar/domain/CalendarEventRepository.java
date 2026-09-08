package dev.grindtrack.calendar.domain;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {

  /**
   * A date range, ordered the way a day is read.
   *
   * <p>Written out rather than derived from the method name because of {@code NULLS FIRST}, which a
   * derived query cannot express and Postgres does not do by default: ascending puts nulls last, so
   * all-day rows sorted <em>below</em> the timed ones. "Dog — flea and tick" is true of the whole
   * day and belongs above a 9am dentist, not after it.
   */
  @Query(
      """
      SELECT e FROM CalendarEvent e
      WHERE e.eventDate BETWEEN :from AND :to
      ORDER BY e.eventDate ASC, e.startTime ASC NULLS FIRST
      """)
  List<CalendarEvent> findInRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

  List<CalendarEvent> findByPlanItemId(Long planItemId);
}
