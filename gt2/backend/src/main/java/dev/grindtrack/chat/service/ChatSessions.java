package dev.grindtrack.chat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.grindtrack.auth.security.SignedIn;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

/**
 * Who has the chat open right now, and the one way to tell them something.
 *
 * <p>A person has one socket per open app: a phone, a laptop, both, or none. Everything the server
 * has to say — a message, an unsend, a reaction, a cursor that moved, the other person typing — is
 * a small JSON frame sent to every socket of the accounts concerned; the REST endpoints change
 * state, the sockets carry the news. A socket that will not take a send within five seconds, or
 * that throws, is a phone that went away: it is dropped here and its close arrives when it arrives.
 *
 * <p>Single replica, so this map is the whole truth. The day there are two pods this becomes a
 * Redis channel, and {@code SchedulingConfig} already says the same about the jobs.
 */
@Component
public class ChatSessions {

  private static final Logger log = LoggerFactory.getLogger(ChatSessions.class);

  static final int SEND_TIMEOUT_MS = 5_000;
  static final int SEND_BUFFER_BYTES = 256 * 1024;

  private final ObjectMapper mapper;

  /** Open sockets by account. */
  private final Map<Long, Set<WebSocketSession>> byUser = new ConcurrentHashMap<>();

  /** Whose socket each one is, by the session id the container gave it. */
  private final Map<String, SignedIn> whose = new ConcurrentHashMap<>();

  public ChatSessions(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  /** Wraps the raw session so two threads never write to it at once, and remembers whose it is. */
  public WebSocketSession register(SignedIn who, WebSocketSession raw) {
    WebSocketSession session =
        new ConcurrentWebSocketSessionDecorator(raw, SEND_TIMEOUT_MS, SEND_BUFFER_BYTES);
    byUser.computeIfAbsent(who.id(), id -> ConcurrentHashMap.newKeySet()).add(session);
    whose.put(raw.getId(), who);
    log.debug("Chat socket {} opened for {}", raw.getId(), who.username());
    return session;
  }

  public void unregister(String sessionId) {
    SignedIn who = whose.remove(sessionId);
    if (who == null) {
      return;
    }
    Set<WebSocketSession> open = byUser.get(who.id());
    if (open != null) {
      open.removeIf(s -> s.getId().equals(sessionId));
      if (open.isEmpty()) {
        byUser.remove(who.id(), open);
      }
    }
    log.debug("Chat socket {} closed for {}", sessionId, who.username());
  }

  public Optional<SignedIn> whose(String sessionId) {
    return Optional.ofNullable(whose.get(sessionId));
  }

  /**
   * Whether any socket of theirs is open — the difference between a push now and a push in a
   * moment.
   */
  public boolean hasOpen(long userId) {
    Set<WebSocketSession> open = byUser.get(userId);
    return open != null && open.stream().anyMatch(WebSocketSession::isOpen);
  }

  public int openCount() {
    return whose.size();
  }

  /** To every socket of one account. */
  public void sendTo(long userId, Map<String, Object> frame) {
    Set<WebSocketSession> open = byUser.get(userId);
    if (open == null || open.isEmpty()) {
      return;
    }
    String json = toJson(frame);
    for (WebSocketSession session : open) {
      deliver(session, json);
    }
  }

  /** To every socket of every account: the room is everyone. */
  public void broadcast(Map<String, Object> frame) {
    String json = toJson(frame);
    for (Set<WebSocketSession> open : byUser.values()) {
      for (WebSocketSession session : open) {
        deliver(session, json);
      }
    }
  }

  /**
   * A keepalive every thirty seconds. The ingress closes an idle upstream connection after five
   * minutes, and a phone that slept through a ping answers nothing; this finds both.
   */
  @Scheduled(fixedRate = 30_000)
  public void ping() {
    for (Set<WebSocketSession> open : byUser.values()) {
      for (WebSocketSession session : open) {
        if (!session.isOpen()) {
          unregister(session.getId());
          continue;
        }
        try {
          session.sendMessage(new PingMessage());
        } catch (IOException | IllegalStateException e) {
          log.debug("Chat socket {} did not take a ping: {}", session.getId(), e.getMessage());
          unregister(session.getId());
        }
      }
    }
  }

  private void deliver(WebSocketSession session, String json) {
    if (!session.isOpen()) {
      unregister(session.getId());
      return;
    }
    try {
      session.sendMessage(new TextMessage(json));
    } catch (IOException | IllegalStateException e) {
      // Went away between the check and the send, or stopped reading. Its close will follow.
      log.debug("Could not send to chat socket {}: {}", session.getId(), e.getMessage());
      unregister(session.getId());
    }
  }

  /** {@code {"type": type, key: value, …}}: the shape of every frame. */
  public static Map<String, Object> frame(String type, Object... keysAndValues) {
    Map<String, Object> frame = new LinkedHashMap<>();
    frame.put("type", type);
    for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
      frame.put((String) keysAndValues[i], keysAndValues[i + 1]);
    }
    return frame;
  }

  private String toJson(Map<String, Object> frame) {
    try {
      return mapper.writeValueAsString(frame);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("could not serialize a chat frame", e);
    }
  }
}
