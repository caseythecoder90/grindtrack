package dev.grindtrack.recovery.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactRepository extends JpaRepository<Contact, Long> {

  Optional<Contact> findFirstByPersonIdOrderByAtDesc(Long personId);

  List<Contact> findTop30ByPersonIdOrderByAtDesc(Long personId);

  long countByPersonId(Long personId);
}
