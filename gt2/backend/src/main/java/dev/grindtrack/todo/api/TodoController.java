package dev.grindtrack.todo.api;

import dev.grindtrack.todo.api.TodoDtos.TodoCreateRequest;
import dev.grindtrack.todo.api.TodoDtos.TodoResponse;
import dev.grindtrack.todo.api.TodoDtos.TodoUpdateRequest;
import dev.grindtrack.todo.service.TodoService;
import dev.grindtrack.web.Requests;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The todo list: short-lived actionable items, filterable to work or personal.
 *
 * <p>Flat CRUD over one table. The validation here is all shape — a title within its column, a kind
 * from the closed set the schema allows — which is why it stays in the controller while {@link
 * TodoService} owns persistence.
 */
@RestController
@RequestMapping("/api/todos")
public class TodoController {

  private static final int MAX_TITLE_CHARS = 300;
  private static final Set<String> KINDS = Set.of("work", "personal");
  private static final String KIND_MESSAGE = "kind must be work or personal";

  private final TodoService todos;

  public TodoController(TodoService todos) {
    this.todos = todos;
  }

  @GetMapping
  public List<TodoResponse> list(@RequestParam(required = false) String kind) {
    String filter = kind == null ? null : Requests.requireOneOf(kind, KINDS, KIND_MESSAGE);
    return todos.list(filter).stream().map(TodoResponse::from).toList();
  }

  @PostMapping
  public TodoResponse create(@RequestBody TodoCreateRequest body) {
    String title = Requests.requireText(body.title(), "todo needs a title", MAX_TITLE_CHARS);
    String kind =
        body.kind() == null ? "personal" : Requests.requireOneOf(body.kind(), KINDS, KIND_MESSAGE);
    return TodoResponse.from(
        todos.create(
            title, kind, Requests.optionalDate(body.dueDate(), "dueDate must be YYYY-MM-DD")));
  }

  @PatchMapping("/{id}")
  public ResponseEntity<?> update(@PathVariable Long id, @RequestBody TodoUpdateRequest body) {
    String title =
        body.title() == null
            ? null
            : Requests.requireText(body.title(), "todo needs a title", MAX_TITLE_CHARS);
    String kind =
        body.kind() == null ? null : Requests.requireOneOf(body.kind(), KINDS, KIND_MESSAGE);

    return todos
        .update(
            id,
            title,
            kind,
            body.done(),
            Boolean.TRUE.equals(body.clearDueDate()),
            Requests.optionalDate(body.dueDate(), "dueDate must be YYYY-MM-DD"),
            body.sortOrder())
        .<ResponseEntity<?>>map(todo -> ResponseEntity.ok(TodoResponse.from(todo)))
        .orElseGet(() -> ResponseEntity.status(404).body(Map.of("error", "no such todo")));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<?> delete(@PathVariable Long id) {
    todos.delete(id);
    return ResponseEntity.ok(Map.of("deleted", id));
  }
}
