package dev.grindtrack.recovery.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

  List<JournalEntry> findTop50ByOrderByIdDesc();

  /** The page after {@code before}: ids are assigned in time order, so the id is the cursor. */
  List<JournalEntry> findTop50ByIdLessThanOrderByIdDesc(Long before);
}
