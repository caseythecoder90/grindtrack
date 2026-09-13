package dev.grindtrack.recovery.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecoveryFileRepository extends JpaRepository<RecoveryFile, Long> {

  List<RecoveryFile> findBySlotOrderByOrdinalAsc(TextSlot slot);

  void deleteBySlot(TextSlot slot);
}
