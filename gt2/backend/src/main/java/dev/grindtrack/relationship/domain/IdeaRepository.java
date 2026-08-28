package dev.grindtrack.relationship.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdeaRepository extends JpaRepository<Idea, Long> {

  List<Idea> findByStatusNotOrderByIdDesc(IdeaStatus status);

  List<Idea> findAllByOrderByIdDesc();

  List<Idea> findByStatusNotAndOccasionIgnoreCase(IdeaStatus status, String occasion);
}
