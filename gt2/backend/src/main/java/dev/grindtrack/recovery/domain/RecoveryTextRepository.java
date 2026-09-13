package dev.grindtrack.recovery.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecoveryTextRepository extends JpaRepository<RecoveryText, Long> {

  Optional<RecoveryText> findBySlot(TextSlot slot);
}
