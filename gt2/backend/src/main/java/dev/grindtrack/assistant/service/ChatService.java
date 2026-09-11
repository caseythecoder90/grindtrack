package dev.grindtrack.assistant.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.grindtrack.assistant.domain.AssistantConversation;
import dev.grindtrack.assistant.domain.AssistantConversationRepository;
import dev.grindtrack.assistant.domain.AssistantMessage;
import dev.grindtrack.assistant.domain.AssistantMessageRepository;
import dev.grindtrack.web.Requests;
import dev.grindtrack.web.ServiceOffException;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Conversations: persistence and replay around {@link ChatModel}.
 *
 * <p>History is replayed as plain text turns. The tool calls a past reply made are not replayed —
 * their yield is in the reply's text — which keeps a turn's cost proportional to the conversation a
 * person can actually see.
 */
@Service
public class ChatService {

  /** Turns replayed per request. Beyond this the oldest fall off; the title keeps the thread. */
  private static final int HISTORY_TURNS = 30;

  private final ChatModel model;
  private final ContextService contextService;
  private final AssistantConversationRepository conversations;
  private final AssistantMessageRepository messages;
  private final ObjectMapper mapper;

  public ChatService(
      ChatModel model,
      ContextService contextService,
      AssistantConversationRepository conversations,
      AssistantMessageRepository messages,
      ObjectMapper mapper) {
    this.model = model;
    this.contextService = contextService;
    this.conversations = conversations;
    this.messages = messages;
    this.mapper = mapper;
  }

  @Transactional
  public ChatReply chat(Long conversationId, String rawMessage) {
    if (!model.configured()) {
      throw new ServiceOffException(
          "the assistant is off — set ANTHROPIC_API_KEY on the deployment to turn it on");
    }
    String message = Requests.requireText(rawMessage, "message needs some text", 2000);

    AssistantConversation conversation =
        conversationId == null
            ? conversations.save(new AssistantConversation(title(message)))
            : conversations
                .findById(conversationId)
                .orElseThrow(() -> new NoSuchElementException("conversation " + conversationId));

    List<ChatModel.Turn> history =
        messages.findByConversationIdOrderByIdAsc(conversation.getId()).stream()
            .map(m -> new ChatModel.Turn(m.getRole(), m.getContent()))
            .collect(java.util.stream.Collectors.toList());
    if (history.size() > HISTORY_TURNS) {
      history = history.subList(history.size() - HISTORY_TURNS, history.size());
    }

    ChatModel.Reply reply = model.reply(context(), history, message);

    messages.save(AssistantMessage.userTurn(conversation.getId(), message));
    messages.save(
        new AssistantMessage(
            conversation.getId(),
            AssistantMessage.ROLE_ASSISTANT,
            reply.text(),
            reply.inputTokens(),
            reply.outputTokens(),
            reply.cacheWriteTokens(),
            reply.cacheReadTokens()));
    conversation.touch();
    conversations.save(conversation);

    return new ChatReply(
        conversation.getId(), reply.text(), reply.inputTokens(), reply.outputTokens());
  }

  @Transactional(readOnly = true)
  public List<ConversationSummary> list() {
    return conversations.findAllByOrderByLastMessageAtDesc().stream()
        .map(c -> new ConversationSummary(c.getId(), c.getTitle(), c.getLastMessageAt().toString()))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<TurnView> turns(Long conversationId) {
    if (!conversations.existsById(conversationId)) {
      throw new NoSuchElementException("conversation " + conversationId);
    }
    return messages.findByConversationIdOrderByIdAsc(conversationId).stream()
        .map(m -> new TurnView(m.getRole(), m.getContent(), m.getCreatedAt().toString()))
        .toList();
  }

  @Transactional
  public void delete(Long conversationId) {
    if (!conversations.existsById(conversationId)) {
      throw new NoSuchElementException("conversation " + conversationId);
    }
    conversations.deleteById(conversationId);
  }

  private String context() {
    try {
      return mapper.writeValueAsString(contextService.build(LocalDate.now()));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("could not serialize the context", e);
    }
  }

  /** The first message, cut to a list row. Cutting beats asking the model to summarize a title. */
  private static String title(String message) {
    return message.length() <= 60 ? message : message.substring(0, 57) + "…";
  }

  public record ChatReply(Long conversationId, String reply, long inputTokens, long outputTokens) {}

  public record ConversationSummary(Long id, String title, String lastMessageAt) {}

  public record TurnView(String role, String content, String createdAt) {}
}
