package dev.grindtrack.todo.service;

import dev.grindtrack.todo.domain.Todo;
import dev.grindtrack.todo.domain.TodoRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The todo list.
 *
 * <p>Genuinely thin — this is flat CRUD over one table and it does not pretend otherwise. It exists
 * anyway, because the alternative is the controller holding a repository, and then "where does data
 * access live" has two answers in this codebase depending on which feature you happen to open.
 * Consistency is worth a twenty-line class.
 */
@Service
public class TodoService {

  private final TodoRepository todos;

  public TodoService(TodoRepository todos) {
    this.todos = todos;
  }

  public List<Todo> list(String kind) {
    return kind == null
        ? todos.findAllByOrderByDoneAscSortOrderAscIdAsc()
        : todos.findByKindOrderByDoneAscSortOrderAscIdAsc(kind);
  }

  @Transactional
  public Todo create(String title, String kind, LocalDate dueDate) {
    // New items go to the end of their list.
    int nextOrder = (int) todos.count();
    return todos.save(new Todo(title, kind, dueDate, nextOrder));
  }

  /**
   * Partial update; null means "leave alone".
   *
   * <p>{@code clearDueDate} exists because null cannot mean both "leave it" and "remove it", and a
   * todo genuinely needs its due date removable.
   *
   * <p>Returns empty for an unknown id rather than throwing, so the controller owns what absence
   * looks like over HTTP.
   */
  @Transactional
  public Optional<Todo> update(
      Long id,
      String title,
      String kind,
      Boolean done,
      boolean clearDueDate,
      LocalDate dueDate,
      Integer sortOrder) {

    return todos
        .findById(id)
        .map(
            todo -> {
              if (title != null) {
                todo.setTitle(title);
              }
              if (kind != null) {
                todo.setKind(kind);
              }
              if (done != null) {
                todo.setDone(done);
              }
              if (clearDueDate) {
                todo.setDueDate(null);
              } else if (dueDate != null) {
                todo.setDueDate(dueDate);
              }
              if (sortOrder != null) {
                todo.setSortOrder(sortOrder);
              }
              return todos.save(todo);
            });
  }

  @Transactional
  public void delete(Long id) {
    todos.deleteById(id);
  }
}
