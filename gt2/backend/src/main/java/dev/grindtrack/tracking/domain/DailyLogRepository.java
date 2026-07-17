package dev.grindtrack.tracking.domain;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyLogRepository extends JpaRepository<DailyLog, LocalDate> {
  List<DailyLog> findByLogDateBetweenOrderByLogDate(LocalDate from, LocalDate to);
}
