package dev.grindtrack.calendar.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecurringTaskRepository extends JpaRepository<RecurringTask, Long> {

  /**
   * Ordering is deliberately not "by next due": that is derived from two columns and Postgres would
   * have to compute it per row anyway. The service sorts the handful of rows this returns.
   */
  List<RecurringTask> findByActiveOrderByTitleAsc(boolean active);
}
