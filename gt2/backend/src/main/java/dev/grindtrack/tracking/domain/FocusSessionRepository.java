package dev.grindtrack.tracking.domain;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FocusSessionRepository extends JpaRepository<FocusSession, Long> {
  List<FocusSession> findBySessionDateOrderByStartedAt(LocalDate sessionDate);
}
