package dev.grindtrack.chat.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.grindtrack.auth.security.SignedIn;
import dev.grindtrack.chat.service.ChatSessions;
import dev.grindtrack.chat.service.RoomService;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * {@code /api/chat/ws}: one socket per open app, for as long as it is open.
 *
 * <p>The handshake is an ordinary GET through the security filter, so the access cookie gates it
 * and the principal it proved is on the session; a socket without one is closed before it is
 * registered. Almost everything flows server to browser (see {@link ChatSessions}). The browser
 * sends one kind of frame, {@code {"type":"typing","on":true|false}}, because typing is the one
 * thing not worth a request: it is nothing to store and nothing to retry.
 */
@Component
public class ChatSocketHandler extends TextWebSocketHandler {

  private static final Logger log = LoggerFactory.getLogger(ChatSocketHandler.class);

  private final ChatSessions sessions;
  private final RoomService chat;
  private final ObjectMapper mapper;

  public ChatSocketHandler(ChatSessions sessions, RoomService chat, ObjectMapper mapper) {
    this.sessions = sessions;
    this.chat = chat;
    this.mapper = mapper;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession raw) throws IOException {
    SignedIn who;
    try {
      who = SignedIn.of(raw.getPrincipal());
    } catch (IllegalStateException e) {
      // Cannot happen behind the filter; said out loud rather than trusted.
      log.warn("Chat socket {} opened without a signed-in account; closing it", raw.getId());
      raw.close(CloseStatus.POLICY_VIOLATION);
      return;
    }
    sessions.register(who, raw);
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) {
    sessions
        .whose(session.getId())
        .ifPresent(
            who -> {
              JsonNode frame = parse(message.getPayload());
              if (frame != null && "typing".equals(frame.path("type").asText())) {
                chat.typing(who, frame.path("on").asBoolean(false));
              }
            });
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    sessions.unregister(session.getId());
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable exception) {
    log.info("Chat socket {} transport error: {}", session.getId(), exception.getMessage());
    sessions.unregister(session.getId());
  }

  private JsonNode parse(String payload) {
    try {
      return mapper.readTree(payload);
    } catch (IOException e) {
      log.debug("Ignoring a chat frame that is not JSON: {}", e.getMessage());
      return null;
    }
  }
}
