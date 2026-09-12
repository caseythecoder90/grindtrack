package dev.grindtrack.push.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

  Optional<PushSubscription> findByEndpoint(String endpoint);

  List<PushSubscription> findAllByOrderByCreatedAtAsc();
}
