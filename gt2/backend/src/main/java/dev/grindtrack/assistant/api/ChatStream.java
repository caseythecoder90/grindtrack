package dev.grindtrack.assistant.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * One server-sent-events connection, for the duration of one turn.
 *
 * <p>Every method swallows the {@link IOException} a write to a closed connection throws, because
 * there is nothing useful to do about it: the person navigated away or closed the tab, and the turn
 * they abandoned should finish and be stored anyway — it has already been paid for.
 *
 * <p>The heartbeat is the reason this class exists rather than four inline lambdas. An idle
 * connection gets closed somewhere between here and the browser, and a turn has long silences in
 * it: the model thinking before its first word, and — much worse — a tool that itself calls a model
 * and returns thirty seconds later having sent nothing.
 *
 * <p>It runs on a timer rather than off upstream events, which is the correction to how this was
 * first built. Poking the connection whenever something arrives only covers silences that happen to
 * be bracketed by traffic; it cannot cover the one place the stream goes quiet precisely
 * <em>because</em> nothing is arriving. A clock does not have that blind spot.
 */
class ChatStream {

  /** Quiet enough not to be noise in a log, frequent enough for a sixty-second proxy timeout. */
  private static final long TICK_INTERVAL_MS = 10_000;

  /** Checked more often than the interval so a real gap is never a whole interval longer. */
  private static final long HEARTBEAT_CHECK_MS = 4_000;

  private final SseEmitter emitter;
  private final ObjectMapper mapper;
  private volatile long lastWriteMs = System.currentTimeMillis();
  private ScheduledFuture<?> heartbeat;

  ChatStream(SseEmitter emitter, ObjectMapper mapper) {
    this.emitter = emitter;
    this.mapper = mapper;
  }

  /**
   * Keep the connection from looking idle for as long as the turn lasts.
   *
   * <p>The writes themselves are synchronized with the turn's own, because two threads writing to
   * one emitter is how a stream gets interleaved into something no parser can read.
   */
  void beat(ScheduledExecutorService scheduler) {
    heartbeat =
        scheduler.scheduleWithFixedDelay(
            this::tick, HEARTBEAT_CHECK_MS, HEARTBEAT_CHECK_MS, TimeUnit.MILLISECONDS);
  }

  private void stopBeating() {
    if (heartbeat != null) {
      heartbeat.cancel(false);
    }
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
  synchronized void tick() {
    if (System.currentTimeMillis() - lastWriteMs >= TICK_INTERVAL_MS) {
      send("tick", Map.of());
    }
  }

  void done(Object payload) {
    stopBeating();
    send("done", payload);
    emitter.complete();
  }

  /**
   * The turn failed. Sent as an event rather than an HTTP status: by the time anything can go wrong
   * the response is committed at 200 and the status line is long gone, so the only way to tell the
   * client is in the stream it is already reading.
   */
  void failed(String message) {
    stopBeating();
    send("error", Map.of("error", message));
    emitter.complete();
  }

  private synchronized void send(String name, Object payload) {
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
