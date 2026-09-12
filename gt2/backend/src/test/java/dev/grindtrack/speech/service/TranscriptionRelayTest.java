package dev.grindtrack.speech.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.grindtrack.config.SpeechProperties;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * The relay's two translations — browser frames to service events and back — and the stop
 * handshake, with a fake service on one side and a fake browser on the other.
 */
class TranscriptionRelayTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  /** The service: remembers what it was sent, and lets the test speak for it. */
  private static final class FakeUpstream implements TranscriptionUpstream {
    final List<String> sent = new ArrayList<>();
    final AtomicReference<Listener> listener = new AtomicReference<>();
    final CompletableFuture<Connection> opening = new CompletableFuture<>();
    boolean closed;

    @Override
    public CompletableFuture<Connection> open(Listener l) {
      listener.set(l);
      return opening;
    }

    void connect() {
      opening.complete(
          new Connection() {
            @Override
            public void send(String json) {
              sent.add(json);
            }

            @Override
            public void close() {
              closed = true;
            }
          });
    }

    void says(String json) {
      listener.get().onEvent(json);
    }

    JsonNode lastSent() throws Exception {
      return JSON.readTree(sent.get(sent.size() - 1));
    }
  }

  /** The browser: remembers what it was told. */
  private static final class FakeSink implements TranscriptionRelay.Sink {
    final List<String> frames = new ArrayList<>();
    boolean closed;

    @Override
    public void send(String json) {
      frames.add(json);
    }

    @Override
    public void close() {
      closed = true;
    }

    String types() {
      return String.join(",", frames.stream().map(f -> field(f, "type")).toList());
    }
  }

  private static String field(String json, String name) {
    try {
      return JSON.readTree(json).path(name).asText();
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  private FakeUpstream upstream;
  private FakeSink sink;
  private ScheduledExecutorService scheduler;
  private List<Runnable> scheduled;
  private TranscriptionRelay relay;

  @BeforeEach
  void setUp() {
    upstream = new FakeUpstream();
    sink = new FakeSink();
    scheduled = new ArrayList<>();
    scheduler = Mockito.mock(ScheduledExecutorService.class);
    Mockito.when(scheduler.schedule(Mockito.any(Runnable.class), Mockito.anyLong(), Mockito.any()))
        .thenAnswer(
            inv -> {
              scheduled.add(inv.getArgument(0));
              return Mockito.mock(ScheduledFuture.class);
            });
    relay =
        new TranscriptionRelay(
            new SpeechProperties("sk-test", "gpt-4o-mini-transcribe", "en"),
            upstream,
            JSON,
            scheduler,
            sink);
  }

  private void ready() {
    relay.start();
    upstream.connect();
    upstream.says("{\"type\":\"session.created\",\"session\":{}}");
    upstream.says("{\"type\":\"session.updated\",\"session\":{}}");
  }

  @Test
  void opensATranscriptionSessionInTheConfiguredModelAndSaysReadyOnceItIsAccepted()
      throws Exception {
    relay.start();
    assertThat(sink.frames).isEmpty();
    upstream.connect();

    JsonNode update = upstream.lastSent();
    assertThat(update.path("type").asText()).isEqualTo("session.update");
    JsonNode input = update.path("session").path("audio").path("input");
    assertThat(update.path("session").path("type").asText()).isEqualTo("transcription");
    assertThat(input.path("format").path("type").asText()).isEqualTo("audio/pcm");
    assertThat(input.path("format").path("rate").asInt()).isEqualTo(24000);
    assertThat(input.path("transcription").path("model").asText())
        .isEqualTo("gpt-4o-mini-transcribe");
    assertThat(input.path("transcription").path("language").asText()).isEqualTo("en");
    assertThat(input.path("turn_detection").path("type").asText()).isEqualTo("server_vad");
    assertThat(sink.frames).isEmpty();

    upstream.says("{\"type\":\"session.updated\",\"session\":{}}");
    assertThat(sink.types()).isEqualTo("ready");
    assertThat(relay.state()).isEqualTo(TranscriptionRelay.State.READY);
  }

  @Test
  void audioBecomesAppendEventsAndNothingIsSentBeforeReady() throws Exception {
    relay.start();
    upstream.connect();
    byte[] pcm = {1, 2, 3, 4, 5, 6};
    relay.onAudio(ByteBuffer.wrap(pcm));
    assertThat(upstream.sent).hasSize(1); // only the session.update

    upstream.says("{\"type\":\"session.updated\",\"session\":{}}");
    relay.onAudio(ByteBuffer.wrap(pcm));
    JsonNode append = upstream.lastSent();
    assertThat(append.path("type").asText()).isEqualTo("input_audio_buffer.append");
    assertThat(Base64.getDecoder().decode(append.path("audio").asText())).isEqualTo(pcm);
  }

  @Test
  void transcriptEventsBecomeDeltaAndFinalFramesAndSpeechMarkersPassThrough() {
    ready();
    upstream.says("{\"type\":\"input_audio_buffer.speech_started\",\"audio_start_ms\":0}");
    upstream.says(
        "{\"type\":\"conversation.item.input_audio_transcription.delta\",\"delta\":\"am I on\"}");
    upstream.says(
        "{\"type\":\"conversation.item.input_audio_transcription.delta\",\"delta\":\" pace\"}");
    upstream.says("{\"type\":\"input_audio_buffer.speech_stopped\",\"audio_end_ms\":900}");
    upstream.says(
        "{\"type\":\"conversation.item.input_audio_transcription.completed\","
            + "\"transcript\":\"Am I on pace for the CKA?\"}");

    assertThat(sink.types()).isEqualTo("ready,speech,delta,delta,speech,final");
    assertThat(field(sink.frames.get(2), "text")).isEqualTo("am I on");
    assertThat(field(sink.frames.get(5), "text")).isEqualTo("Am I on pace for the CKA?");
    assertThat(sink.closed).isFalse();
    assertThat(upstream.closed).isFalse();
  }

  /** Stop commits what is buffered and waits for that phrase before closing both sides. */
  @Test
  void stopIsAHandshakeThatLetsTheLastPhraseArrive() throws Exception {
    ready();
    relay.onStop();
    assertThat(upstream.lastSent().path("type").asText()).isEqualTo("input_audio_buffer.commit");
    assertThat(sink.closed).isFalse();
    assertThat(scheduled).hasSize(1);

    upstream.says(
        "{\"type\":\"conversation.item.input_audio_transcription.completed\","
            + "\"transcript\":\"in December\"}");
    assertThat(sink.types()).isEqualTo("ready,final");
    assertThat(sink.closed).isTrue();
    assertThat(upstream.closed).isTrue();
    assertThat(relay.state()).isEqualTo(TranscriptionRelay.State.CLOSED);
  }

  @Test
  void stopWithNothingBufferedIsNotAnErrorAndTheGraceTimerClosesAStuckOne() {
    ready();
    relay.onStop();
    upstream.says(
        "{\"type\":\"error\",\"error\":{\"code\":\"input_audio_buffer_commit_empty\","
            + "\"message\":\"buffer too small\"}}");
    assertThat(sink.types()).isEqualTo("ready");
    assertThat(sink.closed).isTrue();

    // A second relay whose last phrase never comes back: the timer closes it.
    setUp();
    ready();
    relay.onStop();
    scheduled.get(0).run();
    assertThat(sink.closed).isTrue();
    assertThat(upstream.closed).isTrue();
  }

  @Test
  void aServiceErrorMidDictationIsASentenceThenAClose() {
    ready();
    upstream.says(
        "{\"type\":\"error\",\"error\":{\"code\":\"rate_limit\",\"message\":\"slow down\"}}");
    assertThat(sink.types()).isEqualTo("ready,error");
    assertThat(field(sink.frames.get(1), "message")).contains("slow down");
    assertThat(sink.closed).isTrue();
    assertThat(upstream.closed).isTrue();
  }

  @Test
  void theServiceHangingUpMidDictationIsReportedAndTheBrowserClosingIsNot() {
    ready();
    upstream.listener.get().onClosed("closed 1006");
    assertThat(sink.types()).isEqualTo("ready,error");
    assertThat(field(sink.frames.get(1), "message")).contains("closed the connection");

    setUp();
    ready();
    relay.onBrowserClosed();
    assertThat(upstream.closed).isTrue();
    assertThat(sink.types()).isEqualTo("ready");
    // late events after close are ignored rather than sent to a closed browser
    upstream.says(
        "{\"type\":\"conversation.item.input_audio_transcription.delta\",\"delta\":\"x\"}");
    assertThat(sink.types()).isEqualTo("ready");
  }

  @Test
  void aSocketThatCannotOpenIsASentence() {
    relay.start();
    upstream.opening.completeExceptionally(new RuntimeException("connect timed out"));
    assertThat(sink.types()).isEqualTo("error");
    assertThat(field(sink.frames.get(0), "message")).contains("could not reach");
    assertThat(sink.closed).isTrue();
  }
}
