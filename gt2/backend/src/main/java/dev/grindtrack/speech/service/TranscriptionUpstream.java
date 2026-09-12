package dev.grindtrack.speech.service;

import java.util.concurrent.CompletableFuture;

/**
 * The socket to the transcription service, behind an interface so the relay can be tested against
 * what it sends and how it treats what comes back, without a network or a key.
 *
 * <p>Production is {@link OpenAiTranscriptionUpstream}. Events in both directions are the service's
 * JSON, untouched: the relay is the one place that knows their shape.
 */
public interface TranscriptionUpstream {

  /** Open a connection. The future completes once the socket is up and can be sent to. */
  CompletableFuture<Connection> open(Listener listener);

  interface Connection {
    void send(String json);

    void close();
  }

  interface Listener {
    /** One complete JSON event from the service. */
    void onEvent(String json);

    /** The service closed the socket, or it failed. Called at most once. */
    void onClosed(String reason);
  }
}
