package dev.grindtrack.push.domain;

import dev.grindtrack.auth.domain.Role;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

  Optional<PushSubscription> findByEndpoint(String endpoint);

  Optional<PushSubscription> findByIdAndUserId(Long id, Long userId);

  List<PushSubscription> findAllByUserIdOrderByCreatedAtAsc(Long userId);

  long countByUserId(Long userId);

  /**
   * Every device of every account with that role — with {@code OWNER}, where the scheduled pushes
   * go.
   */
  @Query(
      "select s from PushSubscription s, User u"
          + " where u.id = s.userId and u.role = :role order by s.createdAt asc")
  List<PushSubscription> findAllByRole(@Param("role") Role role);
}
