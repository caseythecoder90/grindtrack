package dev.grindtrack.assistant.service;

import java.util.List;

/**
 * Everything an assistant needs to answer "what should I do this week", in one shape.
 *
 * <p>Its own file beside {@link ContextService}, like {@code Stats} and {@code ReadingProgress}
 * beside theirs. Nothing here is a projection of one entity — every field is composed.
 *
 * <p>There is deliberately no assembled-at timestamp. {@code today} carries the freshness that any
 * rule in the prompt actually reasons over, and a per-request clock reading would sit inside the
 * cached prefix and change on every call — the canonical way to pay for prompt caching and never
 * get a hit. See {@code AnthropicChatModel} for the breakpoint that depends on this.
 *
 * <p>Two rules govern what is in it. It is <strong>compact</strong>: 194 plan items is most of a
 * context window spent on rows that are years away, so only what is in flight or near is included.
 * And every actual arrives <strong>next to its target</strong> — hours against the weekly goal,
 * lunch days against the four — because a number with nothing to compare it to invites the model to
 * invent the comparison.
 *
 * @param plannedVsActual the gap between the mornings booked and the mornings that happened — the
 *     one figure nothing else in the app computes, and the reason the calendar records intent
 */
public record AssistantContext(
    String today,
    Quarter quarter,
    Week week,
    PlannedVsActual plannedVsActual,
    Lunch lunch,
    List<PlanItemSummary> planInFlight,
    List<PlanItemSummary> planUpcoming,
    List<EventSummary> upcomingEvents,
    List<UpkeepSummary> upkeepDue,
    List<TodoSummary> openTodos,
    List<DaySummary> recentDays,
    Recovery recovery) {

  /** Where the plan says you are. Derived from today's date, not stored anywhere. */
  public record Quarter(
      Integer number,
      Integer yearNum,
      String windowLabel,
      String primaryFocus,
      String secondaryFocus,
      String deliverables) {}

  /**
   * This week so far.
   *
   * @param studyTarget the weekly target, carried so the model does not have to be told — and so
   *     that changing it changes every prompt at once
   */
  public record Week(
      String weekStart,
      double studyHours,
      double studyTarget,
      double workHours,
      double workTarget,
      int daysLogged) {}

  /**
   * Booked study time against logged study time, for the current week.
   *
   * @param plannedHours from calendar study blocks that have a start and an end — an all-day block
   *     is an intention without a duration and would otherwise count as zero or as a whole day,
   *     both of which are wrong
   */
  public record PlannedVsActual(double plannedHours, double actualHours, double differenceHours) {}

  public record Lunch(
      int weekdayStreak,
      int daysThisWeek,
      int weeklyTarget,
      double hoursThisWeek,
      List<String> subjects) {}

  /** A plan row, trimmed to what a decision needs. Details and notes are deliberately absent. */
  public record PlanItemSummary(
      Long id, String type, String title, String status, String targetLabel, String targetDate) {}

  public record EventSummary(
      Long id, String date, String startTime, String title, String kind, Long planItemId) {}

  public record UpkeepSummary(
      Long id, String title, String category, String nextDue, long daysOverdue, String state) {}

  public record TodoSummary(Long id, String title, String kind, String dueDate) {}

  /**
   * Where recovery stands today. Absent (null) when no sobriety date is configured — the model is
   * told nothing rather than something made up.
   *
   * @param daysSober counted from the sobriety date to today, the date itself being day one
   */
  public record Recovery(String sobrietyDate, long daysSober) {}

  /** One logged day, with what was written on it — the part that carries the why. */
  public record DaySummary(
      String date,
      double studyHours,
      double workHours,
      Integer energy,
      String did,
      String blockers) {}
}
