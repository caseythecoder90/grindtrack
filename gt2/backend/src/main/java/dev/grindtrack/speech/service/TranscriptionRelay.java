package dev.grindtrack.speech.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.grindtrack.config.SpeechProperties;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One dictation: the browser's microphone on one side, the transcription service on the other.
 *
 * <p>Audio frames from the browser become {@code input_audio_buffer.append} events; the service's
 * transcript events become four small frames the browser understands — {@code ready}, {@code
 * delta}, {@code final}, {@code speech} — plus {@code error}, after which the relay closes. The
 * service decides where phrases end (server-side voice detection), so the browser sends audio
 * continuously and receives text as each phrase settles.
 *
 * <p>Stop is a handshake, not a hang-up: the browser says stop, the relay asks the service to
 * commit whatever is buffered, and closes once that phrase's transcript has arrived — or after a
 * few seconds if it never does, because the last thing said must not be lost to the act of pressing
 * the button.
 *
 * <p>Both sides call in on their own threads, so every entry point is synchronized. The work inside
 * each is a parse and a send.
 */
public final class TranscriptionRelay implements TranscriptionUpstream.Listener {

  private static final Logger log = LoggerFactory.getLogger(TranscriptionRelay.class);

  /** A budget guard: five minutes of audio is a paragraph, not a question. */
  static final Duration MAX_DICTATION = Duration.ofMinutes(5);

  /** How long to wait for the last phrase after stop before closing anyway. */
  static final Duration STOP_GRACE = Duration.ofSeconds(4);

  private static final Base64.Encoder B64 = Base64.getEncoder();

  /** The browser's side of the relay. */
  public interface Sink {
    void send(String json);

    void close();
  }

  enum State {
    OPENING,
    READY,
    STOPPING,
    CLOSED
  }

  private final SpeechProperties props;
  private final TranscriptionUpstream upstream;
  private final ObjectMapper mapper;
  private final ScheduledExecutorService scheduler;
  private final Sink sink;

  private TranscriptionUpstream.Connection connection;
  private State state = State.OPENING;
  private long readyAt;

  public TranscriptionRelay(
      SpeechProperties props,
      TranscriptionUpstream upstream,
      ObjectMapper mapper,
      ScheduledExecutorService scheduler,
      Sink sink) {
    this.props = props;
    this.upstream = upstream;
    this.mapper = mapper;
    this.scheduler = scheduler;
    this.sink = sink;
  }

  public synchronized void start() {
    upstream
        .open(this)
        .whenComplete(
            (conn, error) -> {
              synchronized (this) {
                if (error != null) {
                  log.warn("Could not open the transcription socket: {}", error.getMessage());
                  fail("could not reach the transcription service");
                  return;
                }
                if (state == State.CLOSED) {
                  conn.close();
                  return;
                }
                connection = conn;
                conn.send(sessionUpdate());
              }
            });
  }

  /**
   * PCM16, mono, 24 kHz, as the session was configured. Frames before {@code ready} are dropped.
   */
  public synchronized void onAudio(ByteBuffer pcm) {
    if (state != State.READY) {
      return;
    }
    if (System.nanoTime() - readyAt > MAX_DICTATION.toNanos()) {
      fail("five minutes is the limit for one dictation");
      return;
    }
    byte[] bytes = new byte[pcm.remaining()];
    pcm.get(bytes);
    ObjectNode append = mapper.createObjectNode();
    append.put("type", "input_audio_buffer.append");
    append.put("audio", B64.encodeToString(bytes));
    connection.send(append.toString());
  }

  /** The person pressed stop: get the last phrase out, then close. */
  public synchronized void onStop() {
    if (state != State.READY) {
      if (state == State.OPENING) {
        closeAll();
      }
      return;
    }
    state = State.STOPPING;
    ObjectNode commit = mapper.createObjectNode();
    commit.put("type", "input_audio_buffer.commit");
    connection.send(commit.toString());
    scheduler.schedule(this::finish, STOP_GRACE.toMillis(), TimeUnit.MILLISECONDS);
  }

  public synchronized void onBrowserClosed() {
    closeAll();
  }

  @Override
  public synchronized void onEvent(String json) {
    if (state == State.CLOSED) {
      return;
    }
    JsonNode event;
    try {
      event = mapper.readTree(json);
    } catch (Exception e) {
      log.warn("Unparseable event from the transcription service; ignoring");
      return;
    }
    String type = event.path("type").asText("");
    switch (type) {
      case "session.updated" -> {
        if (state == State.OPENING) {
          state = State.READY;
          readyAt = System.nanoTime();
          tell("ready", null);
        }
      }
      case "conversation.item.input_audio_transcription.delta" ->
          tell("delta", event.path("delta").asText(""));
      case "conversation.item.input_audio_transcription.completed" -> {
        tell("final", event.path("transcript").asText(""));
        if (state == State.STOPPING) {
          finish();
        }
      }
      case "input_audio_buffer.speech_started" -> speech("started");
      case "input_audio_buffer.speech_stopped" -> speech("stopped");
      case "error" -> {
        String code = event.path("error").path("code").asText("");
        // Stop with nothing buffered — the person said nothing after the last phrase. Not an error
        // anyone needs to read.
        if (state == State.STOPPING && code.contains("commit_empty")) {
          finish();
        } else {
          String message = event.path("error").path("message").asText("transcription failed");
          log.warn("Transcription service error: {} {}", code, message);
          fail("transcription failed: " + message);
        }
      }
      default -> {
        // session.created, input_audio_buffer.committed, conversation.item.* bookkeeping: nothing
        // the browser needs.
      }
    }
  }

  @Override
  public synchronized void onClosed(String reason) {
    if (state == State.CLOSED) {
      return;
    }
    if (state == State.STOPPING) {
      finish();
      return;
    }
    log.info("Transcription socket closed mid-dictation: {}", reason);
    fail("the transcription service closed the connection");
  }

  private String sessionUpdate() {
    ObjectNode input = mapper.createObjectNode();
    input.putObject("format").put("type", "audio/pcm").put("rate", 24000);
    input.putObject("noise_reduction").put("type", "near_field");
    ObjectNode transcription = input.putObject("transcription");
    transcription.put("model", props.model());
    if (props.language() != null && !props.language().isBlank()) {
      transcription.put("language", props.language());
    }
    input.putObject("turn_detection").put("type", "server_vad");
    ObjectNode session = mapper.createObjectNode();
    session.put("type", "transcription");
    session.putObject("audio").set("input", input);
    ObjectNode update = mapper.createObjectNode();
    update.put("type", "session.update");
    update.set("session", session);
    return update.toString();
  }

  private void speech(String which) {
    ObjectNode frame = mapper.createObjectNode();
    frame.put("type", "speech");
    frame.put("state", which);
    sink.send(frame.toString());
  }

  private void tell(String type, String text) {
    ObjectNode frame = mapper.createObjectNode();
    frame.put("type", type);
    if (text != null) {
      frame.put("text", text);
    }
    sink.send(frame.toString());
  }

  private synchronized void finish() {
    if (state == State.CLOSED) {
      return;
    }
    closeAll();
  }

  private void fail(String message) {
    if (state == State.CLOSED) {
      return;
    }
    ObjectNode frame = mapper.createObjectNode();
    frame.put("type", "error");
    frame.put("message", message);
    sink.send(frame.toString());
    closeAll();
  }

  private void closeAll() {
    state = State.CLOSED;
    if (connection != null) {
      connection.close();
    }
    sink.close();
  }

  State state() {
    return state;
  }
}
