package dev.grindtrack.todo.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.grindtrack.todo.domain.Todo;
import dev.grindtrack.todo.domain.TodoRepository;
import dev.grindtrack.todo.service.TodoService;
import dev.grindtrack.web.ApiExceptionHandler;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Standalone MockMvc tests pinning todo validation and CRUD outcomes. */
class TodoControllerTest {

  private TodoRepository todos;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    todos = mock(TodoRepository.class);
    when(todos.save(any())).thenAnswer(inv -> inv.getArgument(0));
    mvc =
        MockMvcBuilders.standaloneSetup(new TodoController(new TodoService(todos)))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
  }

  private static Todo todo(String title, String kind) {
    return new Todo(title, kind, null, 0);
  }

  @Test
  void listWithoutAKindReturnsEverything() throws Exception {
    when(todos.findAllByOrderByDoneAscSortOrderAscIdAsc())
        .thenReturn(List.of(todo("ship the proxy", "work"), todo("renew passport", "personal")));

    mvc.perform(get("/api/todos"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  void listFiltersByKind() throws Exception {
    when(todos.findByKindOrderByDoneAscSortOrderAscIdAsc("work"))
        .thenReturn(List.of(todo("ship the proxy", "work")));

    mvc.perform(get("/api/todos").param("kind", "work"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].kind").value("work"));
  }

  @Test
  void listRejectsAnUnknownKind() throws Exception {
    mvc.perform(get("/api/todos").param("kind", "study"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("kind must be work or personal"));
  }

  @Test
  void createTrimsTheTitleAndDefaultsToPersonal() throws Exception {
    mvc.perform(
            post("/api/todos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"  renew passport  \"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("renew passport"))
        .andExpect(jsonPath("$.kind").value("personal"))
        .andExpect(jsonPath("$.done").value(false));
  }

  @Test
  void createAcceptsAKindAndDueDate() throws Exception {
    mvc.perform(
            post("/api/todos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"title\": \"ship the proxy\", \"kind\": \"work\","
                        + " \"dueDate\": \"2026-08-01\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.kind").value("work"))
        .andExpect(jsonPath("$.dueDate").value("2026-08-01"));
  }

  @Test
  void createRejectsABlankTitle() throws Exception {
    mvc.perform(
            post("/api/todos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"   \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("a todo needs a title (max 300 chars)"));
  }

  @Test
  void createRejectsAMalformedDueDate() throws Exception {
    mvc.perform(
            post("/api/todos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"x\", \"dueDate\": \"01-08-2026\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("dueDate must be YYYY-MM-DD"));
  }

  @Test
  void completingAnItemStampsCompletedAtAndUncheckingClearsIt() throws Exception {
    Todo existing = todo("ship the proxy", "work");
    when(todos.findById(1L)).thenReturn(Optional.of(existing));

    mvc.perform(
            patch("/api/todos/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"done\": true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.done").value(true));
    assertThat(existing.getCompletedAt()).isNotNull();

    mvc.perform(
            patch("/api/todos/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"done\": false}"))
        .andExpect(status().isOk());
    assertThat(existing.getCompletedAt()).isNull();
  }

  @Test
  void aNullDueDateLeavesTheExistingOneAloneButClearDueDateRemovesIt() throws Exception {
    Todo existing = new Todo("ship the proxy", "work", LocalDate.of(2026, 8, 1), 0);
    when(todos.findById(1L)).thenReturn(Optional.of(existing));

    // Renaming must not silently drop the deadline.
    mvc.perform(
            patch("/api/todos/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"ship the edge proxy\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dueDate").value("2026-08-01"));

    mvc.perform(
            patch("/api/todos/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clearDueDate\": true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dueDate").doesNotExist());
  }

  @Test
  void patchRejectsAnUnknownKind() throws Exception {
    when(todos.findById(1L)).thenReturn(Optional.of(todo("x", "work")));

    mvc.perform(
            patch("/api/todos/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\": \"study\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("kind must be work or personal"));
  }

  @Test
  void patchingAMissingTodoIs404() throws Exception {
    when(todos.findById(9L)).thenReturn(Optional.empty());

    mvc.perform(
            patch("/api/todos/9")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"done\": true}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("not found: todo 9"));
  }

  @Test
  void newItemsGoToTheEndOfTheList() throws Exception {
    when(todos.count()).thenReturn(4L);

    mvc.perform(
            post("/api/todos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"last\"}"))
        .andExpect(status().isOk());

    ArgumentCaptor<Todo> captor = ArgumentCaptor.forClass(Todo.class);
    verify(todos).save(captor.capture());
    assertThat(captor.getValue().getSortOrder()).isEqualTo(4);
  }

  @Test
  void deleteRemovesById() throws Exception {
    mvc.perform(delete("/api/todos/3"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deleted").value(3));
    verify(todos).deleteById(3L);
  }
}
