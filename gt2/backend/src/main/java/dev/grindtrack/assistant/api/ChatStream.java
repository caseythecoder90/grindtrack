package dev.grindtrack.assistant.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * One server-sent-events connection, for the duration of one turn.
 *
 * <p>Every method swallows the {@link IOException} a write to a closed connection throws, because
 * there is nothing useful to do about it: the person navigated away or closed the tab, and the turn
 * they abandoned should finish and be stored anyway — it has already been paid for.
 *
 * <p>The {@link #tick()} is the reason this class exists rather than four inline lambdas. An
 * ingress proxy closes an idle upstream connection after sixty seconds, and the model can spend
 * longer than that thinking before it emits its first word. Every event that arrives from upstream,
 * including the ones this app has no use for, pokes the connection often enough for the proxy to
 * leave it alone.
 */
class ChatStream {

  /** Quiet enough not to be noise in a log, frequent enough for a sixty-second proxy timeout. */
  private static final long TICK_INTERVAL_MS = 10_000;

  private final SseEmitter emitter;
  private final ObjectMapper mapper;
  private long lastWriteMs = System.currentTimeMillis();

  ChatStream(SseEmitter emitter, ObjectMapper mapper) {
    this.emitter = emitter;
    this.mapper = mapper;
  }

  /** Which of the four read tools the model reached for, so the wait can say what it is doing. */
  void tool(String name) {
    send("tool", Map.of("name", name));
  }

  /** A fragment of the answer. JSON-encoded, so a newline in the text is not a frame boundary. */
  void text(String delta) {
    send("text", Map.of("delta", delta));
  }

  /** Keeps the connection from looking idle to whatever sits between here and the browser. */
  void tick() {
    if (System.currentTimeMillis() - lastWriteMs >= TICK_INTERVAL_MS) {
      send("tick", Map.of());
    }
  }

  void done(Object payload) {
    send("done", payload);
    emitter.complete();
  }

  /**
   * The turn failed. Sent as an event rather than an HTTP status: by the time anything can go wrong
   * the response is committed at 200 and the status line is long gone, so the only way to tell the
   * client is in the stream it is already reading.
   */
  void failed(String message) {
    send("error", Map.of("error", message));
    emitter.complete();
  }

  private void send(String name, Object payload) {
    try {
      emitter.send(SseEmitter.event().name(name).data(json(payload), MediaType.APPLICATION_JSON));
      lastWriteMs = System.currentTimeMillis();
    } catch (IOException | IllegalStateException e) {
      // The client is gone. The turn carries on; there is simply no one to tell.
    }
  }

  private String json(Object payload) {
    try {
      return mapper.writeValueAsString(payload);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("could not serialize a chat event", e);
    }
  }
}
