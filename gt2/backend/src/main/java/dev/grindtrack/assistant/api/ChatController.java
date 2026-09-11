package dev.grindtrack.assistant.api;

import dev.grindtrack.assistant.service.ChatService;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Talking to the assistant. Every reply may have read anything; nothing here can write anything —
 * the tools behind it are reads by construction, and the service holds the 503-when-off rule.
 */
@RestController
@RequestMapping("/api/assistant/chat")
public class ChatController {

  private final ChatService chat;

  public ChatController(ChatService chat) {
    this.chat = chat;
  }

  /** One turn. Takes ten to thirty seconds; the client should say so rather than spin silently. */
  @PostMapping
  public ChatService.ChatReply send(@RequestBody ChatRequest body) {
    return chat.chat(body.conversationId(), body.message());
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
