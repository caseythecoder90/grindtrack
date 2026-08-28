package dev.grindtrack.relationship.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReadingRepository extends JpaRepository<Reading, Long> {

  List<Reading> findAllByOrderByStatusAscIdDesc();

  List<Reading> findByStatusOrderByIdDesc(ReadingStatus status);
}
