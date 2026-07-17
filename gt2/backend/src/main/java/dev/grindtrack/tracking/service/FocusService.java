package dev.grindtrack.tracking.service;

import dev.grindtrack.tracking.domain.DailyLog;
import dev.grindtrack.tracking.domain.DailyLogRepository;
import dev.grindtrack.tracking.domain.FocusSession;
import dev.grindtrack.tracking.domain.FocusSessionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records pomodoro sessions and folds their time into the day's log in one transaction. */
@Service
public class FocusService {

  private static final BigDecimal SIXTY = BigDecimal.valueOf(60);

  private final FocusSessionRepository sessions;
  private final DailyLogRepository dailyLogs;

  public FocusService(FocusSessionRepository sessions, DailyLogRepository dailyLogs) {
    this.sessions = sessions;
    this.dailyLogs = dailyLogs;
  }

  /**
   * Saves the session and adds its duration to the day's hours (daily_logs.hours is NUMERIC(4,1),
   * so minutes are rounded to one decimal of an hour; the entity clamps at 24).
   */
  @Transactional
  public FocusSession record(
      LocalDate date, OffsetDateTime startedAt, int durationMinutes, boolean completed) {
    FocusSession session =
        sessions.save(new FocusSession(date, startedAt, durationMinutes, completed));
    DailyLog log = dailyLogs.findById(date).orElseGet(() -> new DailyLog(date));
    log.addHours(BigDecimal.valueOf(durationMinutes).divide(SIXTY, 1, RoundingMode.HALF_UP));
    dailyLogs.save(log);
    return session;
  }

  public List<FocusSession> sessionsOn(LocalDate date) {
    return sessions.findBySessionDateOrderByStartedAt(date);
  }
}
