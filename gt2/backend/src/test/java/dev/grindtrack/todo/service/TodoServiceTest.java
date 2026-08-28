package dev.grindtrack.todo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.grindtrack.todo.domain.Todo;
import dev.grindtrack.todo.domain.TodoRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Thin by design, so this is a short file. The one thing worth pinning is {@code clearDueDate},
 * because null cannot mean both "leave it alone" and "remove it" and getting that wrong silently
 * strips due dates on every unrelated edit.
 */
class TodoServiceTest {

  private TodoRepository todos;
  private TodoService service;

  @BeforeEach
  void setUp() {
    todos = mock(TodoRepository.class);
    service = new TodoService(todos);
    when(todos.save(any(Todo.class))).thenAnswer(i -> i.getArgument(0));
  }

  private Todo existing() {
    Todo todo = new Todo("Renew passport", "personal", LocalDate.of(2026, 9, 1), 0);
    when(todos.findById(1L)).thenReturn(Optional.of(todo));
    return todo;
  }

  @Test
  void aNewTodoGoesToTheEndOfTheList() {
    when(todos.count()).thenReturn(7L);
    assertThat(service.create("Book the dentist", "personal", null).getSortOrder()).isEqualTo(7);
  }

  @Test
  void anUnfilteredListReadsEveryKind() {
    when(todos.findAllByOrderByDoneAscSortOrderAscIdAsc()).thenReturn(List.of());
    assertThat(service.list(null)).isEmpty();
  }

  @Test
  void clearDueDateRemovesItWhileNullLeavesItAlone() {
    Todo todo = existing();

    service.update(1L, null, null, null, false, null, null);
    assertThat(todo.getDueDate()).isEqualTo(LocalDate.of(2026, 9, 1));

    service.update(1L, null, null, null, true, null, null);
    assertThat(todo.getDueDate()).isNull();
  }

  @Test
  void aSuppliedDueDateReplacesTheOldOne() {
    Todo todo = existing();
    service.update(1L, null, null, null, false, LocalDate.of(2026, 10, 5), null);
    assertThat(todo.getDueDate()).isEqualTo(LocalDate.of(2026, 10, 5));
  }

  @Test
  void markingDoneLeavesEverythingElseUntouched() {
    Todo todo = existing();
    service.update(1L, null, null, true, false, null, null);

    assertThat(todo.isDone()).isTrue();
    assertThat(todo.getTitle()).isEqualTo("Renew passport");
    assertThat(todo.getKind()).isEqualTo("personal");
  }

  @Test
  void updatingAnUnknownTodoReportsAbsenceRatherThanThrowing() {
    when(todos.findById(99L)).thenReturn(Optional.empty());
    assertThat(service.update(99L, "x", null, null, false, null, null)).isEmpty();
  }
}
