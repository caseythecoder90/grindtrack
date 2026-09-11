package dev.grindtrack.assistant.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssistantReportRepository extends JpaRepository<AssistantReport, Long> {

  Optional<AssistantReport> findByKindAndWeekStart(String kind, LocalDate weekStart);

  /** Everything generated since an instant — the month's spend, summed in the service. */
  List<AssistantReport> findByGeneratedAtGreaterThanEqual(OffsetDateTime since);
}
