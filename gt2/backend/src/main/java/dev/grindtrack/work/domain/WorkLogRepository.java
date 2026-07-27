package dev.grindtrack.work.domain;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkLogRepository extends JpaRepository<WorkLog, LocalDate> {
  List<WorkLog> findByLogDateBetweenOrderByLogDate(LocalDate from, LocalDate to);
}
