package dev.grindtrack.chat.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.grindtrack.auth.domain.Role;
import dev.grindtrack.auth.security.SignedIn;
import dev.grindtrack.chat.service.ChatService;
import dev.grindtrack.web.ApiExceptionHandler;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** The shapes over HTTP, and that every one of them is asked on behalf of the signed-in account. */
class ChatControllerTest {

  private static final SignedIn CASEY = new SignedIn(1L, "casey", Role.OWNER);
  private static final UUID CLIENT_ID = UUID.fromString("6f1d2b6e-1c3a-4f9e-9b2a-1d2e3f4a5b6c");

  private ChatService chat;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    chat = mock(ChatService.class);
    mvc =
        MockMvcBuilders.standaloneSetup(new ChatController(chat))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
  }

  private static ChatService.MessageView view(long id, String body) {
    return new ChatService.MessageView(
        id, 1L, body, "2026-09-19T08:00:00Z", null, CLIENT_ID.toString(), List.of());
  }

  @Test
  void theStateIsTheRoomAsSeenByMe() throws Exception {
    when(chat.state(CASEY))
        .thenReturn(
            new ChatService.State(
                new ChatService.Person(1L, "casey"),
                new ChatService.Person(2L, "wife"),
                3,
                10,
                new ChatService.CursorView(1L, 8, 4),
                new ChatService.CursorView(2L, 9, 9)));

    mvc.perform(get("/api/chat").principal(CASEY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.them.username").value("wife"))
        .andExpect(jsonPath("$.unread").value(3))
        .andExpect(jsonPath("$.latestId").value(10))
        .andExpect(jsonPath("$.theirs.readId").value(9));
  }

  @Test
  void historyPassesBeforeAndAfterThrough() throws Exception {
    when(chat.history(5L, null)).thenReturn(new ChatService.Page(List.of(view(4, "a")), false));

    mvc.perform(get("/api/chat/messages").param("before", "5").principal(CASEY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.messages[0].id").value(4))
        .andExpect(jsonPath("$.hasMore").value(false));
    verify(chat).history(5L, null);

    when(chat.history(null, 9L)).thenReturn(new ChatService.Page(List.of(), false));
    mvc.perform(get("/api/chat/messages").param("after", "9").principal(CASEY))
        .andExpect(status().isOk());
    verify(chat).history(null, 9L);
  }

  @Test
  void sendingTakesAClientIdAndSomeWords() throws Exception {
    when(chat.send(CASEY, CLIENT_ID, "hello")).thenReturn(view(10, "hello"));

    mvc.perform(
            post("/api/chat/messages")
                .principal(CASEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"" + CLIENT_ID + "\",\"body\":\"  hello \"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(10))
        .andExpect(jsonPath("$.body").value("hello"));
  }

  @Test
  void aBadClientIdOrNoWordsIsA400WithASentence() throws Exception {
    mvc.perform(
            post("/api/chat/messages")
                .principal(CASEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"nope\",\"body\":\"hello\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.error").value("clientId must be a UUID the phone made for this message"));

    mvc.perform(
            post("/api/chat/messages")
                .principal(CASEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"" + CLIENT_ID + "\",\"body\":\"   \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("a message needs some words (max 4000 chars)"));
    verify(chat, never()).send(any(), any(), any());
  }

  @Test
  void unsendingAnswersTheMessageAsItNowStandsAndSomebodyElsesIs404() throws Exception {
    when(chat.unsend(CASEY, 4L))
        .thenReturn(
            new ChatService.MessageView(
                4L, 1L, "", "2026-09-19T08:00:00Z", "2026-09-19T08:01:00Z", "x", List.of()));

    mvc.perform(delete("/api/chat/messages/4").principal(CASEY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.body").value(""))
        .andExpect(jsonPath("$.deletedAt").value("2026-09-19T08:01:00Z"));

    when(chat.unsend(CASEY, 9L)).thenThrow(new NoSuchElementException("message 9"));
    mvc.perform(delete("/api/chat/messages/9").principal(CASEY))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("not found: message 9"));
  }

  @Test
  void aReactionIsPutOnAndDeletedOffByItsEmojiInThePath() throws Exception {
    when(chat.react(eq(CASEY), eq(4L), eq("❤️"), any(Boolean.class))).thenReturn(view(4, "x"));

    mvc.perform(put("/api/chat/messages/4/reactions/{emoji}", "❤️").principal(CASEY))
        .andExpect(status().isOk());
    verify(chat).react(CASEY, 4L, "❤️", true);

    mvc.perform(delete("/api/chat/messages/4/reactions/{emoji}", "❤️").principal(CASEY))
        .andExpect(status().isOk());
    verify(chat).react(CASEY, 4L, "❤️", false);
  }

  @Test
  void theCursorTakesEitherNumber() throws Exception {
    when(chat.moveCursor(CASEY, null, 9L)).thenReturn(new ChatService.CursorView(1L, 9, 9));

    mvc.perform(
            post("/api/chat/cursor")
                .principal(CASEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"readUpTo\":9}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.readId").value(9))
        .andExpect(jsonPath("$.deliveredId").value(9));
  }
}
