package dev.grindtrack.calendar.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecurringTaskCompletionRepository
    extends JpaRepository<RecurringTaskCompletion, Long> {

  List<RecurringTaskCompletion> findByTaskIdOrderByDoneOnDesc(Long taskId);

  void deleteByTaskId(Long taskId);
}
