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
        .thenReturn(new ChatModel.Reply("the answer", 3000, 400, 0, 0));
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
              return new ChatModel.Reply("the answer", 3000, 400, 0, 0);
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
        .thenReturn(new ChatModel.Reply("the answer", 300, 400, 5000, 12000));

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
}
