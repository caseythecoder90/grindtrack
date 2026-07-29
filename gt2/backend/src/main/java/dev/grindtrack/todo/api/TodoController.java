package dev.grindtrack.todo.api;

import dev.grindtrack.todo.api.TodoDtos.TodoCreateRequest;
import dev.grindtrack.todo.api.TodoDtos.TodoResponse;
import dev.grindtrack.todo.api.TodoDtos.TodoUpdateRequest;
import dev.grindtrack.todo.domain.Todo;
import dev.grindtrack.todo.domain.TodoRepository;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The todo list: short-lived actionable items, filterable to work or personal. Mirrors the shape of
 * {@code WorkController} — flat CRUD over one table, validation inline, no service layer, because
 * there is no logic here beyond field checks.
 */
@RestController
@RequestMapping("/api/todos")
public class TodoController {

  private static final int MAX_TITLE_CHARS = 300;
  private static final Set<String> KINDS = Set.of("work", "personal");

  private final TodoRepository todos;

  public TodoController(TodoRepository todos) {
    this.todos = todos;
  }

  @GetMapping
  public List<TodoResponse> list(@RequestParam(required = false) String kind) {
    List<Todo> found =
        kind == null
            ? todos.findAllByOrderByDoneAscSortOrderAscIdAsc()
            : todos.findByKindOrderByDoneAscSortOrderAscIdAsc(requireKind(kind));
    return found.stream().map(TodoResponse::from).toList();
  }

  @PostMapping
  public TodoResponse create(@RequestBody TodoCreateRequest body) {
    String title = requireTitle(body.title());
    String kind = body.kind() == null ? "personal" : requireKind(body.kind());
    // New items go to the end of their list.
    int nextOrder = (int) todos.count();
    Todo todo = new Todo(title, kind, optionalDate(body.dueDate()), nextOrder);
    return TodoResponse.from(todos.save(todo));
  }

  @PatchMapping("/{id}")
  public ResponseEntity<?> update(@PathVariable Long id, @RequestBody TodoUpdateRequest body) {
    return todos
        .findById(id)
        .<ResponseEntity<?>>map(
            todo -> {
              if (body.title() != null) {
                todo.setTitle(requireTitle(body.title()));
              }
              if (body.kind() != null) {
                todo.setKind(requireKind(body.kind()));
              }
              if (body.done() != null) {
                todo.setDone(body.done());
              }
              if (Boolean.TRUE.equals(body.clearDueDate())) {
                todo.setDueDate(null);
              } else if (body.dueDate() != null) {
                todo.setDueDate(optionalDate(body.dueDate()));
              }
              if (body.sortOrder() != null) {
                todo.setSortOrder(body.sortOrder());
              }
              return ResponseEntity.ok(TodoResponse.from(todos.save(todo)));
            })
        .orElseGet(() -> ResponseEntity.status(404).body(Map.of("error", "no such todo")));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<?> delete(@PathVariable Long id) {
    todos.deleteById(id);
    return ResponseEntity.ok(Map.of("deleted", id));
  }

  // ---------- validation ----------

  private static String requireTitle(String value) {
    String title = value == null ? "" : value.trim();
    if (title.isBlank() || title.length() > MAX_TITLE_CHARS) {
      throw new BadRequest("a todo needs a title (max " + MAX_TITLE_CHARS + " chars)");
    }
    return title;
  }

  private static String requireKind(String value) {
    if (!KINDS.contains(value)) {
      throw new BadRequest("kind must be work or personal");
    }
    return value;
  }

  private static LocalDate optionalDate(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(value);
    } catch (DateTimeParseException e) {
      throw new BadRequest("dueDate must be YYYY-MM-DD");
    }
  }

  @ExceptionHandler(BadRequest.class)
  ResponseEntity<Map<String, String>> onBadRequest(BadRequest e) {
    return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
  }

  private static final class BadRequest extends RuntimeException {
    BadRequest(String message) {
      super(message);
    }
  }
}
