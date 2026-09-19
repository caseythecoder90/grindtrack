package dev.grindtrack.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Every change goes out over the sockets; the other person is pushed unless their open socket
 * delivered it first; only the sender unsends; cursors move forward and never past the end.
 */
class ChatServiceTest {

  private static final SignedIn CASEY = new SignedIn(1L, "casey", Role.OWNER);
  private static final SignedIn WIFE = new SignedIn(2L, "wife", Role.PARTNER);
  private static final UUID CLIENT_ID = UUID.fromString("6f1d2b6e-1c3a-4f9e-9b2a-1d2e3f4a5b6c");

  private ChatMessageRepository messages;
  private ChatReactionRepository reactions;
  private ChatCursorRepository cursors;
  private UserRepository users;
  private ChatSessions sessions;
  private PushService push;
  private TaskScheduler scheduler;
  private ChatService service;

  @BeforeEach
  void setUp() {
    messages = mock(ChatMessageRepository.class);
    reactions = mock(ChatReactionRepository.class);
    cursors = mock(ChatCursorRepository.class);
    users = mock(UserRepository.class);
    sessions = mock(ChatSessions.class);
    push = mock(PushService.class);
    scheduler = mock(TaskScheduler.class);
    service = new ChatService(messages, reactions, cursors, users, sessions, push, scheduler);

    when(users.findFirstByRoleOrderByIdAsc(Role.PARTNER))
        .thenReturn(Optional.of(account(2L, "wife", Role.PARTNER)));
    when(users.findFirstByRoleOrderByIdAsc(Role.OWNER))
        .thenReturn(Optional.of(account(1L, "casey", Role.OWNER)));
    when(messages.save(any()))
        .thenAnswer(
            inv -> {
              ChatMessage row = inv.getArgument(0);
              if (row.getId() == null) {
                ReflectionTestUtils.setField(row, "id", 10L);
              }
              return row;
            });
    when(cursors.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(reactions.findAllByMessageIdOrderByIdAsc(anyLong())).thenReturn(List.of());
    when(reactions.findAllByMessageIdInOrderByIdAsc(any())).thenReturn(List.of());
    when(push.configured()).thenReturn(true);
    when(push.sendTo(anyLong(), any())).thenReturn(new PushService.Outcome(1, 0, 0));
  }

  private static User account(long id, String username, Role role) {
    User user = new User(username, "hash", "SECRET", role);
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }

  private static ChatMessage message(long id, long senderId, String body) {
    ChatMessage m = new ChatMessage(senderId, body, UUID.randomUUID());
    ReflectionTestUtils.setField(m, "id", id);
    return m;
  }

  private static ChatCursor cursorAt(long userId, long delivered, long read) {
    ChatCursor c = new ChatCursor(userId);
    c.advance(delivered, read);
    return c;
  }

  private static boolean isFrame(Map<String, Object> frame, String type) {
    return type.equals(frame.get("type"));
  }

  @Test
  void aMessageIsStoredBroadcastAndPushedAtOnceWhenTheyHaveNoSocketOpen() {
    when(messages.findByClientId(CLIENT_ID)).thenReturn(Optional.empty());
    when(sessions.hasOpen(2L)).thenReturn(false);

    ChatService.MessageView view = service.send(CASEY, CLIENT_ID, "hi there");

    assertThat(view.id()).isEqualTo(10L);
    assertThat(view.senderId()).isEqualTo(1L);
    assertThat(view.body()).isEqualTo("hi there");
    assertThat(view.clientId()).isEqualTo(CLIENT_ID.toString());
    assertThat(view.deletedAt()).isNull();
    verify(sessions).broadcast(argThat(f -> isFrame(f, "message")));
    ArgumentCaptor<PushService.Notification> pushed =
        ArgumentCaptor.forClass(PushService.Notification.class);
    verify(push).sendTo(eq(2L), pushed.capture());
    assertThat(pushed.getValue().title()).isEqualTo("casey");
    assertThat(pushed.getValue().body()).isEqualTo("hi there");
    assertThat(pushed.getValue().tab()).isEqualTo("chat");
    assertThat(pushed.getValue().tag()).isEqualTo("chat");
    verify(scheduler, never()).schedule(any(Runnable.class), any(Instant.class));
  }

  @Test
  void withASocketOpenThePushWaitsAndIsSkippedOnceThePhoneAcknowledged() {
    when(messages.findByClientId(CLIENT_ID)).thenReturn(Optional.empty());
    when(sessions.hasOpen(2L)).thenReturn(true);

    service.send(CASEY, CLIENT_ID, "are you up?");

    verify(push, never()).sendTo(anyLong(), any());
    ArgumentCaptor<Runnable> later = ArgumentCaptor.forClass(Runnable.class);
    verify(scheduler).schedule(later.capture(), any(Instant.class));

    // The phone never said it got it: the push follows.
    when(cursors.findById(2L)).thenReturn(Optional.of(cursorAt(2L, 9, 0)));
    later.getValue().run();
    verify(push).sendTo(eq(2L), any());

    // It did: nothing more.
    when(cursors.findById(2L)).thenReturn(Optional.of(cursorAt(2L, 10, 0)));
    later.getValue().run();
    verify(push, times(1)).sendTo(eq(2L), any());
  }

  @Test
  void theSameClientIdTwiceIsTheSameMessage() {
    ChatMessage existing = message(7L, 1L, "once");
    when(messages.findByClientId(CLIENT_ID)).thenReturn(Optional.of(existing));

    ChatService.MessageView view = service.send(CASEY, CLIENT_ID, "once");

    assertThat(view.id()).isEqualTo(7L);
    verify(messages, never()).save(any());
    verify(sessions, never()).broadcast(any());
    verify(push, never()).sendTo(anyLong(), any());
  }

  @Test
  void aPushThatFailsIsALogLineNotAFailedSend() {
    when(messages.findByClientId(CLIENT_ID)).thenReturn(Optional.empty());
    when(sessions.hasOpen(2L)).thenReturn(false);
    when(push.sendTo(anyLong(), any())).thenThrow(new IllegalStateException("apple is down"));

    ChatService.MessageView view = service.send(CASEY, CLIENT_ID, "still sent");

    assertThat(view.id()).isEqualTo(10L);
    verify(sessions).broadcast(argThat(f -> isFrame(f, "message")));
  }

  @Test
  void nothingIsPushedWhenPushIsOffOrThereIsNobodyElseYet() {
    when(messages.findByClientId(any())).thenReturn(Optional.empty());
    when(push.configured()).thenReturn(false);
    service.send(CASEY, CLIENT_ID, "into the void");
    verify(push, never()).sendTo(anyLong(), any());
    verify(scheduler, never()).schedule(any(Runnable.class), any(Instant.class));

    when(push.configured()).thenReturn(true);
    when(users.findFirstByRoleOrderByIdAsc(Role.PARTNER)).thenReturn(Optional.empty());
    service.send(CASEY, UUID.randomUUID(), "still nobody");
    verify(push, never()).sendTo(anyLong(), any());
    verify(sessions, times(2)).broadcast(argThat(f -> isFrame(f, "message")));
  }

  @Test
  void onlyTheSenderUnsendsAndTwiceIsOnce() {
    ChatMessage hers = message(4L, 2L, "oops");
    when(messages.findById(4L)).thenReturn(Optional.of(hers));

    assertThatThrownBy(() -> service.unsend(CASEY, 4L)).isInstanceOf(NoSuchElementException.class);
    verify(messages, never()).save(any());

    ChatService.MessageView view = service.unsend(WIFE, 4L);
    assertThat(view.body()).isEmpty();
    assertThat(view.deletedAt()).isNotNull();
    verify(sessions).broadcast(argThat(f -> isFrame(f, "unsent")));

    service.unsend(WIFE, 4L);
    verify(messages, times(1)).save(any());
    verify(sessions, times(2)).broadcast(argThat(f -> isFrame(f, "unsent")));
  }

  @Test
  void aReactionGoesOnOnceComesOffOnceAndNeverOnAnUnsentMessage() {
    ChatMessage his = message(5L, 1L, "look");
    when(messages.findById(5L)).thenReturn(Optional.of(his));
    when(reactions.findByMessageIdAndUserIdAndEmoji(5L, 2L, "❤️")).thenReturn(Optional.empty());
    when(reactions.findAllByMessageIdOrderByIdAsc(5L))
        .thenReturn(List.of(new ChatReaction(5L, 2L, "❤️")));

    ChatService.MessageView view = service.react(WIFE, 5L, " ❤️ ", true);
    assertThat(view.reactions()).containsExactly(new ChatService.ReactionView(2L, "❤️"));
    verify(reactions).save(any());
    verify(sessions).broadcast(argThat(f -> isFrame(f, "reaction")));

    ChatReaction hers = new ChatReaction(5L, 2L, "❤️");
    when(reactions.findByMessageIdAndUserIdAndEmoji(5L, 2L, "❤️")).thenReturn(Optional.of(hers));
    service.react(WIFE, 5L, "❤️", true);
    verify(reactions, times(1)).save(any());
    service.react(WIFE, 5L, "❤️", false);
    verify(reactions).delete(hers);

    assertThatThrownBy(() -> service.react(WIFE, 5L, "two words", true))
        .isInstanceOf(BadRequestException.class);
    assertThatThrownBy(() -> service.react(WIFE, 5L, "", true))
        .isInstanceOf(BadRequestException.class);

    his.unsend();
    assertThatThrownBy(() -> service.react(WIFE, 5L, "❤️", true))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("unsent");
  }

  @Test
  void aCursorMovesForwardOnlyStopsAtTheNewestMessageAndIsBroadcastOnlyWhenItMoved() {
    when(messages.findTopByOrderByIdDesc()).thenReturn(Optional.of(message(10L, 1L, "last")));
    when(cursors.findById(1L)).thenReturn(Optional.empty());

    ChatService.CursorView moved = service.moveCursor(CASEY, 50L, null);
    assertThat(moved.deliveredId()).isEqualTo(10L);
    assertThat(moved.readId()).isEqualTo(0L);
    verify(cursors).save(any());
    verify(sessions).broadcast(argThat(f -> isFrame(f, "cursor")));

    when(cursors.findById(1L)).thenReturn(Optional.of(cursorAt(1L, 10, 0)));
    ChatService.CursorView same = service.moveCursor(CASEY, 5L, null);
    assertThat(same.deliveredId()).isEqualTo(10L);
    verify(cursors, times(1)).save(any());
    verify(sessions, times(1)).broadcast(any());

    ChatService.CursorView read = service.moveCursor(CASEY, null, 7L);
    assertThat(read.readId()).isEqualTo(7L);
    assertThat(read.deliveredId()).isEqualTo(10L);
  }

  @Test
  void readingSomethingMeansItWasDelivered() {
    ChatCursor c = new ChatCursor(2L);
    assertThat(c.advance(null, 6L)).isTrue();
    assertThat(c.getDeliveredId()).isEqualTo(6L);
    assertThat(c.getReadId()).isEqualTo(6L);
    assertThat(c.advance(3L, 3L)).isFalse();
  }

  @Test
  void theStateNamesTheOtherPersonAndCountsWhatIsNewForMe() {
    when(cursors.findById(1L)).thenReturn(Optional.of(cursorAt(1L, 8, 4)));
    when(cursors.findById(2L)).thenReturn(Optional.of(cursorAt(2L, 9, 9)));
    when(messages.findTopByOrderByIdDesc()).thenReturn(Optional.of(message(10L, 2L, "new")));
    when(messages.countBySenderIdNotAndIdGreaterThanAndDeletedAtIsNull(1L, 4L)).thenReturn(3L);

    ChatService.State state = service.state(CASEY);

    assertThat(state.me()).isEqualTo(new ChatService.Person(1L, "casey"));
    assertThat(state.them()).isEqualTo(new ChatService.Person(2L, "wife"));
    assertThat(state.unread()).isEqualTo(3L);
    assertThat(state.latestId()).isEqualTo(10L);
    assertThat(state.mine().readId()).isEqualTo(4L);
    assertThat(state.theirs().readId()).isEqualTo(9L);
  }

  @Test
  void anOwnerWithNoPartnerYetHasAnEmptyRoom() {
    when(users.findFirstByRoleOrderByIdAsc(Role.PARTNER)).thenReturn(Optional.empty());
    when(cursors.findById(1L)).thenReturn(Optional.empty());
    when(messages.findTopByOrderByIdDesc()).thenReturn(Optional.empty());

    ChatService.State state = service.state(CASEY);

    assertThat(state.them()).isNull();
    assertThat(state.theirs()).isNull();
    assertThat(state.latestId()).isZero();
  }

  @Test
  void theNewestPageIsOldestFirstAndAFullPageSaysThereIsMore() {
    List<ChatMessage> newestFirst = new ArrayList<>();
    for (long id = 60; id > 10; id--) {
      newestFirst.add(message(id, id % 2 == 0 ? 1L : 2L, "m" + id));
    }
    when(messages.findTop50ByOrderByIdDesc()).thenReturn(newestFirst);

    ChatService.Page page = service.history(null, null);

    assertThat(page.messages()).hasSize(50);
    assertThat(page.messages().get(0).id()).isEqualTo(11L);
    assertThat(page.messages().get(49).id()).isEqualTo(60L);
    assertThat(page.hasMore()).isTrue();

    when(messages.findTop50ByIdLessThanOrderByIdDesc(11L))
        .thenReturn(List.of(message(9L, 1L, "m9"), message(8L, 2L, "m8")));
    ChatService.Page above = service.history(11L, null);
    assertThat(above.messages()).extracting(ChatService.MessageView::id).containsExactly(8L, 9L);
    assertThat(above.hasMore()).isFalse();

    when(messages.findTop500ByIdGreaterThanOrderByIdAsc(60L))
        .thenReturn(List.of(message(61L, 2L, "m61")));
    ChatService.Page since = service.history(null, 60L);
    assertThat(since.messages()).extracting(ChatService.MessageView::id).containsExactly(61L);
    assertThat(since.hasMore()).isFalse();
  }

  @Test
  void reactionsRideAlongWithAPageInOneQuery() {
    when(messages.findTop50ByOrderByIdDesc()).thenReturn(List.of(message(3L, 1L, "a")));
    when(reactions.findAllByMessageIdInOrderByIdAsc(List.of(3L)))
        .thenReturn(List.of(new ChatReaction(3L, 2L, "😂")));

    ChatService.Page page = service.history(null, null);

    assertThat(page.messages().get(0).reactions())
        .containsExactly(new ChatService.ReactionView(2L, "😂"));
  }

  @Test
  void typingGoesToTheOtherPersonAndNowhereElse() {
    service.typing(CASEY, true);

    verify(sessions)
        .sendTo(eq(2L), argThat(f -> isFrame(f, "typing") && Boolean.TRUE.equals(f.get("on"))));
    verify(sessions, never()).broadcast(any());
  }

  @Test
  void aPreviewIsOneShortLine() {
    assertThat(ChatService.preview("hi\n\nthere   you")).isEqualTo("hi there you");
    String longOne = "x".repeat(300);
    assertThat(ChatService.preview(longOne)).hasSize(ChatService.PREVIEW_CHARS).endsWith("…");
  }
}
