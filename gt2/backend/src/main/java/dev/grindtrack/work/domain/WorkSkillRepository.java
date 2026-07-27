package dev.grindtrack.work.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkSkillRepository extends JpaRepository<WorkSkill, Long> {
  List<WorkSkill> findAllByOrderBySortOrderAscIdAsc();
}
