package dev.grindtrack.recovery.domain;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecoveryDayRepository extends JpaRepository<RecoveryDay, LocalDate> {

  /** Enough history for the streak: a run longer than this is reported as "120+". */
  List<RecoveryDay> findTop120ByMeditatedTrueOrderByDayDesc();

  long countByReadDoneTrue();
}
