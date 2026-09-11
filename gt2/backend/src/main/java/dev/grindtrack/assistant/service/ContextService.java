package dev.grindtrack.assistant.service;

import dev.grindtrack.assistant.service.AssistantContext.DaySummary;
import dev.grindtrack.assistant.service.AssistantContext.EventSummary;
import dev.grindtrack.assistant.service.AssistantContext.Lunch;
import dev.grindtrack.assistant.service.AssistantContext.PlanItemSummary;
import dev.grindtrack.assistant.service.AssistantContext.PlannedVsActual;
import dev.grindtrack.assistant.service.AssistantContext.Quarter;
import dev.grindtrack.assistant.service.AssistantContext.TodoSummary;
import dev.grindtrack.assistant.service.AssistantContext.UpkeepSummary;
import dev.grindtrack.assistant.service.AssistantContext.Week;
import dev.grindtrack.calendar.domain.CalendarEvent;
import dev.grindtrack.calendar.domain.EventKind;
import dev.grindtrack.calendar.service.CalendarService;
import dev.grindtrack.calendar.service.UpkeepItem;
import dev.grindtrack.calendar.service.UpkeepService;
import dev.grindtrack.plan.domain.PlanItem;
import dev.grindtrack.plan.domain.PlanQuarter;
import dev.grindtrack.plan.service.PlanService;
import dev.grindtrack.todo.service.TodoService;
import dev.grindtrack.tracking.service.ReadingProgress;
import dev.grindtrack.tracking.service.ReadingService;
import dev.grindtrack.tracking.service.TrackingService;
import dev.grindtrack.work.service.WorkService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Assembles the read-only picture an assistant reasons over.
 *
 * <p>Composition only: every fact here already exists behind another service, and this one decides
 * which of them are worth the context window and puts each next to the target it should be judged
 * against. It writes nothing and it is the only thing an assistant needs to be able to read, which
 * is what keeps "the model can see my data" a much smaller claim than "the model can change it".
 */
@Service
public class ContextService {

  /**
   * The workbook's steady-state budget: 20 h/wk study, 40 h/wk work.
   *
   * <p>Carried in the context rather than left for the model to infer. A number with nothing beside
   * it invites an invented comparison, and "you did 14.5 hours" reads very differently against 20
   * than against 10.
   */
  private static final double STUDY_TARGET_HOURS = 20;

  private static final double WORK_TARGET_HOURS = 40;

  /** Far enough ahead to plan a week against, short enough not to be a data dump. */
  private static final int UPCOMING_DAYS = 14;

  /** A plan item this far out is worth knowing about; past that it is noise for a weekly review. */
  private static final int PLAN_HORIZON_DAYS = 90;

  private static final int RECENT_DAYS = 14;

  /** Q1 is Jul-Sep 2026 — the same constant the import script uses. */
  private static final LocalDate PLAN_START = LocalDate.of(2026, 7, 1);

  private final PlanService plan;
  private final TrackingService tracking;
  private final WorkService work;
  private final ReadingService reading;
  private final CalendarService calendar;
  private final UpkeepService upkeep;
  private final TodoService todos;

  public ContextService(
      PlanService plan,
      TrackingService tracking,
      WorkService work,
      ReadingService reading,
      CalendarService calendar,
      UpkeepService upkeep,
      TodoService todos) {
    this.plan = plan;
    this.tracking = tracking;
    this.work = work;
    this.reading = reading;
    this.calendar = calendar;
    this.upkeep = upkeep;
    this.todos = todos;
  }

  public AssistantContext build(LocalDate today) {
    LocalDate weekStart = TrackingService.mondayOf(today);
    LocalDate horizon = today.plusDays(UPCOMING_DAYS);

    List<PlanItem> items = plan.allItems();
    List<CalendarEvent> upcoming = calendar.range(today, horizon);
    List<CalendarEvent> thisWeek = calendar.range(weekStart, weekStart.plusDays(6));

    double studyHours =
        hours(tracking.daysBetween(weekStart, today).stream().map(d -> d.getHours()));
    double workHours = hours(work.daysBetween(weekStart, today).stream().map(w -> w.getHours()));
    double planned = plannedStudyHours(thisWeek);

    return new AssistantContext(
        today.toString(),
        currentQuarter(today),
        new Week(
            weekStart.toString(),
            studyHours,
            STUDY_TARGET_HOURS,
            workHours,
            WORK_TARGET_HOURS,
            tracking.daysBetween(weekStart, today).size()),
        new PlannedVsActual(planned, studyHours, round(studyHours - planned)),
        lunch(today),
        summarise(items.stream().filter(i -> "in_progress".equals(i.getStatus()))),
        summarise(
            items.stream()
                .filter(i -> "not_started".equals(i.getStatus()))
                .filter(i -> withinHorizon(i, today))),
        upcoming.stream()
            .map(
                e ->
                    new EventSummary(
                        e.getId(),
                        e.getEventDate().toString(),
                        e.getStartTime() == null ? null : e.getStartTime().toString(),
                        e.getTitle(),
                        e.getKind().wireValue(),
                        e.getPlanItemId()))
            .toList(),
        upkeep.due(today).stream()
            .filter(item -> item.state() != UpkeepItem.State.LATER)
            .map(
                item ->
                    new UpkeepSummary(
                        item.task().getId(),
                        item.task().getTitle(),
                        item.task().getCategory().wireValue(),
                        item.nextDue().toString(),
                        item.daysOverdue(),
                        item.state().wireValue()))
            .toList(),
        todos.list(null).stream()
            .filter(t -> !t.isDone())
            .map(
                t ->
                    new TodoSummary(
                        t.getId(),
                        t.getTitle(),
                        t.getKind(),
                        t.getDueDate() == null ? null : t.getDueDate().toString()))
            .toList(),
        recentDays(today));
  }

