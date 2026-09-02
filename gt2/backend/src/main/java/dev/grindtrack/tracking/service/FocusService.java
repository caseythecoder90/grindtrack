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
import java.util.NoSuchElementException;
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
      FocusKind kind,
      Long planItemId,
      String topic) {
    // Normalised once, so the row that is stored and the log the minutes land on cannot
    // disagree. The controller never sends null, but this is also called from tests and would
    // be called by any future importer; a null kind must not decide it is the day job.
    FocusKind resolved = kind == null ? FocusKind.STUDY : kind;
    FocusSession session = new FocusSession(date, startedAt, durationMinutes, completed, resolved);
    session.setSubject(planItemId, topic);
    session = sessions.save(session);
    BigDecimal hours = BigDecimal.valueOf(durationMinutes).divide(SIXTY, 1, RoundingMode.HALF_UP);
    if (resolved.isDayJob()) {
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

  /**
   * Records the note written after a session ended.
   *
   * <p>A separate call rather than part of {@link #record}, because you do not know the takeaway
   * when the timer stops — you know it a minute later, once you have thought about it. Making it
   * part of the original write would mean either blocking the save on a text box or losing the
   * note.
   */
  @Transactional
  public FocusSession recordTakeaway(Long sessionId, String takeaway) {
    FocusSession session =
        sessions
            .findById(sessionId)
            .orElseThrow(() -> new NoSuchElementException("focus session " + sessionId));
    session.setTakeaway(takeaway);
    return sessions.save(session);
  }

  /** Sessions on a date, optionally filtered to one kind ({@code null} returns every kind). */
  public List<FocusSession> sessionsOn(LocalDate date, FocusKind kind) {
    return kind == null
        ? sessions.findBySessionDateOrderByStartedAt(date)
        : sessions.findBySessionDateAndKindOrderByStartedAt(date, kind);
  }
}
