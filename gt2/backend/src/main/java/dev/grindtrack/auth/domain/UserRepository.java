package dev.grindtrack.auth.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
  Optional<User> findByUsername(String username);

  List<User> findAllByOrderByIdAsc();

  /** The owner, or the first partner: the other person in the chat. */
  Optional<User> findFirstByRoleOrderByIdAsc(Role role);
}
