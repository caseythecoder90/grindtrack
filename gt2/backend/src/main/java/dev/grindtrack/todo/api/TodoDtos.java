package dev.grindtrack.todo.api;

import dev.grindtrack.todo.domain.Todo;

/** Request/response shapes for the todo API. */
public final class TodoDtos {

  private TodoDtos() {}

  /** Create payload. {@code kind} defaults to personal; {@code dueDate} is optional. */
  public record TodoCreateRequest(String title, String kind, String dueDate) {}

  /**
   * Partial update: a null field is left unchanged. {@code dueDate} therefore needs the explicit
   * {@code clearDueDate} flag to be removed, since null already means "don't touch".
   */
  public record TodoUpdateRequest(
      String title,
      String kind,
      Boolean done,
      String dueDate,
      Boolean clearDueDate,
      Integer sortOrder) {}

  public record TodoResponse(
      Long id, String title, String kind, boolean done, String dueDate, int sortOrder) {

    public static TodoResponse from(Todo todo) {
      return new TodoResponse(
          todo.getId(),
          todo.getTitle(),
          todo.getKind(),
          todo.isDone(),
          todo.getDueDate() == null ? null : todo.getDueDate().toString(),
          todo.getSortOrder());
    }
  }
}
