package dev.grindtrack.assistant.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.grindtrack.calendar.service.CalendarService;
import dev.grindtrack.plan.service.PlanService;
import dev.grindtrack.tracking.domain.FocusKind;
import dev.grindtrack.tracking.service.FocusService;
import dev.grindtrack.tracking.service.TrackingService;
import dev.grindtrack.web.BadRequestException;
import dev.grindtrack.web.ServiceOffException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * What the chat model may look up, and the one thing it may draft.
 *
 * <p>Four of the five tools are reads, dispatched to the same services the controllers use. The
 * fifth, {@code propose_week}, drafts a week of study blocks — and a draft is a row and a card, not
 * a booking. <strong>Nothing here writes to the calendar.</strong> The only thing that does is
 * {@code WeekPlanService.accept}, which a person reaches by pressing a button on blocks they have
 * already read, and which re-validates every one of them against the plan before writing.
 *
 * <p>That distinction is the whole design and it is worth keeping sharp: the model can put a
 * proposal in front of you, and it cannot put anything in your calendar.
 *
 * <p>{@code propose_week} delegates to the planner rather than letting the chat model invent blocks
 * itself. The planner has its own prompt — mornings before work, one to three hours, real plan item
 * ids, book for the week you actually had rather than the ideal one — and reproducing that inside a
 * conversational answer would mean maintaining it twice and getting a worse plan. It costs a second
 * model call, which is the honest price of a better draft.
 *
 * <p>Errors return as strings rather than throwing, because a tool result that says "from must be
 * YYYY-MM-DD" teaches the model to correct itself, while an exception ends the whole turn.
 */
@Component
public class AssistantToolExecutor {

  /** A bound on any date range a tool accepts: enough for "this quarter", not a context bomb. */
  private static final int MAX_RANGE_DAYS = 120;

  private final PlanService plan;
  private final TrackingService tracking;
  private final CalendarService calendar;
  private final FocusService focus;
  private final WeekPlanService weekPlan;
  private final ObjectMapper mapper;

  public AssistantToolExecutor(
      PlanService plan,
      TrackingService tracking,
      CalendarService calendar,
      FocusService focus,
      WeekPlanService weekPlan,
      ObjectMapper mapper) {
    this.plan = plan;
    this.tracking = tracking;
    this.calendar = calendar;
    this.focus = focus;
    this.weekPlan = weekPlan;
    this.mapper = mapper;
  }

  /** Name, description and JSON schema for each tool, in the API's wire shape. */
  public List<ToolSpec> specs() {
    return List.of(
        new ToolSpec(
            "get_plan",
            "Every plan item (id, type, title, status, target, year) and the quarter roadmap."
                + " Use when a question needs items beyond the in-flight ones in the context.",
            Map.of()),
        new ToolSpec(
            "get_days",
            "Daily study logs between two dates inclusive: hours, categories, focus, what"
                + " happened, wins, blockers, energy. The notes carry the why.",
            Map.of(
                "from", Map.of("type", "string", "description", "YYYY-MM-DD"),
                "to", Map.of("type", "string", "description", "YYYY-MM-DD"))),
        new ToolSpec(
            "get_calendar",
            "Calendar events between two dates inclusive, with kind and any linked plan item id.",
            Map.of(
                "from", Map.of("type", "string", "description", "YYYY-MM-DD"),
                "to", Map.of("type", "string", "description", "YYYY-MM-DD"))),
        new ToolSpec(
            "get_focus_sessions",
            "Focus sessions on one day, all kinds: start, minutes, kind, subject, takeaway.",
            Map.of("date", Map.of("type", "string", "description", "YYYY-MM-DD"))),
        new ToolSpec(
            "propose_week",
            "Draft a week of study blocks for Casey to approve. Books NOTHING: it produces a"
                + " proposal shown as a card he can accept or ignore, and the calendar is"
                + " untouched unless he presses the button. Use it when he asks you to plan,"
                + " prep or block out a week. Costs a model call, so call it once per turn and"
                + " only when planning was actually asked for. Returns the rationale and the"
                + " blocks; tell him what it drafted in your own words and that it is waiting"
                + " on him.",
            Map.of(
                "weekStart",
                Map.of(
                    "type",
                    "string",
                    "description",
                    "The MONDAY of the week to plan, YYYY-MM-DD. Work it out from today's date"
                        + " in the context; a date that is not a Monday is refused."))));
  }

  /** Dispatch one call. The result is what the model reads next, so errors are sentences. */
  public String execute(String name, Map<String, String> args) {
    try {
      return switch (name) {
        case "get_plan" -> json(planRows());
        case "get_days" -> json(dayRows(range(args)));
        case "get_calendar" -> json(eventRows(range(args)));
        case "get_focus_sessions" -> json(sessionRows(date(args.get("date"))));
        case "propose_week" -> proposeWeek(args.get("weekStart"));
        default -> "unknown tool: " + name;
      };
    } catch (ToolArgumentException e) {
      return e.getMessage();
    }
  }

