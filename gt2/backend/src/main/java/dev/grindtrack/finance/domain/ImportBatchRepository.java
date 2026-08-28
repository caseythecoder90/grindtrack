package dev.grindtrack.finance.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {

  List<ImportBatch> findAllByOrderByImportedAtDesc();

  List<ImportBatch> findByAccountIdOrderByImportedAtDesc(Long accountId);
}
