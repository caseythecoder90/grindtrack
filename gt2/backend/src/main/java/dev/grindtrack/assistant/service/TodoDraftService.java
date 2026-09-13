package dev.grindtrack.assistant.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.grindtrack.assistant.domain.AssistantReport;
import dev.grindtrack.assistant.domain.AssistantReportRepository;
import dev.grindtrack.config.AssistantProperties;
import dev.grindtrack.todo.service.TodoService;
import dev.grindtrack.web.BadRequestException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drafting todos in conversation, and adding them once a person says yes.
 *
 * <p>The same two halves as {@link DayLogService}: the chat turn is the drafter, nothing reaches
 * the todo list without {@link #accept}, and accept re-reads the stored draft rather than a request
 * body. Two things differ. A day's batch <em>accumulates</em> — "add a todo" twice before either is
 * accepted is one card with two rows, not a second card replacing the first — and accepting
 * <em>removes</em> the draft, so pressing the button twice cannot add the todos twice.
 */
@Service
public class TodoDraftService {

  private static final Set<String> KINDS = Set.of("work", "personal");
  private static final int MAX_ITEMS = 20;
  private static final int MAX_TITLE = 300;

  private final TodoService todos;
  private final AssistantReportRepository reports;
  private final AssistantProperties props;
  private final ObjectMapper mapper;

  public TodoDraftService(
      TodoService todos,
      AssistantReportRepository reports,
      AssistantProperties props,
      ObjectMapper mapper) {
    this.todos = todos;
    this.reports = reports;
    this.props = props;
    this.mapper = mapper;
  }

  /**
   * Add items to the day's draft. Writes a report row; writes no todo.
   *
   * <p>Every failure is a sentence the tool can hand back for the model to correct. Titles are
   * trimmed, an empty one is refused, a duplicate of one already drafted today is dropped rather
   * than doubled, and an unknown kind becomes personal because that is what the form does too.
   */
  @Transactional
  public Draft propose(LocalDate date, List<TodoDraft.Item> items) {
    if (items == null || items.isEmpty()) {
      throw new BadRequestException("no todos to draft — say what needs doing");
    }
    List<TodoDraft.Item> clean = new ArrayList<>();
    for (TodoDraft.Item item : items) {
      String title = item.title() == null ? "" : item.title().trim();
      if (title.isEmpty()) {
        throw new BadRequestException("a todo needs a title");
      }
      if (title.length() > MAX_TITLE) {
        throw new BadRequestException("a todo title is at most " + MAX_TITLE + " characters");
      }
      String kind = item.kind() != null && KINDS.contains(item.kind()) ? item.kind() : "personal";
      String due = null;
      if (item.dueDate() != null && !item.dueDate().isBlank()) {
        try {
          due = LocalDate.parse(item.dueDate().trim()).toString();
        } catch (DateTimeParseException e) {
          throw new BadRequestException("due dates must be YYYY-MM-DD, got: " + item.dueDate());
        }
      }
      clean.add(new TodoDraft.Item(title, kind, due));
    }

    AssistantReport report =
        reports
            .findByKindAndWeekStart(AssistantReport.KIND_TODO_BATCH, date)
            .orElseGet(() -> new AssistantReport(AssistantReport.KIND_TODO_BATCH, date));
    List<TodoDraft.Item> merged =
        new ArrayList<>(
            report.getDraftJson() == null ? List.of() : parse(report.getDraftJson()).items());
    for (TodoDraft.Item item : clean) {
      boolean already = merged.stream().anyMatch(m -> m.title().equalsIgnoreCase(item.title()));
      if (!already) {
        merged.add(item);
      }
    }
    if (merged.size() > MAX_ITEMS) {
      throw new BadRequestException("that is more than " + MAX_ITEMS + " todos in one day's draft");
    }
    // Tokens are zero on purpose: the chat turn that made this draft carries its own bill.
    report.replaceDraft(props.model(), 0, 0, toJson(new TodoDraft(merged)));
    reports.save(report);
    return toView(report, merged);
  }

  @Transactional(readOnly = true)
  public Optional<Draft> find(LocalDate date) {
    return reports
        .findByKindAndWeekStart(AssistantReport.KIND_TODO_BATCH, date)
        .map(r -> toView(r, parse(r.getDraftJson()).items()));
  }

  /**
   * Add the drafted todos. Reads the stored draft, never the request, and deletes the draft once
   * the todos exist — so a second press finds nothing to add.
   */
  @Transactional
  public Accepted accept(LocalDate date) {
    AssistantReport report =
        reports
            .findByKindAndWeekStart(AssistantReport.KIND_TODO_BATCH, date)
            .orElseThrow(() -> new BadRequestException("no drafted todos for " + date));
    List<TodoDraft.Item> items = parse(report.getDraftJson()).items();
    for (TodoDraft.Item item : items) {
      todos.create(
          item.title(),
          item.kind(),
          item.dueDate() == null ? null : LocalDate.parse(item.dueDate()));
    }
    reports.delete(report);
    return new Accepted(date.toString(), items.size());
  }

  private TodoDraft parse(String json) {
    try {
      return mapper.readValue(json, TodoDraft.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("stored draft no longer parses as TodoDraft", e);
    }
  }

  private String toJson(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("could not serialize", e);
    }
  }

  private static Draft toView(AssistantReport r, List<TodoDraft.Item> items) {
    return new Draft(
        r.getWeekStart().toString(),
        r.getGeneratedAt() == null ? null : r.getGeneratedAt().toString(),
        items);
  }

  public record Draft(String date, String generatedAt, List<TodoDraft.Item> items) {}

  public record Accepted(String date, int added) {}
}
