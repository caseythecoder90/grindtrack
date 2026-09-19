package dev.grindtrack.chat.service;

import static dev.grindtrack.chat.service.ChatSessions.frame;

import dev.grindtrack.auth.domain.Role;
import dev.grindtrack.auth.domain.User;
import dev.grindtrack.auth.domain.UserRepository;
import dev.grindtrack.auth.security.SignedIn;
import dev.grindtrack.chat.domain.ChatCursor;
import dev.grindtrack.chat.domain.ChatCursorRepository;
import dev.grindtrack.chat.domain.ChatMessage;
import dev.grindtrack.chat.domain.ChatMessageRepository;
import dev.grindtrack.chat.domain.ChatReaction;
import dev.grindtrack.chat.domain.ChatReactionRepository;
import dev.grindtrack.push.service.PushService;
import dev.grindtrack.web.BadRequestException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one room: what is in it, and what happens when something is said.
 *
 * <p>Writes come in over REST and go out over the sockets — every method that changes something
 * ends by handing a frame to {@link ChatSessions}, so a phone with the chat open sees it without
 * asking, and the sender's own other devices too. The methods that write are deliberately not
 * transactional as a whole: the repository call commits on its own, and the broadcast and the push
 * that follow must not hold a database connection while they talk to sockets and to Apple.
 *
 * <p>The push rule: the other person is told about a message unless one of their open sockets
 * delivered it first. No socket open — push at once. A socket open — wait {@link #DELIVERY_GRACE}
 * for their phone to acknowledge; an app in the foreground does within a second, and an app whose
 * socket is open but asleep in the background does not, and gets the push after all.
 */
@Service
public class ChatService {

  private static final Logger log = LoggerFactory.getLogger(ChatService.class);

  static final int MAX_BODY_CHARS = 4000;
  static final int MAX_EMOJI_CHARS = 32;
  static final int PAGE = 50;
  static final int CATCH_UP = 500;
  static final int PREVIEW_CHARS = 120;
  static final Duration DELIVERY_GRACE = Duration.ofSeconds(5);

  private final ChatMessageRepository messages;
  private final ChatReactionRepository reactions;
  private final ChatCursorRepository cursors;
  private final UserRepository users;
  private final ChatSessions sessions;
  private final PushService push;
  private final TaskScheduler scheduler;

  public ChatService(
      ChatMessageRepository messages,
      ChatReactionRepository reactions,
      ChatCursorRepository cursors,
      UserRepository users,
      ChatSessions sessions,
      PushService push,
      TaskScheduler scheduler) {
    this.messages = messages;
    this.reactions = reactions;
    this.cursors = cursors;
    this.users = users;
    this.sessions = sessions;
    this.push = push;
    this.scheduler = scheduler;
  }

  /** Who is in the room, how far each has read, and how much is new for me. */
  @Transactional(readOnly = true)
  public State state(SignedIn me) {
    Optional<User> them = other(me);
    ChatCursor mine = cursor(me.id());
    long latest = latestId();
    long unread =
        messages.countBySenderIdNotAndIdGreaterThanAndDeletedAtIsNull(me.id(), mine.getReadId());
    return new State(
        new Person(me.id(), me.username()),
        them.map(Person::of).orElse(null),
        unread,
        latest,
        CursorView.of(mine),
        them.map(u -> CursorView.of(cursor(u.getId()))).orElse(null));
  }

  /**
   * A page of the room, oldest first within the page.
   *
   * <p>{@code before} is scrolling up: the fifty before that id. {@code after} is catching up: a
   * phone whose socket was away asks for everything since the last id it saw, up to five hundred,
   * and {@code hasMore} tells it to ask again from the last one. Neither means the newest page,
   * which is what no argument gets.
   */
  @Transactional(readOnly = true)
  public Page history(Long before, Long after) {
    if (after != null) {
      List<ChatMessage> rows = messages.findTop500ByIdGreaterThanOrderByIdAsc(after);
      return new Page(views(rows), rows.size() == CATCH_UP);
    }
    List<ChatMessage> rows =
        new ArrayList<>(
            before == null
                ? messages.findTop50ByOrderByIdDesc()
                : messages.findTop50ByIdLessThanOrderByIdDesc(before));
    Collections.reverse(rows);
    return new Page(views(rows), rows.size() == PAGE);
  }

  /**
   * Say something. The same {@code clientId} twice is the same message, answered again rather than
   * stored again — the retry after a dropped connection.
   */
  public MessageView send(SignedIn me, UUID clientId, String body) {
    Optional<ChatMessage> already = messages.findByClientId(clientId);
    if (already.isPresent()) {
      return view(already.get());
    }
    ChatMessage saved = messages.save(new ChatMessage(me.id(), body, clientId));
    MessageView view = view(saved);
    sessions.broadcast(frame("message", "message", view));
    other(me).ifPresent(them -> tell(them, me, view));
    return view;
  }

  private void tell(User them, SignedIn from, MessageView view) {
    if (!push.configured()) {
      return;
    }
    PushService.Notification notification =
        PushService.Notification.chatMessage(from.username(), preview(view.body()));
    if (!sessions.hasOpen(them.getId())) {
      pushQuietly(them.getId(), notification);
      return;
    }
    scheduler.schedule(
        () -> {
          if (cursor(them.getId()).getDeliveredId() < view.id()) {
            pushQuietly(them.getId(), notification);
          }
        },
        Instant.now().plus(DELIVERY_GRACE));
  }

  /** A push that fails is a log line, never a failed send: the message is already in the room. */
  private void pushQuietly(long userId, PushService.Notification notification) {
    try {
      PushService.Outcome outcome = push.sendTo(userId, notification);
      log.info(
          "Chat push to account {}: {} sent, {} failed", userId, outcome.sent(), outcome.failed());
    } catch (RuntimeException e) {
      log.warn("Chat push to account {} failed: {}", userId, e.getMessage());
    }
  }

  /**
   * Take a message back. Only the sender's own; anyone else's is not there, as far as they are
   * concerned. Already unsent is unsent, not an error.
   *
   * @throws NoSuchElementException for another person's message or none — a 404
   */
  public MessageView unsend(SignedIn me, long id) {
    ChatMessage message =
        messages
            .findById(id)
            .filter(m -> m.getSenderId().equals(me.id()))
            .orElseThrow(() -> new NoSuchElementException("message " + id));
    if (!message.isUnsent()) {
      message.unsend();
      message = messages.save(message);
    }
    MessageView view = view(message);
    sessions.broadcast(frame("unsent", "message", view));
    return view;
  }

  /**
   * An emoji on a message, on or off. Idempotent both ways; the answer is the message as it now
   * stands.
   */
  public MessageView react(SignedIn me, long id, String emoji, boolean on) {
    String symbol = requireEmoji(emoji);
    ChatMessage message =
        messages.findById(id).orElseThrow(() -> new NoSuchElementException("message " + id));
    if (message.isUnsent()) {
      throw new BadRequestException("that message was unsent");
    }
    Optional<ChatReaction> existing =
        reactions.findByMessageIdAndUserIdAndEmoji(id, me.id(), symbol);
    if (on && existing.isEmpty()) {
      reactions.save(new ChatReaction(id, me.id(), symbol));
    } else if (!on && existing.isPresent()) {
      reactions.delete(existing.get());
    }
    MessageView view = view(message);
    sessions.broadcast(frame("reaction", "message", view));
    return view;
  }

  /**
   * How far I have got. Forward only, and never past the newest message, so a phone with a wrong
   * number cannot mark tomorrow read. Broadcast when it moved: that is the other person's "seen".
   */
  public CursorView moveCursor(SignedIn me, Long deliveredUpTo, Long readUpTo) {
    long latest = latestId();
    ChatCursor cursor = cursor(me.id());
    boolean moved = cursor.advance(clamp(deliveredUpTo, latest), clamp(readUpTo, latest));
    if (moved) {
      cursor = cursors.save(cursor);
      sessions.broadcast(frame("cursor", "cursor", CursorView.of(cursor)));
    }
    return CursorView.of(cursor);
  }

  /** The other person is (or stopped) typing. Relayed to them alone; nothing is stored. */
  public void typing(SignedIn me, boolean on) {
    other(me)
        .ifPresent(
            them -> sessions.sendTo(them.getId(), frame("typing", "userId", me.id(), "on", on)));
  }

  /**
   * The other person in the room. For the owner, the partner (the first, should there ever be more
   * than one — the room is two people); for a partner, the owner. Empty while there is no partner
   * yet.
   */
  Optional<User> other(SignedIn me) {
    return users.findFirstByRoleOrderByIdAsc(me.role() == Role.OWNER ? Role.PARTNER : Role.OWNER);
  }

  private ChatCursor cursor(long userId) {
    return cursors.findById(userId).orElseGet(() -> new ChatCursor(userId));
  }

  private long latestId() {
    return messages.findTopByOrderByIdDesc().map(ChatMessage::getId).orElse(0L);
  }

  private static Long clamp(Long upTo, long latest) {
    return upTo == null ? null : Math.min(upTo, latest);
  }

  private List<MessageView> views(List<ChatMessage> rows) {
    if (rows.isEmpty()) {
      return List.of();
    }
    Map<Long, List<ReactionView>> byMessage =
        reactions
            .findAllByMessageIdInOrderByIdAsc(rows.stream().map(ChatMessage::getId).toList())
            .stream()
            .collect(
                Collectors.groupingBy(
                    ChatReaction::getMessageId,
                    Collectors.mapping(ReactionView::of, Collectors.toList())));
    return rows.stream()
        .map(m -> MessageView.of(m, byMessage.getOrDefault(m.getId(), List.of())))
        .toList();
  }

  private MessageView view(ChatMessage message) {
    return MessageView.of(
        message,
        reactions.findAllByMessageIdOrderByIdAsc(message.getId()).stream()
            .map(ReactionView::of)
            .toList());
  }

  static String preview(String body) {
    String oneLine = body.replaceAll("\\s+", " ").trim();
    return oneLine.length() <= PREVIEW_CHARS
        ? oneLine
        : oneLine.substring(0, PREVIEW_CHARS - 1) + "…";
  }

  static String requireEmoji(String emoji) {
    String symbol = emoji == null ? "" : emoji.trim();
    if (symbol.isEmpty()
        || symbol.length() > MAX_EMOJI_CHARS
        || symbol.chars().anyMatch(Character::isWhitespace)) {
      throw new BadRequestException("a reaction is one emoji");
    }
    return symbol;
  }

  public record Person(long id, String username) {
    static Person of(User user) {
      return new Person(user.getId(), user.getUsername());
    }
  }

  /**
   * @param them null while there is nobody else yet — an owner before a partner exists
   * @param unread the other person's messages past my read cursor
   * @param latestId the newest message in the room, or 0 when it is empty
   */
  public record State(
      Person me, Person them, long unread, long latestId, CursorView mine, CursorView theirs) {}

  public record Page(List<MessageView> messages, boolean hasMore) {}

  /**
   * @param body empty once unsent
   * @param deletedAt when it was unsent, else null
   */
  public record MessageView(
      long id,
      long senderId,
      String body,
      String sentAt,
      String deletedAt,
      String clientId,
      List<ReactionView> reactions) {
    static MessageView of(ChatMessage m, List<ReactionView> reactions) {
      return new MessageView(
          m.getId(),
          m.getSenderId(),
          m.getBody(),
          m.getSentAt().toString(),
          m.getDeletedAt() == null ? null : m.getDeletedAt().toString(),
          m.getClientId().toString(),
          reactions);
    }
  }

  public record ReactionView(long userId, String emoji) {
    static ReactionView of(ChatReaction r) {
      return new ReactionView(r.getUserId(), r.getEmoji());
    }
  }

  public record CursorView(long userId, long deliveredId, long readId) {
    static CursorView of(ChatCursor c) {
      return new CursorView(c.getUserId(), c.getDeliveredId(), c.getReadId());
    }
  }
}
