package dev.grindtrack.assistant.api;

import dev.grindtrack.assistant.api.AssistantDtos.ToolDescription;
import dev.grindtrack.assistant.service.AssistantContext;
import dev.grindtrack.assistant.service.ContextService;
import dev.grindtrack.assistant.service.WeekPlanService;
import dev.grindtrack.assistant.service.WeeklyReviewService;
import dev.grindtrack.web.Requests;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * What the assistant reads, what it drafts, and the one thing it can be told to write.
 *
 * <p>This began read-only — every endpoint a GET — because the first thing an assistant with a
 * write path does wrong is mark the wrong plan item done, and unlike a wrong sentence that one is
 * invisible until the next review. That rule has not been dropped so much as made explicit: a model
 * still writes nothing. It drafts into {@code assistant_reports}, and a person reads the draft and
 * calls {@code /week-plan/accept}, which books what was <em>stored and shown</em> rather than
 * anything a request body carries.
 *
 * <p>So the money and the mutation are in different calls on purpose. Drafting spends and writes
 * nothing; accepting writes and spends nothing. Neither can be mistaken for the other.
 */
@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

  private final ContextService context;
  private final WeeklyReviewService reviews;
  private final WeekPlanService weekPlans;

  public AssistantController(
      ContextService context, WeeklyReviewService reviews, WeekPlanService weekPlans) {
    this.context = context;
    this.reviews = reviews;
    this.weekPlans = weekPlans;
  }

  /**
   * Everything needed to answer "what should I do this week", in one request.
   *
   * @param today overrides the date the context is built for. For reproducing a Friday review on a
   *     Saturday, and for tests — not for pretending it is next month
   */
  @GetMapping("/context")
  public AssistantContext context(@RequestParam(required = false) String today) {
    LocalDate on = Requests.optionalDate(today, "today must be YYYY-MM-DD");
    return context.build(on == null ? LocalDate.now() : on);
  }

  /**
   * Draft (or redraft) the review for a week. The one endpoint here that spends money — about six
   * cents a click — which is why it is a POST and why the response says what it cost.
   *
   * @param weekStart a Monday; absent means the current week
   */
  @PostMapping("/reviews")
  public WeeklyReviewService.Report generateReview(
      @RequestParam(required = false) String weekStart) {
    return reviews.generate(week(weekStart));
  }

  /** The stored draft for a week, or an empty body when none has been generated. */
  @GetMapping("/reviews")
  public WeeklyReviewService.Report review(@RequestParam(required = false) String weekStart) {
    return reviews.find(week(weekStart)).orElse(null);
  }

  /**
   * Propose a week of study blocks. Spends money, writes nothing to the calendar — booking is a
   * separate call, because a proposal a person has not read is not a plan.
   */
  @PostMapping("/week-plan")
  public WeekPlanService.Draft proposeWeek(@RequestParam(required = false) String weekStart) {
    return weekPlans.propose(nextWeek(weekStart));
  }

  /** The drafted plan for a week, or an empty body when none has been proposed. */
  @GetMapping("/week-plan")
  public WeekPlanService.Draft weekPlan(@RequestParam(required = false) String weekStart) {
    return weekPlans.find(nextWeek(weekStart)).orElse(null);
  }

  /**
   * Book the drafted blocks. The only endpoint in this package that writes anything, and it writes
   * what was stored and shown — never what the request body says.
   */
  @PostMapping("/week-plan/accept")
  public WeekPlanService.Accepted acceptWeek(@RequestParam(required = false) String weekStart) {
    return weekPlans.accept(nextWeek(weekStart));
  }

  /**
   * Is the assistant on, and what has it cost this month. The week tab decides its UI from this.
   */
  @GetMapping("/status")
  public WeeklyReviewService.Status status() {
    return reviews.status();
  }

  /** Planning defaults to the week ahead; reviewing defaults to the week just lived. */
  private static LocalDate nextWeek(String weekStart) {
    LocalDate parsed = Requests.optionalDate(weekStart, "weekStart must be YYYY-MM-DD");
    return parsed != null ? parsed : week(null).plusWeeks(1);
  }

  private static LocalDate week(String weekStart) {
    LocalDate parsed = Requests.optionalDate(weekStart, "weekStart must be YYYY-MM-DD");
    return parsed != null
        ? parsed
        : LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
  }

  /**
   * The read tools, described.
   *
   * <p>A manifest rather than prose in a README: whatever ends up driving the model — a Java tool
   * loop here, or a conversation somewhere else — needs the same list, and one that is generated
   * from the app cannot drift from it the way a hand-written one does.
   */
  @GetMapping("/tools")
  public List<ToolDescription> tools() {
    return List.of(
        new ToolDescription(
            "getContext",
            "GET",
            "/api/assistant/context",
            "The whole picture: current quarter, this week against target, planned versus actual"
                + " study hours, lunch streak, in-flight and upcoming plan items, the next 14 days"
                + " of calendar, overdue upkeep, open todos, and the last 14 logged days."),
        new ToolDescription(
            "getPlan", "GET", "/api/plan", "All plan items, quarters and reference sheets."),
        new ToolDescription(
            "getStats", "GET", "/api/stats", "Hour totals, streaks and per-day series by scope."),
        new ToolDescription(
            "getFocusSessions",
            "GET",
            "/api/focus/sessions?date=YYYY-MM-DD",
            "Focus sessions on a day, with kind, duration and lunch subject."),
        new ToolDescription(
            "getReadingProgress",
            "GET",
            "/api/focus/reading",
            "The lunch habit: weekday streak, days this week against the target of four, what the"
                + " hours went into, and the written takeaways."),
        new ToolDescription(
            "getCalendar",
            "GET",
            "/api/calendar?from=YYYY-MM-DD&to=YYYY-MM-DD",
            "Events in a date range. Study blocks may carry the plan item they were booked for."),
        new ToolDescription(
            "getUpkeep",
            "GET",
            "/api/upkeep",
            "Recurring tasks with a derived next-due date and how overdue each is."),
        new ToolDescription(
            "getTodos", "GET", "/api/todos", "Open and completed todos, filterable by kind."));
  }
}
