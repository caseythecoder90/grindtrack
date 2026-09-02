package dev.grindtrack.tracking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.grindtrack.tracking.domain.DailyLog;
import dev.grindtrack.tracking.domain.DailyLogRepository;
import dev.grindtrack.tracking.domain.FocusKind;
import dev.grindtrack.tracking.domain.FocusSession;
import dev.grindtrack.tracking.domain.FocusSessionRepository;
import dev.grindtrack.work.domain.WorkLog;
import dev.grindtrack.work.domain.WorkLogRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FocusServiceTest {

  private static final LocalDate DATE = LocalDate.of(2026, 7, 15);
  private static final OffsetDateTime STARTED_AT = OffsetDateTime.parse("2026-07-15T09:00:00Z");

  private FocusSessionRepository sessions;
  private DailyLogRepository dailyLogs;
  private WorkLogRepository workLogs;
  private FocusService service;

  @BeforeEach
  void setUp() {
    sessions = mock(FocusSessionRepository.class);
    dailyLogs = mock(DailyLogRepository.class);
    workLogs = mock(WorkLogRepository.class);
    when(sessions.save(any())).thenAnswer(inv -> inv.getArgument(0));
    service = new FocusService(sessions, dailyLogs, workLogs);
  }

  private DailyLog savedDailyLog() {
    ArgumentCaptor<DailyLog> captor = ArgumentCaptor.forClass(DailyLog.class);
    verify(dailyLogs).save(captor.capture());
    return captor.getValue();
  }

  @Test
  void roundsSessionMinutesToOneDecimalOfAnHour() {
    when(dailyLogs.findById(DATE)).thenReturn(Optional.empty());

    service.record(DATE, STARTED_AT, 25, true, FocusKind.STUDY, null, "");

    assertThat(savedDailyLog().getHours()).isEqualByComparingTo("0.4");
  }

  @Test
  void roundsHalfUpAtTheMidpoint() {
    when(dailyLogs.findById(DATE)).thenReturn(Optional.empty());

    // 45 min = 0.75 h; HALF_UP to one decimal is 0.8, not 0.7.
    service.record(DATE, STARTED_AT, 45, true, FocusKind.STUDY, null, "");

    assertThat(savedDailyLog().getHours()).isEqualByComparingTo("0.8");
  }

  @Test
  void addsToTheExistingDaysHours() {
    DailyLog existing = new DailyLog(DATE);
    existing.addHours(new BigDecimal("2.5"));
    when(dailyLogs.findById(DATE)).thenReturn(Optional.of(existing));

    service.record(DATE, STARTED_AT, 30, true, FocusKind.STUDY, null, "");

    assertThat(savedDailyLog().getHours()).isEqualByComparingTo("3.0");
  }

  @Test
  void clampsTheDaysHoursAtTwentyFour() {
    DailyLog existing = new DailyLog(DATE);
    existing.addHours(new BigDecimal("23.8"));
    when(dailyLogs.findById(DATE)).thenReturn(Optional.of(existing));

    service.record(DATE, STARTED_AT, 60, false, FocusKind.STUDY, null, "");

    assertThat(savedDailyLog().getHours()).isEqualByComparingTo("24");
  }

  @Test
  void savesTheSessionWithItsFieldsAndReturnsIt() {
    when(dailyLogs.findById(DATE)).thenReturn(Optional.empty());

    FocusSession session = service.record(DATE, STARTED_AT, 25, false, FocusKind.STUDY, null, "");

    assertThat(session.getSessionDate()).isEqualTo(DATE);
    assertThat(session.getStartedAt()).isEqualTo(STARTED_AT);
    assertThat(session.getDurationMinutes()).isEqualTo(25);
    assertThat(session.isCompleted()).isFalse();
    assertThat(session.getKind()).isEqualTo(FocusKind.STUDY);
    verify(sessions).save(session);
  }

  @Test
  void workSessionAddsToTheWorkLogNotTheDailyLog() {
    when(workLogs.findById(DATE)).thenReturn(Optional.empty());

    FocusSession session = service.record(DATE, STARTED_AT, 30, true, FocusKind.WORK, null, "");

    ArgumentCaptor<WorkLog> captor = ArgumentCaptor.forClass(WorkLog.class);
    verify(workLogs).save(captor.capture());
    assertThat(captor.getValue().getHours()).isEqualByComparingTo("0.5");
    assertThat(session.getKind()).isEqualTo(FocusKind.WORK);
    verify(dailyLogs, never()).save(any());
  }

  @Test
  void aMissingKindFallsBackToStudy() {
    when(dailyLogs.findById(DATE)).thenReturn(Optional.empty());

    FocusSession session = service.record(DATE, STARTED_AT, 30, true, null, null, "");

    assertThat(session.getKind()).isEqualTo(FocusKind.STUDY);
    verify(dailyLogs).save(any());
    verify(workLogs, never()).save(any());
  }

  @Test
  void sessionsOnDelegatesToTheRepository() {
    FocusSession session = new FocusSession(DATE, STARTED_AT, 25, true, FocusKind.STUDY);
    when(sessions.findBySessionDateOrderByStartedAt(DATE)).thenReturn(List.of(session));

    assertThat(service.sessionsOn(DATE, null)).containsExactly(session);
  }

  @Test
  void sessionsOnFiltersByKindWhenGiven() {
    FocusSession session = new FocusSession(DATE, STARTED_AT, 25, true, FocusKind.WORK);
    when(sessions.findBySessionDateAndKindOrderByStartedAt(DATE, FocusKind.WORK))
        .thenReturn(List.of(session));

    assertThat(service.sessionsOn(DATE, FocusKind.WORK)).containsExactly(session);
  }
}
