package dev.grindtrack.assistant.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.grindtrack.assistant.service.ChatModel;
import dev.grindtrack.assistant.service.ChatService;
import dev.grindtrack.web.ApiExceptionHandler;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * The streamed turn, over the wire.
 *
 * <p>What is worth pinning here is the frame format, because it is a contract with a hand-written
 * parser in the browser rather than with a library: an event name, a JSON payload, a blank line.
 * The turn runs on the calling thread so the assertions are about what was written, not about when.
 */
class ChatControllerTest {

  private ChatService chat;
  private ScheduledExecutorService heartbeats;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    chat = mock(ChatService.class);
    Executor sameThread = Runnable::run;
    // A real scheduler: the heartbeat must be cancellable and must not outlive the turn, and a
    // mock would assert that by construction rather than exercise it.
    heartbeats = Executors.newSingleThreadScheduledExecutor();
    mvc =
        MockMvcBuilders.standaloneSetup(
                new ChatController(chat, new ObjectMapper(), sameThread, heartbeats))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
  }

  @AfterEach
  void tearDown() {
    heartbeats.shutdownNow();
  }

  private String streamed(String question) throws Exception {
    MvcResult started =
        mvc.perform(
                post("/api/assistant/chat/stream")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"conversationId\":null,\"message\":\"" + question + "\"}"))
            .andExpect(status().isOk())
            .andReturn();
    return mvc.perform(asyncDispatch(started)).andReturn().getResponse().getContentAsString();
  }

  @Test
  void aTurnStreamsWhatItReadsThenWhatItSaysThenHowItWentAndWhatItCost() throws Exception {
    when(chat.chat(isNull(), anyString(), any()))
        .thenAnswer(
            inv -> {
              ChatModel.Listener listener = inv.getArgument(2);
              listener.onToolUse("get_plan");
              listener.onText("you are ");
              listener.onText("behind");
              return new ChatService.ChatReply(4L, "you are behind", 3000, 400, null);
            });

    String body = streamed("how am I doing?");

    assertThat(body)
        .contains("event:tool")
        .contains("{\"name\":\"get_plan\"}")
        .contains("{\"delta\":\"you are \"}")
        .contains("{\"delta\":\"behind\"}")
        .contains("event:done")
        .contains("\"conversationId\":4");
  }

  /**
   * A newline inside an answer must not end a frame. The payload is JSON for this reason alone —
   * raw text would make every paragraph break look like the end of the message to the parser.
   */
  @Test
  void aNewlineInTheAnswerDoesNotEndAFrame() throws Exception {
    when(chat.chat(isNull(), anyString(), any()))
        .thenAnswer(
            inv -> {
              ((ChatModel.Listener) inv.getArgument(2)).onText("first\n\nsecond");
              return new ChatService.ChatReply(1L, "first\n\nsecond", 1, 1, null);
            });

    assertThat(streamed("two paragraphs please"))
        .contains("{\"delta\":\"first\\n\\nsecond\"}")
        .doesNotContain("\n\nsecond");
  }

  /**
   * A turn that fails after the response is committed cannot answer with a status code — there is
   * no status line left to set — so the failure has to travel in the stream the client is reading.
   */
  @Test
  void aFailureArrivesAsAnEventBecauseTheStatusLineIsLongGone() throws Exception {
    when(chat.chat(isNull(), anyString(), any()))
        .thenThrow(new IllegalStateException("upstream fell over"));

    assertThat(streamed("anything"))
        .contains("event:error")
        .contains("{\"error\":\"upstream fell over\"}");
  }

  @Test
  void theQuestionAndTheConversationReachTheService() throws Exception {
    when(chat.chat(eq(9L), eq("and now?"), any()))
        .thenReturn(new ChatService.ChatReply(9L, "now this", 1, 1, null));

    String body =
        mvc.perform(
                asyncDispatch(
                    mvc.perform(
                            post("/api/assistant/chat/stream")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"conversationId\":9,\"message\":\"and now?\"}"))
                        .andReturn()))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(body).contains("\"conversationId\":9");
  }
}
