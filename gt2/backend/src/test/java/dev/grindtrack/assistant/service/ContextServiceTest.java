package dev.grindtrack.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import dev.grindtrack.calendar.domain.CalendarEvent;
import dev.grindtrack.calendar.domain.EventKind;
import dev.grindtrack.calendar.service.CalendarService;
import dev.grindtrack.calendar.service.UpkeepService;
import dev.grindtrack.plan.domain.PlanQuarter;
import dev.grindtrack.plan.service.PlanService;
import dev.grindtrack.todo.service.TodoService;
import dev.grindtrack.tracking.domain.DailyLog;
import dev.grindtrack.tracking.service.ReadingProgress;
import dev.grindtrack.tracking.service.ReadingService;
import dev.grindtrack.tracking.service.TrackingService;
import dev.grindtrack.work.service.WorkService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** The two numbers this service computes that nothing else in the app does. */
@ExtendWith(MockitoExtension.class)
class ContextServiceTest {

  /** A Tuesday in Q1 (Jul-Sep 2026). */
  private static final LocalDate TODAY = LocalDate.of(2026, 9, 8);

  @Mock private PlanService plan;
  @Mock private TrackingService tracking;
  @Mock private WorkService work;
  @Mock private ReadingService reading;
  @Mock private CalendarService calendar;
  @Mock private UpkeepService upkeep;
  @Mock private TodoService todos;

  private ContextService service;

  @BeforeEach
  void setUp() {
    service = new ContextService(plan, tracking, work, reading, calendar, upkeep, todos);
    lenient().when(plan.allItems()).thenReturn(List.of());
    lenient().when(plan.allQuarters()).thenReturn(List.of());
    lenient().when(tracking.daysBetween(any(), any())).thenReturn(List.of());
    lenient().when(work.daysBetween(any(), any())).thenReturn(List.of());
    lenient().when(calendar.range(any(), any())).thenReturn(List.of());
    lenient().when(upkeep.due(any())).thenReturn(List.of());
    lenient().when(todos.list(null)).thenReturn(List.of());
    lenient()
        .when(reading.progress(any()))
        .thenReturn(new ReadingProgress(0, 0, 0, 4, 0, 0, 0, List.of(), List.of()));
  }

  private static CalendarEvent studyBlock(LocalDate date, LocalTime from, LocalTime to) {
    return new CalendarEvent("CKA course + labs", EventKind.STUDY_BLOCK, date, from, to);
  }

  private static DailyLog day(LocalDate date, String hours) {
    DailyLog log = new DailyLog(date);
    log.setHours(new BigDecimal(hours));
    return log;
  }

  @Test
  void plannedHoursCountOnlyStudyBlocksThatHaveBothEnds() {
    LocalDate monday = LocalDate.of(2026, 9, 7);
    when(calendar.range(monday, monday.plusDays(6)))
        .thenReturn(
            List.of(
                studyBlock(monday, LocalTime.of(6, 0), LocalTime.of(8, 0)), // 2h
                studyBlock(TODAY, LocalTime.of(6, 0), LocalTime.of(7, 30)), // 1.5h
                // An all-day study block is an intention with no duration. Counting it as
                // zero understates the plan; counting it as a day overstates it wildly.
                studyBlock(TODAY.plusDays(1), null, null),
                // A work block is not study time.
                new CalendarEvent(
                    "standups",
                    EventKind.WORK_BLOCK,
                    TODAY,
                    LocalTime.of(9, 0),
                    LocalTime.of(17, 0))));

    assertThat(service.build(TODAY).plannedVsActual().plannedHours()).isEqualTo(3.5);
  }

  @Test
  void differenceIsActualMinusPlannedSoASurplusIsPositive() {
    LocalDate monday = LocalDate.of(2026, 9, 7);
    when(calendar.range(monday, monday.plusDays(6)))
        .thenReturn(List.of(studyBlock(monday, LocalTime.of(6, 0), LocalTime.of(8, 0))));
    when(tracking.daysBetween(monday, TODAY))
        .thenReturn(List.of(day(monday, "2.5"), day(TODAY, "1.0")));

    var gap = service.build(TODAY).plannedVsActual();
    assertThat(gap.plannedHours()).isEqualTo(2.0);
    assertThat(gap.actualHours()).isEqualTo(3.5);
    assertThat(gap.differenceHours()).isEqualTo(1.5);
  }

  @Test
  void theCurrentQuarterIsTheOneWhoseThreeMonthWindowContainsToday() {
    when(plan.allQuarters())
        .thenReturn(
            List.of(
                new PlanQuarter(1, "Jul-Sep 2026", 1, "CKAD final prep", "", "", ""),
                new PlanQuarter(2, "Oct-Dec 2026", 1, "CKA", "", "", ""),
                new PlanQuarter(3, "Jan-Mar 2027", 1, "CKS", "", "", "")));

    assertThat(service.build(TODAY).quarter().number()).isEqualTo(1);
    assertThat(service.build(LocalDate.of(2026, 11, 2)).quarter().number()).isEqualTo(2);
    assertThat(service.build(LocalDate.of(2027, 3, 31)).quarter().number()).isEqualTo(3);
  }

  @Test
  void aDateOutsideEveryQuarterReportsNoQuarterRatherThanGuessing() {
    when(plan.allQuarters())
        .thenReturn(List.of(new PlanQuarter(1, "Jul-Sep 2026", 1, "CKAD", "", "", "")));
    // Before the plan starts, and after it ends, are both real states — the second one
    // arrives in 2031 — and neither should be reported as "you are in Q1".
    assertThat(service.build(LocalDate.of(2026, 6, 30)).quarter().number()).isNull();
    assertThat(service.build(LocalDate.of(2031, 1, 1)).quarter().number()).isNull();
  }

  @Test
  void anEmptyPlanReportsNoQuarterAndDoesNotThrow() {
    assertThat(service.build(TODAY).quarter().number()).isNull();
  }

  @Test
  void targetsTravelWithTheActuals() {
    // A number with nothing beside it invites the model to invent the comparison.
    var week = service.build(TODAY).week();
    assertThat(week.studyTarget()).isEqualTo(20);
    assertThat(week.workTarget()).isEqualTo(40);
  }
}
