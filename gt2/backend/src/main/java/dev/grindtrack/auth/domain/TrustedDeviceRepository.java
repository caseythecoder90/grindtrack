package dev.grindtrack.auth.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrustedDeviceRepository extends JpaRepository<TrustedDevice, Long> {
  Optional<TrustedDevice> findByTokenHash(String tokenHash);

  List<TrustedDevice> findByUserIdAndRevokedFalse(Long userId);
}
