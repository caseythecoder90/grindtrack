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
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Conversations: persistence and replay around {@link ChatModel}.
 *
 * <p>History is replayed as plain text turns. The tool calls a past reply made are not replayed —
 * their yield is in the reply's text — which keeps a turn's cost proportional to the conversation a
 * person can actually see.
 *
 * <p>A turn is three phases and only the outer two touch the database, which is why this class
 * drives transactions by hand instead of wearing {@code @Transactional} over the whole thing. The
 * model call in the middle takes ten to thirty seconds; holding a pooled connection open across it
 * would pin one of ten connections to a request that is doing nothing but waiting on a socket.
 *
 * <p>Splitting it that way also fixes something the single transaction got wrong: the conversation
 * row is now written <em>after</em> the reply arrives, so a turn that fails no longer leaves an
 * empty thread in the list with nothing in it.
 */
@Service
public class ChatService {

  private static final Logger log = LoggerFactory.getLogger(ChatService.class);

  /** Turns replayed per request. Beyond this the oldest fall off; the title keeps the thread. */
  private static final int HISTORY_TURNS = 30;

  private final ChatModel model;
  private final ContextService contextService;
  private final AssistantConversationRepository conversations;
  private final AssistantMessageRepository messages;
  private final ObjectMapper mapper;
  private final TransactionTemplate transactions;

  public ChatService(
      ChatModel model,
      ContextService contextService,
      AssistantConversationRepository conversations,
      AssistantMessageRepository messages,
      ObjectMapper mapper,
      TransactionTemplate transactions) {
    this.model = model;
    this.contextService = contextService;
    this.conversations = conversations;
    this.messages = messages;
    this.mapper = mapper;
    this.transactions = transactions;
  }

  /** One turn, answered when it is finished. */
  public ChatReply chat(Long conversationId, String rawMessage) {
    return chat(conversationId, rawMessage, ChatModel.Listener.NONE);
  }

  /**
   * One turn, with somebody watching it happen.
   *
   * <p>The same turn either way: the listener sees the tool calls and the answer arriving, and the
   * return value is what gets stored. Nothing about what is saved, billed or replayed depends on
   * whether anybody was watching.
   */
  public ChatReply chat(Long conversationId, String rawMessage, ChatModel.Listener listener) {
    if (!model.configured()) {
      throw new ServiceOffException(
          "the assistant is off — set ANTHROPIC_API_KEY on the deployment to turn it on");
    }
    String message = Requests.requireText(rawMessage, "message needs some text", 2000);

    List<ChatModel.Turn> history = replay(conversationId);
    String context = transactions.execute(tx -> context());

    ChatModel.Reply reply = model.reply(context, history, message, listener);

    return store(conversationId, message, reply);
  }

  /** The turns to replay, and the check that the conversation exists — before a penny is spent. */
  private List<ChatModel.Turn> replay(Long conversationId) {
    if (conversationId == null) {
      return List.of();
    }
    return transactions.execute(
        tx -> {
          if (!conversations.existsById(conversationId)) {
            throw new NoSuchElementException("conversation " + conversationId);
          }
          List<ChatModel.Turn> turns =
              messages.findByConversationIdOrderByIdAsc(conversationId).stream()
                  .map(m -> new ChatModel.Turn(m.getRole(), m.getContent()))
                  .toList();
          return turns.size() > HISTORY_TURNS
              ? turns.subList(turns.size() - HISTORY_TURNS, turns.size())
              : turns;
        });
  }

  /** Both turns and the conversation, written together once the answer exists. */
  private ChatReply store(Long conversationId, String message, ChatModel.Reply reply) {
    return transactions.execute(
        tx -> {
          AssistantConversation conversation =
              conversationId == null
                  ? conversations.save(new AssistantConversation(title(message)))
                  : conversations
                      .findById(conversationId)
                      .orElseThrow(
                          () -> new NoSuchElementException("conversation " + conversationId));

          messages.save(AssistantMessage.userTurn(conversation.getId(), message));
          messages.save(
              new AssistantMessage(
                  conversation.getId(),
                  AssistantMessage.ROLE_ASSISTANT,
                  reply.text(),
                  reply.inputTokens(),
                  reply.outputTokens(),
                  reply.cacheWriteTokens(),
                  reply.cacheReadTokens(),
                  monday(reply.proposedWeekStart())));
          conversation.touch();
          conversations.save(conversation);

          return new ChatReply(
              conversation.getId(),
              reply.text(),
              reply.inputTokens(),
              reply.outputTokens(),
              reply.proposedWeekStart());
        });
  }

  @Transactional(readOnly = true)
  public List<ConversationSummary> list() {
    Map<Long, Long> turns = new HashMap<>();
    for (Object[] row : messages.turnCounts()) {
      turns.put((Long) row[0], (Long) row[1]);
    }
    Set<Long> drafted = new HashSet<>(messages.conversationsWithADraft());
    return conversations.findAllByOrderByLastMessageAtDesc().stream()
        .map(
            c ->
                new ConversationSummary(
                    c.getId(),
                    c.getTitle(),
                    c.getLastMessageAt().toString(),
                    turns.getOrDefault(c.getId(), 0L),
                    drafted.contains(c.getId())))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<TurnView> turns(Long conversationId) {
    if (!conversations.existsById(conversationId)) {
      throw new NoSuchElementException("conversation " + conversationId);
    }
    return messages.findByConversationIdOrderByIdAsc(conversationId).stream()
        .map(
            m ->
                new TurnView(
                    m.getRole(),
                    m.getContent(),
                    m.getCreatedAt().toString(),
                    m.getProposedWeekStart() == null ? null : m.getProposedWeekStart().toString()))
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

  /**
   * The drafted week, or null if it is not a date.
   *
   * <p>The model half of this app must never be able to fail a turn at the point of writing it
   * down. By the time this runs the answer has been streamed and read; losing the card is a
   * disappointment, losing the whole turn to a parse error is a bug the person cannot work around.
   * {@code AnthropicChatModel} already only reports weeks a tool actually drafted — this is the
   * second lock on the same door, because that door is on the far side of everything expensive.
   */
  private static LocalDate monday(String value) {
    if (value == null) {
      return null;
    }
    try {
      return LocalDate.parse(value);
    } catch (DateTimeParseException e) {
      log.warn("ignoring an unparseable proposed week from the model: {}", value);
      return null;
    }
  }

  /** The first message, cut to a list row. Cutting beats asking the model to summarize a title. */
  private static String title(String message) {
    return message.length() <= 60 ? message : message.substring(0, 57) + "…";
  }

  /**
   * @param proposedWeekStart the Monday this turn drafted a week for, or null. The client renders a
   *     card for it; the draft itself is fetched from the week-plan endpoint, and accepting is a
   *     separate call
   */
  public record ChatReply(
      Long conversationId,
      String reply,
      long inputTokens,
      long outputTokens,
      String proposedWeekStart) {}

  /**
   * @param turns messages in the thread, both sides. One means an attempt, not a conversation
   * @param hasDraft whether any turn drafted a week — worth knowing before deleting the thread,
   *     though the draft itself lives with the week and survives the thread being removed
   */
  public record ConversationSummary(
      Long id, String title, String lastMessageAt, long turns, boolean hasDraft) {}

  public record TurnView(String role, String content, String createdAt, String proposedWeekStart) {}
}
