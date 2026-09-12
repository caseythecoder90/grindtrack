package dev.grindtrack.assistant.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.grindtrack.assistant.service.ChatModel;
import dev.grindtrack.assistant.service.ChatService;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Talking to the assistant. Every reply may have read anything; nothing here can write anything —
 * the tools behind it are reads by construction, and the service holds the 503-when-off rule.
 *
 * <p>There are two ways to ask, answering the same question and storing the same turn. {@code POST
 * /api/assistant/chat} waits and returns the reply; {@code POST /api/assistant/chat/stream} sends
 * it as it arrives. The plain one stays because a stream is a poor thing to test against and a
 * worse thing to curl.
 */
@RestController
@RequestMapping("/api/assistant/chat")
public class ChatController {

  /**
   * How long a turn may hold a connection. The loop allows six model rounds, so this is generous on
   * purpose — a turn cut off at its own timeout would still be billed and still be stored.
   */
  private static final long TURN_TIMEOUT_MS = 300_000;

  private final ChatService chat;
  private final ObjectMapper mapper;
  private final Executor executor;
  private final ScheduledExecutorService heartbeats;

  public ChatController(
      ChatService chat,
      ObjectMapper mapper,
      @Qualifier("assistantTurnExecutor") Executor executor,
      @Qualifier("assistantHeartbeatScheduler") ScheduledExecutorService heartbeats) {
    this.chat = chat;
    this.mapper = mapper;
    this.executor = executor;
    this.heartbeats = heartbeats;
  }

  /** One turn. Takes ten to thirty seconds; the client should say so rather than spin silently. */
  @PostMapping
  public ChatService.ChatReply send(@RequestBody ChatRequest body) {
    return chat.chat(body.conversationId(), body.message());
  }

  /**
   * The same turn, narrated.
   *
   * <p>POST rather than GET, which rules out {@code EventSource} on the client — it only does GET —
   * in favour of reading the response body as a stream. The alternative is posting the question,
   * getting a handle back and opening a second request to watch it, which is two round trips and a
   * piece of server state to invent a lifetime for.
   *
   * <p>The work runs off the request thread so the container thread is free while the model thinks.
   * Nothing under here reads the authenticated principal — the app has one user and the services
   * are written without one — so the security context staying behind on the request thread costs
   * nothing. That stops being true the day a second user exists.
   */
  @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public ResponseEntity<SseEmitter> stream(@RequestBody ChatRequest body) {
    SseEmitter emitter = new SseEmitter(TURN_TIMEOUT_MS);
    ChatStream events = new ChatStream(emitter, mapper);
    // Before the work starts, because the first silence can begin immediately.
    events.beat(heartbeats);
    executor.execute(
        () -> {
          try {
            events.done(
                chat.chat(
                    body.conversationId(),
                    body.message(),
                    new ChatModel.Listener() {
                      @Override
                      public void onToolUse(String toolName) {
                        events.tool(toolName);
                      }

                      @Override
                      public void onText(String delta) {
                        events.text(delta);
                      }

                      @Override
                      public void onProgress() {
                        events.tick();
                      }
                    }));
          } catch (RuntimeException e) {
            events.failed(
                e.getMessage() == null ? "the assistant could not answer" : e.getMessage());
          }
        });
    // Tell any nginx between here and the browser not to buffer this. Without it a proxy is free
    // to hold the whole response and deliver it in one piece at the end, which is precisely the
    // behaviour this endpoint exists to avoid — and it would look like the stream simply not
    // working rather than like a proxy setting. Saying it here rather than in an ingress
    // annotation keeps it true wherever this is deployed.
    return ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform")
        .header("X-Accel-Buffering", "no")
        .body(emitter);
  }

  @GetMapping
  public List<ChatService.ConversationSummary> conversations() {
    return chat.list();
  }

  @GetMapping("/{id}")
  public List<ChatService.TurnView> turns(@PathVariable Long id) {
    return chat.turns(id);
  }

  @DeleteMapping("/{id}")
  public void delete(@PathVariable Long id) {
    chat.delete(id);
  }

  /** Null conversationId starts a new conversation; the reply carries the id to continue it. */
  public record ChatRequest(Long conversationId, String message) {}
}
