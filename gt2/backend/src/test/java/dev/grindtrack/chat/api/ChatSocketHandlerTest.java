package dev.grindtrack.chat.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.grindtrack.auth.domain.Role;
import dev.grindtrack.auth.security.SignedIn;
import dev.grindtrack.chat.service.ChatSessions;
import dev.grindtrack.chat.service.RoomService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/** A socket is registered to whoever the cookie proved, and the one frame it sends is typing. */
class ChatSocketHandlerTest {

  private static final SignedIn CASEY = new SignedIn(1L, "casey", Role.OWNER);

  private ChatSessions sessions;
  private RoomService chat;
  private ChatSocketHandler handler;
  private WebSocketSession raw;

  @BeforeEach
  void setUp() {
    sessions = mock(ChatSessions.class);
    chat = mock(RoomService.class);
    handler = new ChatSocketHandler(sessions, chat, new ObjectMapper());
    raw = mock(WebSocketSession.class);
    when(raw.getId()).thenReturn("s1");
  }

  @Test
  void aSocketWithAnAccountIsRegisteredToIt() throws Exception {
    when(raw.getPrincipal()).thenReturn(CASEY);

    handler.afterConnectionEstablished(raw);

    verify(sessions).register(CASEY, raw);
  }

  @Test
  void aSocketWithoutAnAccountIsClosedNotRegistered() throws Exception {
    when(raw.getPrincipal()).thenReturn(() -> "someone");

    handler.afterConnectionEstablished(raw);

    verify(raw).close(CloseStatus.POLICY_VIOLATION);
    verify(sessions, never()).register(any(), any());
  }

  @Test
  void aTypingFrameIsRelayedAndAnythingElseIsIgnored() throws Exception {
    when(sessions.whose("s1")).thenReturn(Optional.of(CASEY));

    handler.handleMessage(raw, new TextMessage("{\"type\":\"typing\",\"on\":true}"));
    verify(chat).typing(CASEY, true);

    handler.handleMessage(raw, new TextMessage("{\"type\":\"typing\",\"on\":false}"));
    verify(chat).typing(CASEY, false);

    handler.handleMessage(raw, new TextMessage("{\"type\":\"dance\"}"));
    handler.handleMessage(raw, new TextMessage("not json"));
    org.mockito.Mockito.verifyNoMoreInteractions(chat);
  }

  @Test
  void aFrameFromAnUnregisteredSocketDoesNothing() throws Exception {
    when(sessions.whose("s1")).thenReturn(Optional.empty());

    handler.handleMessage(raw, new TextMessage("{\"type\":\"typing\",\"on\":true}"));

    verifyNoInteractions(chat);
  }

  @Test
  void closingOrFailingUnregisters() throws Exception {
    handler.afterConnectionClosed(raw, CloseStatus.NORMAL);
    handler.handleTransportError(raw, new RuntimeException("gone"));

    verify(sessions, org.mockito.Mockito.times(2)).unregister("s1");
  }
}
