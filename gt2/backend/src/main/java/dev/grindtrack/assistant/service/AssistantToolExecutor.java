package dev.grindtrack.assistant.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.grindtrack.calendar.service.CalendarService;
import dev.grindtrack.plan.service.PlanService;
import dev.grindtrack.tracking.domain.FocusKind;
import dev.grindtrack.tracking.service.FocusService;
import dev.grindtrack.tracking.service.TrackingService;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * What the chat model may look up, and nothing else.
 *
 * <p>Four read tools, dispatched by name to the same services the controllers use. Every one is a
 * read: the write half of this app is not reachable from a model, by construction — there is no
 * name this class dispatches to that mutates anything.
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
  private final ObjectMapper mapper;

  public AssistantToolExecutor(
      PlanService plan,
      TrackingService tracking,
      CalendarService calendar,
      FocusService focus,
      ObjectMapper mapper) {
    this.plan = plan;
    this.tracking = tracking;
    this.calendar = calendar;
    this.focus = focus;
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
            Map.of("date", Map.of("type", "string", "description", "YYYY-MM-DD"))));
  }

  /** Dispatch one call. The result is what the model reads next, so errors are sentences. */
  public String execute(String name, Map<String, String> args) {
    try {
      return switch (name) {
        case "get_plan" -> json(planRows());
        case "get_days" -> json(dayRows(range(args)));
        case "get_calendar" -> json(eventRows(range(args)));
        case "get_focus_sessions" -> json(sessionRows(date(args.get("date"))));
        default -> "unknown tool: " + name;
      };
    } catch (ToolArgumentException e) {
      return e.getMessage();
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
