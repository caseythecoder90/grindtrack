package dev.grindtrack.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.grindtrack.assistant.domain.AssistantReport;
import dev.grindtrack.assistant.domain.AssistantReportRepository;
import dev.grindtrack.config.AssistantProperties;
import dev.grindtrack.todo.domain.Todo;
import dev.grindtrack.todo.service.TodoService;
import dev.grindtrack.web.BadRequestException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * A day's batch accumulates, accepting adds and removes, and every refusal is a sentence the model
 * can act on.
 */
class TodoDraftServiceTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 9, 13);

  private TodoService todos;
  private AssistantReportRepository reports;
  private TodoDraftService service;

  @BeforeEach
  void setUp() {
    todos = mock(TodoService.class);
    reports = mock(AssistantReportRepository.class);
    when(reports.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(todos.create(any(), any(), any()))
        .thenAnswer(inv -> new Todo(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2), 0));
    service =
        new TodoDraftService(
            todos,
            reports,
            new AssistantProperties("sk-test", "claude-opus-5", "UTC", "", ""),
            new ObjectMapper());
  }

  @Test
  void proposingWritesADraftRowAndNoTodo() {
    when(reports.findByKindAndWeekStart(AssistantReport.KIND_TODO_BATCH, TODAY))
        .thenReturn(Optional.empty());

    TodoDraftService.Draft draft =
        service.propose(
            TODAY,
            List.of(
                new TodoDraft.Item("  call the dentist ", "personal", "2026-09-15"),
                new TodoDraft.Item("renew the domain", "nonsense", null)));

    ArgumentCaptor<AssistantReport> saved = ArgumentCaptor.forClass(AssistantReport.class);
    verify(reports).save(saved.capture());
    assertThat(saved.getValue().getWeekStart()).isEqualTo(TODAY);
    assertThat(saved.getValue().getInputTokens()).isZero();
    assertThat(draft.items())
        .containsExactly(
            new TodoDraft.Item("call the dentist", "personal", "2026-09-15"),
            new TodoDraft.Item("renew the domain", "personal", null));
    verify(todos, never()).create(any(), any(), any());
  }

  /** "Add a todo" twice in one day is one card with two rows, and the same title twice is one. */
  @Test
  void aDaysBatchAccumulatesWithoutDoubling() {
    AssistantReport existing = new AssistantReport(AssistantReport.KIND_TODO_BATCH, TODAY);
    existing.replaceDraft(
        "claude-opus-5",
        0,
        0,
        "{\"items\":[{\"title\":\"call the dentist\",\"kind\":\"personal\",\"dueDate\":null}]}");
    when(reports.findByKindAndWeekStart(AssistantReport.KIND_TODO_BATCH, TODAY))
        .thenReturn(Optional.of(existing));

    TodoDraftService.Draft draft =
        service.propose(
            TODAY,
            List.of(
                new TodoDraft.Item("Call the Dentist", "personal", null),
                new TodoDraft.Item("book the car service", "personal", null)));

    assertThat(draft.items())
        .extracting(TodoDraft.Item::title)
        .containsExactly("call the dentist", "book the car service");
    verify(reports).save(existing);
  }

  @Test
  void acceptingAddsWhatWasStoredAndRemovesTheDraft() {
    AssistantReport stored = new AssistantReport(AssistantReport.KIND_TODO_BATCH, TODAY);
    stored.replaceDraft(
        "claude-opus-5",
        0,
        0,
        "{\"items\":[{\"title\":\"call the dentist\",\"kind\":\"personal\",\"dueDate\":\"2026-09-15\"},"
            + "{\"title\":\"ship the deck\",\"kind\":\"work\",\"dueDate\":null}]}");
    when(reports.findByKindAndWeekStart(AssistantReport.KIND_TODO_BATCH, TODAY))
        .thenReturn(Optional.of(stored));

    TodoDraftService.Accepted accepted = service.accept(TODAY);

    assertThat(accepted.added()).isEqualTo(2);
    verify(todos).create("call the dentist", "personal", LocalDate.of(2026, 9, 15));
    verify(todos).create(eq("ship the deck"), eq("work"), eq(null));
    verify(reports).delete(stored);
  }

  @Test
  void acceptingTwiceCannotAddTwice() {
    when(reports.findByKindAndWeekStart(AssistantReport.KIND_TODO_BATCH, TODAY))
        .thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.accept(TODAY))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("no drafted todos");
    verify(todos, never()).create(any(), any(), any());
  }

  @Test
  void refusalsAreSentencesTheModelCanFix() {
    assertThatThrownBy(() -> service.propose(TODAY, List.of()))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("say what needs doing");
    assertThatThrownBy(
            () -> service.propose(TODAY, List.of(new TodoDraft.Item("  ", "personal", null))))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("needs a title");
    assertThatThrownBy(
            () ->
                service.propose(
                    TODAY, List.of(new TodoDraft.Item("call", "personal", "next tuesday"))))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("YYYY-MM-DD");
    verify(reports, never()).save(any());
  }
}
