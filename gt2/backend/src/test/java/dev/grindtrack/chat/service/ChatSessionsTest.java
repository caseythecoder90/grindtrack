package dev.grindtrack.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.grindtrack.auth.domain.Role;
import dev.grindtrack.auth.security.SignedIn;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

/** Frames reach the right sockets and only those; a socket that closed is forgotten. */
class ChatSessionsTest {

  private static final SignedIn CASEY = new SignedIn(1L, "casey", Role.OWNER);
  private static final SignedIn WIFE = new SignedIn(2L, "wife", Role.PARTNER);

  private ChatSessions sessions;
  private WebSocketSession phone;
  private WebSocketSession laptop;
  private WebSocketSession hers;

  private static WebSocketSession open(String id) {
    WebSocketSession s = mock(WebSocketSession.class);
    when(s.getId()).thenReturn(id);
    when(s.isOpen()).thenReturn(true);
    return s;
  }

  private static String payloadOf(WebSocketMessage<?> message) {
    return ((TextMessage) message).getPayload();
  }

  @BeforeEach
  void setUp() {
    sessions = new ChatSessions(new ObjectMapper());
    phone = open("phone");
    laptop = open("laptop");
    hers = open("hers");
    sessions.register(CASEY, phone);
    sessions.register(CASEY, laptop);
    sessions.register(WIFE, hers);
  }

  @Test
  void sendToReachesEverySocketOfOneAccountAndNoOther() throws Exception {
    sessions.sendTo(1L, ChatSessions.frame("typing", "userId", 2L, "on", true));

    ArgumentCaptor<WebSocketMessage<?>> sent = ArgumentCaptor.forClass(WebSocketMessage.class);
    verify(phone).sendMessage(sent.capture());
    verify(laptop).sendMessage(any());
    verify(hers, never()).sendMessage(any());
    assertThat(payloadOf(sent.getValue()))
        .isEqualTo("{\"type\":\"typing\",\"userId\":2,\"on\":true}");
  }

  @Test
  void broadcastReachesEveryone() throws Exception {
    sessions.broadcast(ChatSessions.frame("message", "message", Map.of("id", 4)));

    verify(phone).sendMessage(any());
    verify(laptop).sendMessage(any());
    verify(hers).sendMessage(any());
  }

  @Test
  void whoseAndHasOpenAnswerFromTheRegistry() {
    assertThat(sessions.whose("phone")).contains(CASEY);
    assertThat(sessions.whose("hers")).contains(WIFE);
    assertThat(sessions.whose("nobody")).isEmpty();
    assertThat(sessions.hasOpen(1L)).isTrue();
    assertThat(sessions.hasOpen(2L)).isTrue();
    assertThat(sessions.hasOpen(3L)).isFalse();
    assertThat(sessions.openCount()).isEqualTo(3);
  }

  @Test
  void aClosedSocketIsDroppedWhenItIsNextSpokenToAndByThePing() throws Exception {
    when(hers.isOpen()).thenReturn(false);

    assertThat(sessions.hasOpen(2L)).isFalse();
    sessions.broadcast(ChatSessions.frame("cursor", "cursor", Map.of()));
    verify(hers, never()).sendMessage(any());
    assertThat(sessions.whose("hers")).isEmpty();
    assertThat(sessions.openCount()).isEqualTo(2);

    when(laptop.isOpen()).thenReturn(false);
    sessions.ping();
    verify(phone).sendMessage(any(PingMessage.class));
    assertThat(sessions.whose("laptop")).isEmpty();
    assertThat(sessions.openCount()).isEqualTo(1);
  }

  @Test
  void unregisteringTheLastSocketForgetsTheAccount() {
    sessions.unregister("hers");
    assertThat(sessions.hasOpen(2L)).isFalse();
    assertThat(sessions.whose("hers")).isEmpty();
    sessions.unregister("hers"); // twice is fine
    assertThat(sessions.openCount()).isEqualTo(2);
  }

  @Test
  void aFrameIsItsTypeThenItsFieldsInOrder() {
    Map<String, Object> frame = ChatSessions.frame("unsent", "message", Map.of("id", 1), "x", 2);
    assertThat(frame.keySet()).containsExactly("type", "message", "x");
    assertThat(frame.get("type")).isEqualTo("unsent");
  }
}
