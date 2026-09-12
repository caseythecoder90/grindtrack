package dev.grindtrack.speech.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.grindtrack.config.SpeechProperties;
import dev.grindtrack.speech.service.TranscriptionRelay;
import dev.grindtrack.speech.service.TranscriptionUpstream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

/**
 * {@code /api/speech/ws}: one socket per dictation, one relay per socket.
 *
 * <p>Binary frames are audio (PCM16, mono, 24 kHz); the one text frame the browser sends is {@code
 * {"type":"stop"}}. Everything back is a small JSON frame the relay documents. The handshake is an
 * ordinary GET through the security filter, so the session cookie gates it like any other endpoint
 * — a socket from a logged-out browser is refused before this class hears of it.
 */
@Component
public class SpeechSocketHandler extends AbstractWebSocketHandler {

  private static final Logger log = LoggerFactory.getLogger(SpeechSocketHandler.class);

  /** Sends that queue past this are a browser that stopped reading; the socket is dropped. */
  private static final int SEND_TIMEOUT_MS = 5_000;

  private static final int SEND_BUFFER_BYTES = 256 * 1024;

  private final SpeechProperties props;
  private final TranscriptionUpstream upstream;
  private final ObjectMapper mapper;
  private final ScheduledExecutorService scheduler;
  private final Map<String, TranscriptionRelay> relays = new ConcurrentHashMap<>();

  public SpeechSocketHandler(
      SpeechProperties props,
      TranscriptionUpstream upstream,
      ObjectMapper mapper,
      @Qualifier("speechScheduler") ScheduledExecutorService speechScheduler) {
    this.props = props;
    this.upstream = upstream;
    this.mapper = mapper;
    this.scheduler = speechScheduler;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession raw) throws Exception {
    WebSocketSession session =
        new ConcurrentWebSocketSessionDecorator(raw, SEND_TIMEOUT_MS, SEND_BUFFER_BYTES);
    if (!props.configured()) {
      session.sendMessage(
          new TextMessage(
              "{\"type\":\"error\",\"message\":\"speech to text is off — set OPENAI_API_KEY on"
                  + " the deployment to turn it on\"}"));
      session.close(CloseStatus.POLICY_VIOLATION);
      return;
    }
    TranscriptionRelay relay =
        new TranscriptionRelay(props, upstream, mapper, scheduler, sink(session));
    relays.put(raw.getId(), relay);
    relay.start();
  }

  @Override
  protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
    TranscriptionRelay relay = relays.get(session.getId());
    if (relay != null) {
      relay.onAudio(message.getPayload());
    }
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) {
    TranscriptionRelay relay = relays.get(session.getId());
    if (relay != null && message.getPayload().contains("\"stop\"")) {
      relay.onStop();
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    TranscriptionRelay relay = relays.remove(session.getId());
    if (relay != null) {
      relay.onBrowserClosed();
    }
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable exception) {
    log.info("Speech socket {} transport error: {}", session.getId(), exception.getMessage());
    afterConnectionClosed(session, CloseStatus.SESSION_NOT_RELIABLE);
  }

  private static TranscriptionRelay.Sink sink(WebSocketSession session) {
    return new TranscriptionRelay.Sink() {
      @Override
      public void send(String json) {
        if (!session.isOpen()) {
          return;
        }
        try {
          session.sendMessage(new TextMessage(json));
        } catch (IOException | IllegalStateException e) {
          // The browser went away between the check and the send. Its close will follow.
          log.debug("Could not send to speech socket {}: {}", session.getId(), e.getMessage());
        }
      }

      @Override
      public void close() {
        try {
          if (session.isOpen()) {
            session.close(CloseStatus.NORMAL);
          }
        } catch (IOException e) {
          log.debug("Could not close speech socket {}: {}", session.getId(), e.getMessage());
        }
      }
    };
  }
}
