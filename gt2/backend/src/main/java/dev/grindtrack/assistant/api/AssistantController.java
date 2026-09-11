package dev.grindtrack.assistant.api;

import dev.grindtrack.assistant.api.AssistantDtos.ToolDescription;
import dev.grindtrack.assistant.service.AssistantContext;
import dev.grindtrack.assistant.service.ContextService;
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
 * The read surface an assistant reasons over.
 *
 * <p>Read-only, deliberately and for now completely. Every endpoint here is a GET and there is no
 * write path, because the first thing an assistant with a write path does wrong is mark the wrong
 * plan item done — and unlike a wrong sentence, that one is invisible until the next review.
 *
 * <p>Nothing here calls a model. The context is useful on its own: paste it into a conversation and
 * ask for a week. Wiring it to the API is a separate change with a separate cost, and this half is
 * the half that has to be right either way.
 */
@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

  private final ContextService context;
  private final WeeklyReviewService reviews;

  public AssistantController(ContextService context, WeeklyReviewService reviews) {
    this.context = context;
    this.reviews = reviews;
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
   * Is the assistant on, and what has it cost this month. The week tab decides its UI from this.
   */
  @GetMapping("/status")
  public WeeklyReviewService.Status status() {
    return reviews.status();
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
