package dev.grindtrack.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.grindtrack.assistant.domain.AssistantConversation;
import dev.grindtrack.assistant.domain.AssistantConversationRepository;
import dev.grindtrack.assistant.domain.AssistantMessage;
import dev.grindtrack.assistant.domain.AssistantMessageRepository;
import dev.grindtrack.web.BadRequestException;
import dev.grindtrack.web.ServiceOffException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

  @Mock private ChatModel model;
  @Mock private ContextService contextService;
  @Mock private AssistantConversationRepository conversations;
  @Mock private AssistantMessageRepository messages;

  private ChatService service;

  @BeforeEach
  void setUp() {
    service =
        new ChatService(
            model,
            contextService,
            conversations,
            messages,
            new ObjectMapper(),
            inlineTransactions());
  }

  private void modelAnswers() {
    when(model.configured()).thenReturn(true);
    when(model.reply(anyString(), any(), anyString(), any()))
        .thenReturn(new ChatModel.Reply("the answer", 3000, 400, 0, 0, null, null, null));
    when(conversations.save(any())).thenAnswer(inv -> inv.getArgument(0));
  }

  @Test
  void chatRefusesWhenOff() {
    when(model.configured()).thenReturn(false);

    assertThatThrownBy(() -> service.chat(null, "hi"))
        .isInstanceOf(ServiceOffException.class)
        .hasMessageContaining("ANTHROPIC_API_KEY");
    verify(model, never()).reply(anyString(), any(), anyString(), any());
  }

  @Test
  void aBlankMessageIsA400NotAModelCall() {
    when(model.configured()).thenReturn(true);

    assertThatThrownBy(() -> service.chat(null, "   ")).isInstanceOf(BadRequestException.class);
    verify(model, never()).reply(anyString(), any(), anyString(), any());
  }

  @Test
  void aNewConversationTakesItsTitleFromTheFirstMessageCutToFit() {
    modelAnswers();

    service.chat(null, "x".repeat(80));

    ArgumentCaptor<AssistantConversation> saved =
        ArgumentCaptor.forClass(AssistantConversation.class);
    verify(conversations, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
    assertThat(saved.getAllValues().get(0).getTitle()).hasSize(58).endsWith("…");
  }

  @Test
  void bothTurnsArePersistedAndTheBillRidesOnTheAssistants() {
    modelAnswers();

    ChatService.ChatReply reply = service.chat(null, "how is the week going?");

    assertThat(reply.reply()).isEqualTo("the answer");
    ArgumentCaptor<AssistantMessage> saved = ArgumentCaptor.forClass(AssistantMessage.class);
    verify(messages, org.mockito.Mockito.times(2)).save(saved.capture());
    assertThat(saved.getAllValues().get(0).getRole()).isEqualTo("user");
    assertThat(saved.getAllValues().get(0).getInputTokens()).isZero();
    assertThat(saved.getAllValues().get(1).getRole()).isEqualTo("assistant");
    assertThat(saved.getAllValues().get(1).getInputTokens()).isEqualTo(3000);
    assertThat(saved.getAllValues().get(1).getOutputTokens()).isEqualTo(400);
  }

  @Test
  void historyIsReplayedButOnlyTheLastThirtyTurns() {
    modelAnswers();
    when(conversations.existsById(7L)).thenReturn(true);
    when(conversations.findById(7L)).thenReturn(Optional.of(new AssistantConversation("t")));
    when(messages.findByConversationIdOrderByIdAsc(any()))
        .thenReturn(
            IntStream.range(0, 40)
                .mapToObj(i -> AssistantMessage.userTurn(7L, "turn " + i))
                .toList());

    service.chat(7L, "and now?");

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<ChatModel.Turn>> history = ArgumentCaptor.forClass(List.class);
    verify(model).reply(anyString(), history.capture(), eq("and now?"), any());
    assertThat(history.getValue()).hasSize(30);
    assertThat(history.getValue().get(0).content()).isEqualTo("turn 10");
  }

  /** And a 404 before the model is called, not after: a typo'd id should not cost anything. */
  @Test
  void anUnknownConversationIsA404() {
    when(model.configured()).thenReturn(true);
    when(conversations.existsById(99L)).thenReturn(false);

    assertThatThrownBy(() -> service.chat(99L, "hi")).isInstanceOf(NoSuchElementException.class);
    verify(model, never()).reply(anyString(), any(), anyString(), any());
  }

  /**
   * A failed turn leaves nothing behind. The conversation row used to be written before the model
   * was called, so a turn that threw left an empty thread in the list with no way to remove it.
   */
  @Test
  void aFailedTurnDoesNotCreateAConversation() {
    when(model.configured()).thenReturn(true);
    when(model.reply(anyString(), any(), anyString(), any()))
        .thenThrow(new IllegalStateException("upstream fell over"));

    assertThatThrownBy(() -> service.chat(null, "hi")).isInstanceOf(IllegalStateException.class);

    verify(conversations, never()).save(any());
    verify(messages, never()).save(any());
  }

  /** Tool calls and fragments reach the listener; the stored turn is the same either way. */
  @Test
  void aListenerSeesTheTurnHappenAndChangesNothingAboutIt() {
    when(model.configured()).thenReturn(true);
    when(conversations.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(model.reply(anyString(), any(), anyString(), any()))
        .thenAnswer(
            inv -> {
              ChatModel.Listener listener = inv.getArgument(3);
              listener.onToolUse("get_plan");
              listener.onText("the ");
              listener.onText("answer");
              return new ChatModel.Reply("the answer", 3000, 400, 0, 0, null, null, null);
            });

    List<String> tools = new java.util.ArrayList<>();
    StringBuilder text = new StringBuilder();
    ChatService.ChatReply reply =
        service.chat(
            null,
            "how is the week going?",
            new ChatModel.Listener() {
              @Override
              public void onToolUse(String toolName) {
                tools.add(toolName);
              }

              @Override
              public void onText(String delta) {
                text.append(delta);
              }
            });

    assertThat(tools).containsExactly("get_plan");
    // The fragments are the answer, not a summary of it: what was watched is what was stored.
    assertThat(text.toString()).isEqualTo(reply.reply()).isEqualTo("the answer");
  }

  /** What the cache cost and what it saved is per-turn data; losing it loses the month's bill. */
  @Test
  void cacheUsageIsStoredOnTheAssistantTurn() {
    when(model.configured()).thenReturn(true);
    when(conversations.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(model.reply(anyString(), any(), anyString(), any()))
        .thenReturn(new ChatModel.Reply("the answer", 300, 400, 5000, 12000, null, null, null));

    service.chat(null, "how am I doing?");

    ArgumentCaptor<AssistantMessage> saved = ArgumentCaptor.forClass(AssistantMessage.class);
    verify(messages, times(2)).save(saved.capture());
    AssistantMessage assistant = saved.getAllValues().get(1);
    assertThat(assistant.getCacheWriteTokens()).isEqualTo(5000);
    assertThat(assistant.getCacheReadTokens()).isEqualTo(12000);
    // The user's own turn is billed as part of the reply, never separately.
    AssistantMessage user = saved.getAllValues().get(0);
    assertThat(user.getCacheWriteTokens()).isZero();
    assertThat(user.getInputTokens()).isZero();
  }

  /**
   * A real {@link TransactionTemplate} over a manager that does nothing.
   *
   * <p>Not a mock: the point of the template here is that callbacks run in order and an exception
   * thrown inside one comes back out, and a stubbed {@code execute} would assert that by
   * construction rather than exercise it.
   */
  private static TransactionTemplate inlineTransactions() {
    return new TransactionTemplate(
        new PlatformTransactionManager() {
          @Override
          public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
          }

          @Override
          public void commit(TransactionStatus status) {}

          @Override
          public void rollback(TransactionStatus status) {}
        });
  }

  /**
   * A drafted week has to survive the turn that drafted it. Without this the card is gone the
   * moment the conversation is reopened, and the reply above it refers to something unreachable.
   */
  @Test
  void aDraftedWeekIsStoredOnTheTurnAndHandedToTheClient() {
    when(model.configured()).thenReturn(true);
    when(conversations.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(model.reply(anyString(), any(), anyString(), any()))
        .thenReturn(
            new ChatModel.Reply("drafted four mornings", 300, 400, 0, 0, "2026-09-14", null, null));

    ChatService.ChatReply reply = service.chat(null, "plan next week for me");

    assertThat(reply.proposedWeekStart()).isEqualTo("2026-09-14");
    ArgumentCaptor<AssistantMessage> saved = ArgumentCaptor.forClass(AssistantMessage.class);
    verify(messages, times(2)).save(saved.capture());
    assertThat(saved.getAllValues().get(1).getProposedWeekStart())
        .isEqualTo(java.time.LocalDate.of(2026, 9, 14));
  }

  /** Which is nearly every turn: no card, and nothing on the row to render one from. */
  @Test
  void anOrdinaryTurnProposesNothing() {
    when(model.configured()).thenReturn(true);
    when(conversations.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(model.reply(anyString(), any(), anyString(), any()))
        .thenReturn(new ChatModel.Reply("you are behind", 300, 400, 0, 0, null, null, null));

    assertThat(service.chat(null, "how am I doing?").proposedWeekStart()).isNull();

    ArgumentCaptor<AssistantMessage> saved = ArgumentCaptor.forClass(AssistantMessage.class);
    verify(messages, times(2)).save(saved.capture());
    assertThat(saved.getAllValues().get(1).getProposedWeekStart()).isNull();
  }

  /**
   * The last lock on the door: nothing a model produced may fail a turn at the point of writing it
   * down. By then the answer has been streamed and read, so losing the card is a disappointment and
   * losing the turn is a bug the person cannot work around.
   */
  @Test
  void anUnparseableProposedWeekLosesTheCardAndNotTheTurn() {
    when(model.configured()).thenReturn(true);
    when(conversations.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(model.reply(anyString(), any(), anyString(), any()))
        .thenReturn(
            new ChatModel.Reply("here is your week", 300, 400, 0, 0, "next monday", null, null));

    ChatService.ChatReply reply = service.chat(null, "plan next week");

    assertThat(reply.reply()).isEqualTo("here is your week");
    ArgumentCaptor<AssistantMessage> saved = ArgumentCaptor.forClass(AssistantMessage.class);
    verify(messages, times(2)).save(saved.capture());
    assertThat(saved.getAllValues().get(1).getProposedWeekStart()).isNull();
  }

  /**
   * Two threads that began with the same question have the same title, and a retry begins with
   * exactly the same question — so the list carries what tells them apart: how long each is, and
   * whether a week was drafted inside it.
   */
  @Test
  void theListSaysHowLongEachThreadIsAndWhetherItDraftedAWeek() {
    AssistantConversation real = conversationWithId(1L, "Can you plan my week?");
    AssistantConversation attempt = conversationWithId(2L, "Can you plan my week?");
    when(conversations.findAllByOrderByLastMessageAtDesc()).thenReturn(List.of(real, attempt));
    when(messages.turnCounts()).thenReturn(List.of(new Object[] {1L, 2L}, new Object[] {2L, 1L}));
    when(messages.conversationsWithADraft()).thenReturn(List.of(1L));

    List<ChatService.ConversationSummary> list = service.list();

    assertThat(list).hasSize(2);
    assertThat(list.get(0).turns()).isEqualTo(2);
    assertThat(list.get(0).hasDraft()).isTrue();
    assertThat(list.get(1).turns()).isEqualTo(1);
    assertThat(list.get(1).hasDraft()).isFalse();
  }

  /** A conversation with no messages yet counts as zero turns, not as a missing row. */
  @Test
  void aThreadWithNoTurnsCountsAsZero() {
    // Built before the stub, not inside it: a mock created while another stubbing is open is
    // Mockito's UnfinishedStubbing, and it is the same trap this file fell into once already.
    AssistantConversation empty = conversationWithId(9L, "empty");
    when(conversations.findAllByOrderByLastMessageAtDesc()).thenReturn(List.of(empty));
    when(messages.turnCounts()).thenReturn(List.of());
    when(messages.conversationsWithADraft()).thenReturn(List.of());

    assertThat(service.list().get(0).turns()).isZero();
  }

  private static AssistantConversation conversationWithId(long id, String title) {
    AssistantConversation c = org.mockito.Mockito.mock(AssistantConversation.class);
    when(c.getId()).thenReturn(id);
    when(c.getTitle()).thenReturn(title);
    when(c.getLastMessageAt()).thenReturn(java.time.OffsetDateTime.now());
    return c;
  }
}
