package dev.grindtrack.tracking.domain;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FocusSessionRepository extends JpaRepository<FocusSession, Long> {
  List<FocusSession> findBySessionDateOrderByStartedAt(LocalDate sessionDate);

  List<FocusSession> findBySessionDateAndKindOrderByStartedAt(
      LocalDate sessionDate, FocusKind kind);

  /**
   * Every lunch session, newest first.
   *
   * <p>One query for all three rollups — streak, week, per-subject. A lunch is at most one or two
   * sessions a day, so even four years of them is a few hundred rows: cheaper to load once and
   * group in memory than to issue three grouped queries whose results have to agree with each
   * other. Revisit if this ever stops being true, which it will not.
   */
  @Query(
      "SELECT s FROM FocusSession s "
          + "WHERE s.kind IN (dev.grindtrack.tracking.domain.FocusKind.READING, "
          + "                 dev.grindtrack.tracking.domain.FocusKind.REVIEW) "
          + "ORDER BY s.sessionDate DESC, s.startedAt DESC")
  List<FocusSession> findLunchSessions();

  /** Sessions already recorded against a plan item, for the Plan tab's per-item totals. */
  @Query("SELECT s FROM FocusSession s WHERE s.planItemId = :planItemId")
  List<FocusSession> findByPlanItemId(@Param("planItemId") Long planItemId);
}
