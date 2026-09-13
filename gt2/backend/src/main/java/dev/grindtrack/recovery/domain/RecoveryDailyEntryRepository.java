package dev.grindtrack.recovery.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecoveryDailyEntryRepository extends JpaRepository<RecoveryDailyEntry, Long> {

  Optional<RecoveryDailyEntry> findByTextIdAndMonthAndDay(Long textId, int month, int day);

  long countByTextId(Long textId);
}