  /**
   * The quarter today falls in.
   *
   * <p>Matched on the window label's own dates rather than counted from a start date, because the
   * workbook is the authority on where a quarter begins and a plan can be re-imported with a
   * different shape. Absent when no quarter covers today — which is true before the plan is
   * imported, and would be true again past the end of it.
   */
  private Quarter currentQuarter(LocalDate today) {
    // Quarters are three months from the plan's first, so the nth quarter contains today
    // when today's month offset from Q1 divided by three lands on it.
    List<PlanQuarter> quarters = plan.allQuarters();
    if (quarters.isEmpty()) {
      return new Quarter(null, null, null, null, null, null);
    }
    Optional<PlanQuarter> match =
        quarters.stream()
            .sorted(Comparator.comparing(PlanQuarter::getQtr))
            .filter(q -> coversToday(q, today))
            .findFirst();
    return match
        .map(
            q ->
                new Quarter(
                    q.getQtr(),
                    q.getYearNum(),
                    q.getWindowLabel(),
                    q.getPrimaryFocus(),
                    q.getSecondaryFocus(),
                    q.getDeliverables()))
        .orElseGet(() -> new Quarter(null, null, null, null, null, null));
  }

  /**
   * Whether a quarter's window contains today.
   *
   * <p>The window label carries the months ("Jul-Sep 2026"), but parsing prose is how a screen ends
   * up wrong in a language it was never tested in. The quarter number is the reliable fact: Q1
   * starts the plan, and each is three months after the last.
   */
  private static boolean coversToday(PlanQuarter q, LocalDate today) {
    LocalDate start = PLAN_START.plusMonths(3L * (q.getQtr() - 1));
    return !today.isBefore(start) && today.isBefore(start.plusMonths(3));
  }

  private boolean withinHorizon(PlanItem item, LocalDate today) {
    LocalDate target = item.getTargetDate();
    return target != null
        && !target.isBefore(today)
        && target.isBefore(today.plusDays(PLAN_HORIZON_DAYS));
  }

  private static List<PlanItemSummary> summarise(java.util.stream.Stream<PlanItem> items) {
    return items
        .map(
            i ->
                new PlanItemSummary(
                    i.getId(),
                    i.getItemType(),
                    i.getTitle(),
                    i.getStatus(),
                    i.getTargetLabel(),
                    i.getTargetDate() == null ? null : i.getTargetDate().toString()))
        .toList();
  }

  private Lunch lunch(LocalDate today) {
    ReadingProgress progress = reading.progress(today);
    return new Lunch(
        progress.weekdayStreak(),
        progress.daysThisWeek(),
        progress.weeklyTarget(),
        progress.hoursThisWeek(),
        progress.subjects().stream().map(ReadingProgress.Subject::label).toList());
  }

  /**
   * Booked study hours in the current week.
   *
   * <p>Only blocks with both a start and an end. An all-day study block is an intention without a
   * duration: counting it as zero understates the plan and counting it as a whole day overstates it
   * by an order of magnitude, and a wrong denominator is worse than a missing one.
   */
  private static double plannedStudyHours(List<CalendarEvent> week) {
    double minutes =
        week.stream()
            .filter(e -> e.getKind() == EventKind.STUDY_BLOCK)
            .filter(e -> e.getStartTime() != null && e.getEndTime() != null)
            .mapToLong(e -> Duration.between(e.getStartTime(), e.getEndTime()).toMinutes())
            .sum();
    return round(minutes / 60.0);
  }

  private List<DaySummary> recentDays(LocalDate today) {
    LocalDate from = today.minusDays(RECENT_DAYS);
    var workByDate = work.daysBetween(from, today);
    return tracking.daysBetween(from, today).stream()
        .map(
            d ->
                new DaySummary(
                    d.getLogDate().toString(),
                    d.getHours().doubleValue(),
                    workByDate.stream()
                        .filter(w -> w.getLogDate().equals(d.getLogDate()))
                        .findFirst()
                        .map(w -> w.getHours().doubleValue())
                        .orElse(0.0),
                    d.getEnergy(),
                    d.getDid(),
                    d.getBlockers()))
        .toList();
  }

  private static double hours(java.util.stream.Stream<BigDecimal> values) {
    return round(
        values.filter(java.util.Objects::nonNull).mapToDouble(BigDecimal::doubleValue).sum());
  }

  private static double round(double value) {
    return Math.round(value * 10.0) / 10.0;
  }
}
