package dev.grindtrack.speech.service;

import dev.grindtrack.config.SpeechProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

/**
 * The Realtime API's transcription socket, over the JDK's WebSocket client.
 *
 * <p>{@code intent=transcription} selects a session that only listens: audio in, text out, no model
 * reply. The key travels in the handshake header and nowhere else — the browser never sees it,
 * which is the whole reason this relay exists rather than the phone talking to the service
 * directly.
 *
 * <p>The JDK client delivers a text frame in parts with a {@code last} flag; parts are gathered
 * into one event before the relay sees it, so the relay only ever parses whole JSON.
 */
@Component
public class OpenAiTranscriptionUpstream implements TranscriptionUpstream {

  static final URI ENDPOINT = URI.create("wss://api.openai.com/v1/realtime?intent=transcription");

  private final SpeechProperties props;
  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  public OpenAiTranscriptionUpstream(SpeechProperties props) {
    this.props = props;
  }

  @Override
  public CompletableFuture<Connection> open(Listener listener) {
    return client
        .newWebSocketBuilder()
        .header("Authorization", "Bearer " + props.apiKey())
        .connectTimeout(Duration.ofSeconds(10))
        .buildAsync(ENDPOINT, new Gathering(listener))
        .thenApply(Socket::new);
  }

  /** Whole events out of the JDK's partial frames; one close notification however it ends. */
  private static final class Gathering implements WebSocket.Listener {
    private final Listener listener;
    private final StringBuilder partial = new StringBuilder();
    private final AtomicBoolean closed = new AtomicBoolean();

    Gathering(Listener listener) {
      this.listener = listener;
    }

    @Override
    public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
      partial.append(data);
      if (last) {
        String event = partial.toString();
        partial.setLength(0);
        listener.onEvent(event);
      }
      ws.request(1);
      return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
      closeOnce("closed " + statusCode + (reason == null || reason.isBlank() ? "" : " " + reason));
      return null;
    }

    @Override
    public void onError(WebSocket ws, Throwable error) {
      closeOnce(error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
    }

    private void closeOnce(String reason) {
      if (closed.compareAndSet(false, true)) {
        listener.onClosed(reason);
      }
    }
  }

  /**
   * The JDK client refuses a second send while the first is still in flight, so sends are chained:
   * each waits for the one before it. Audio arrives every hundred milliseconds and each frame is a
   * few kilobytes, so the chain never grows.
   */
  private static final class Socket implements Connection {
    private final WebSocket ws;
    private CompletableFuture<WebSocket> pending;

    Socket(WebSocket ws) {
      this.ws = ws;
      this.pending = CompletableFuture.completedFuture(ws);
    }

    @Override
    public synchronized void send(String json) {
      pending = pending.thenCompose(w -> w.sendText(json, true)).exceptionally(e -> ws);
    }

    @Override
    public synchronized void close() {
      pending =
          pending
              .thenCompose(
                  w ->
                      w.isOutputClosed()
                          ? CompletableFuture.completedFuture(w)
                          : w.sendClose(WebSocket.NORMAL_CLOSURE, "done"))
              .exceptionally(e -> ws);
    }
  }
}
