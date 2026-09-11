package dev.grindtrack.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.grindtrack.calendar.service.CalendarService;
import dev.grindtrack.plan.service.PlanService;
import dev.grindtrack.tracking.service.FocusService;
import dev.grindtrack.tracking.service.TrackingService;
import dev.grindtrack.web.ServiceOffException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The tool surface, and the one line in it that matters.
 *
 * <p>Four tools read. The fifth drafts. Nothing here books, and that is the property worth a test
 * rather than a comment: the difference between "the model can put a proposal in front of you" and
 * "the model can put things in your calendar" is the entire safety argument for letting it near the
 * calendar at all.
 */
class AssistantToolExecutorTest {

  private static final LocalDate MONDAY = LocalDate.of(2026, 9, 14);

  private PlanService plan;
  private CalendarService calendar;
  private WeekPlanService weekPlan;
  private AssistantToolExecutor executor;

  @BeforeEach
  void setUp() {
    plan = mock(PlanService.class);
    calendar = mock(CalendarService.class);
    weekPlan = mock(WeekPlanService.class);
    executor =
        new AssistantToolExecutor(
            plan,
            mock(TrackingService.class),
            calendar,
            mock(FocusService.class),
            weekPlan,
            new ObjectMapper());
  }

  private static WeekPlanService.Draft draft() {
    return new WeekPlanService.Draft(
        MONDAY.toString(),
        "2026-09-11T09:00Z",
        "claude-opus-5",
        3000,
        600,
        "Four mornings against CKA, which is nearest.",
        List.of(new WeekPlanDraft.Block(MONDAY.toString(), "06:00", "07:30", "CKA · etcd", 7L)));
  }

  @Test
  void theToolSurfaceIsFourReadsAndOneDraft() {
    assertThat(executor.specs())
        .extracting(AssistantToolExecutor.ToolSpec::name)
        .containsExactly(
            "get_plan", "get_days", "get_calendar", "get_focus_sessions", "propose_week");
  }

  /** The whole point. Drafting is a row and a card; the calendar is not touched by a model. */
  @Test
  void proposingDraftsAndBooksNothing() {
    when(weekPlan.propose(MONDAY)).thenReturn(draft());

    String result = executor.execute("propose_week", Map.of("weekStart", MONDAY.toString()));

    assertThat(result).contains("\"weekStart\":\"2026-09-14\"").contains("CKA · etcd");
    // And says so to the model, so it cannot tell Casey his week is booked.
    assertThat(result).contains("nothing has been booked");
    verifyNoInteractions(calendar);
  }

  /**
   * A refusal the model can act on, rather than an exception that ends a turn someone is waiting
   * on. It must also not have spent a model call working that out.
   */
  @Test
  void aWeekStartThatIsNotAMondayIsRefusedBeforeItCostsAnything() {
    String result = executor.execute("propose_week", Map.of("weekStart", "2026-09-16"));

    assertThat(result).contains("must be a Monday").contains("WEDNESDAY");
    verify(weekPlan, never()).propose(any());
    verifyNoInteractions(calendar);
  }

  @Test
  void anUnreadableDateIsASentenceNotAStackTrace() {
    assertThat(executor.execute("propose_week", Map.of("weekStart", "next monday")))
        .contains("YYYY-MM-DD");
    verify(weekPlan, never()).propose(any());
  }

  /** With no API key the planner refuses; the chat turn carries on and says why. */
  @Test
  void theAssistantBeingOffIsAnAnswerNotAFailedTurn() {
    when(weekPlan.propose(MONDAY)).thenThrow(new ServiceOffException("the assistant is off"));

    assertThat(executor.execute("propose_week", Map.of("weekStart", MONDAY.toString())))
        .contains("could not draft that week")
        .contains("the assistant is off");
  }

  @Test
  void anUnknownToolNameSaysSoRatherThanThrowing() {
    assertThat(executor.execute("book_the_whole_year", Map.of())).contains("unknown tool");
    verifyNoInteractions(calendar);
  }
}
