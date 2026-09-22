package dev.grindtrack.recovery.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecoveryMarkRepository extends JpaRepository<RecoveryMark, Long> {

  List<RecoveryMark> findBySlotOrderBySeqAscStartOffAscIdAsc(TextSlot slot);

  List<RecoveryMark> findBySlotAndSeqBetweenOrderBySeqAscStartOffAscIdAsc(
      TextSlot slot, int from, int to);
}
