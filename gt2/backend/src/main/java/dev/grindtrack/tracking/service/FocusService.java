package dev.grindtrack.tracking.service;

import dev.grindtrack.tracking.domain.DailyLog;
import dev.grindtrack.tracking.domain.DailyLogRepository;
import dev.grindtrack.tracking.domain.FocusKind;
import dev.grindtrack.tracking.domain.FocusSession;
import dev.grindtrack.tracking.domain.FocusSessionRepository;
import dev.grindtrack.work.domain.WorkLog;
import dev.grindtrack.work.domain.WorkLogRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records pomodoro sessions and folds their time into the day's log in one transaction. A {@link
 * FocusKind#STUDY} session adds to the personal daily log; a {@link FocusKind#WORK} one adds to the
 * day-job work log — hence the (deliberate) dependency on the work feature's repository.
 */
@Service
public class FocusService {

  private static final BigDecimal SIXTY = BigDecimal.valueOf(60);

  private final FocusSessionRepository sessions;
  private final DailyLogRepository dailyLogs;
  private final WorkLogRepository workLogs;

  public FocusService(
      FocusSessionRepository sessions, DailyLogRepository dailyLogs, WorkLogRepository workLogs) {
    this.sessions = sessions;
    this.dailyLogs = dailyLogs;
    this.workLogs = workLogs;
  }

  /**
   * Saves the session and adds its duration to the matching day's hours: the personal daily log for
   * study, the work log for work. Hours columns are NUMERIC(4,1), so minutes are rounded to one
   * decimal of an hour; both entities clamp at 24.
   */
  @Transactional
  public FocusSession record(
      LocalDate date,
      OffsetDateTime startedAt,
      int durationMinutes,
      boolean completed,
      FocusKind kind) {
    FocusSession session =
        sessions.save(new FocusSession(date, startedAt, durationMinutes, completed, kind));
    BigDecimal hours = BigDecimal.valueOf(durationMinutes).divide(SIXTY, 1, RoundingMode.HALF_UP);
    if (kind == FocusKind.WORK) {
      WorkLog log = workLogs.findById(date).orElseGet(() -> new WorkLog(date));
      log.addHours(hours);
      workLogs.save(log);
    } else {
      DailyLog log = dailyLogs.findById(date).orElseGet(() -> new DailyLog(date));
      log.addHours(hours);
      dailyLogs.save(log);
    }
    return session;
  }

  /** Sessions on a date, optionally filtered to one kind ({@code null} returns both). */
  public List<FocusSession> sessionsOn(LocalDate date, FocusKind kind) {
    return kind == null
        ? sessions.findBySessionDateOrderByStartedAt(date)
        : sessions.findBySessionDateAndKindOrderByStartedAt(date, kind);
  }
}