  /**
   * Draft a week and hand the model back what it drafted.
   *
   * <p>Every failure is a sentence rather than an exception for the usual reason, and here it
   * matters more than usual: "weekStart must be a Monday" is something the model can fix by itself
   * on the next round, and a thrown error would end a turn the person is waiting on.
   */
  private String proposeWeek(String weekStart) {
    LocalDate monday = date(weekStart);
    if (monday.getDayOfWeek() != DayOfWeek.MONDAY) {
      return "weekStart must be a Monday; " + weekStart + " is a " + monday.getDayOfWeek();
    }
    try {
      WeekPlanService.Draft draft = weekPlan.propose(monday);
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("weekStart", draft.weekStart());
      result.put("rationale", draft.rationale());
      result.put("blocks", draft.blocks());
      result.put("status", "drafted and waiting for Casey to accept it — nothing has been booked");
      return json(result);
    } catch (ServiceOffException | BadRequestException e) {
      return "could not draft that week: " + e.getMessage();
    }
  }

  private List<Map<String, Object>> planRows() {
    return plan.allItems().stream()
        .map(
            i -> {
              Map<String, Object> row = new LinkedHashMap<>();
              row.put("id", i.getId());
              row.put("type", i.getItemType());
              row.put("title", i.getTitle());
              row.put("status", i.getStatus());
              row.put("target", i.getTargetLabel());
              row.put("yearNum", i.getYearNum());
              return row;
            })
        .toList();
  }

  private List<Map<String, Object>> dayRows(LocalDate[] range) {
    return tracking.daysBetween(range[0], range[1]).stream()
        .map(
            d -> {
              Map<String, Object> row = new LinkedHashMap<>();
              row.put("date", d.getLogDate().toString());
              row.put("hours", d.getHours());
              row.put("categories", d.categoryList());
              row.put("focus", d.getFocus());
              row.put("did", d.getDid());
              row.put("wins", d.getWins());
              row.put("blockers", d.getBlockers());
              row.put("energy", d.getEnergy());
              return row;
            })
        .toList();
  }

  private List<Map<String, Object>> eventRows(LocalDate[] range) {
    return calendar.range(range[0], range[1]).stream()
        .map(
            e -> {
              Map<String, Object> row = new LinkedHashMap<>();
              row.put("date", e.getEventDate().toString());
              row.put("title", e.getTitle());
              row.put("kind", e.getKind());
              row.put("startTime", e.getStartTime() == null ? null : e.getStartTime().toString());
              row.put("planItemId", e.getPlanItemId());
              return row;
            })
        .toList();
  }

  private List<Map<String, Object>> sessionRows(LocalDate date) {
    return Arrays.stream(FocusKind.values())
        .flatMap(kind -> focus.sessionsOn(date, kind).stream())
        .map(
            s -> {
              Map<String, Object> row = new LinkedHashMap<>();
              row.put("startedAt", s.getStartedAt().toString());
              row.put("minutes", s.getDurationMinutes());
              row.put("kind", s.getKind().wireValue());
              row.put("topic", s.getTopic());
              row.put("takeaway", s.getTakeaway());
              return row;
            })
        .toList();
  }

  private LocalDate[] range(Map<String, String> args) {
    LocalDate from = date(args.get("from"));
    LocalDate to = date(args.get("to"));
    if (to.isBefore(from)) {
      throw new ToolArgumentException("to must not be before from");
    }
    if (to.toEpochDay() - from.toEpochDay() > MAX_RANGE_DAYS) {
      throw new ToolArgumentException("the range may cover at most " + MAX_RANGE_DAYS + " days");
    }
    return new LocalDate[] {from, to};
  }

  private static LocalDate date(String value) {
    if (value == null || value.isBlank()) {
      throw new ToolArgumentException("a date argument is missing; dates are YYYY-MM-DD");
    }
    try {
      return LocalDate.parse(value.trim());
    } catch (DateTimeParseException e) {
      throw new ToolArgumentException("dates must be YYYY-MM-DD, got: " + value);
    }
  }

  private String json(Object rows) {
    try {
      return mapper.writeValueAsString(rows);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("could not serialize a tool result", e);
    }
  }

  /** Thrown for bad tool arguments; caught in {@link #execute} and returned as the tool result. */
  private static final class ToolArgumentException extends RuntimeException {
    ToolArgumentException(String message) {
      super(message);
    }
  }

  /**
   * @param properties JSON-schema property map; empty means a no-argument tool
   */
  public record ToolSpec(String name, String description, Map<String, Object> properties) {}
}
