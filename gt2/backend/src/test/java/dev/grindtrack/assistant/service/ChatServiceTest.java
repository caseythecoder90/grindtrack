package dev.grindtrack.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

  @Mock private ChatModel model;
  @Mock private ContextService contextService;
  @Mock private AssistantConversationRepository conversations;
  @Mock private AssistantMessageRepository messages;

  private ChatService service;

  @BeforeEach
  void setUp() {
    service = new ChatService(model, contextService, conversations, messages, new ObjectMapper());
  }

  private void modelAnswers() {
    when(model.configured()).thenReturn(true);
    when(model.reply(anyString(), any(), anyString()))
        .thenReturn(new ChatModel.Reply("the answer", 3000, 400));
    when(conversations.save(any())).thenAnswer(inv -> inv.getArgument(0));
  }

  @Test
  void chatRefusesWhenOff() {
    when(model.configured()).thenReturn(false);

    assertThatThrownBy(() -> service.chat(null, "hi"))
        .isInstanceOf(ServiceOffException.class)
        .hasMessageContaining("ANTHROPIC_API_KEY");
    verify(model, never()).reply(anyString(), any(), anyString());
  }

  @Test
  void aBlankMessageIsA400NotAModelCall() {
    when(model.configured()).thenReturn(true);

    assertThatThrownBy(() -> service.chat(null, "   ")).isInstanceOf(BadRequestException.class);
    verify(model, never()).reply(anyString(), any(), anyString());
  }

  @Test
  void aNewConversationTakesItsTitleFromTheFirstMessageCutToFit() {
    modelAnswers();
    when(messages.findByConversationIdOrderByIdAsc(any())).thenReturn(List.of());

    service.chat(null, "x".repeat(80));

    ArgumentCaptor<AssistantConversation> saved =
        ArgumentCaptor.forClass(AssistantConversation.class);
    verify(conversations, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
    assertThat(saved.getAllValues().get(0).getTitle()).hasSize(58).endsWith("…");
  }

  @Test
  void bothTurnsArePersistedAndTheBillRidesOnTheAssistants() {
    modelAnswers();
    when(messages.findByConversationIdOrderByIdAsc(any())).thenReturn(List.of());

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
    AssistantConversation existing = new AssistantConversation("t");
    when(conversations.findById(7L)).thenReturn(Optional.of(existing));
    when(messages.findByConversationIdOrderByIdAsc(any()))
        .thenReturn(
            IntStream.range(0, 40)
                .mapToObj(i -> new AssistantMessage(7L, "user", "turn " + i, 0, 0))
                .toList());

    service.chat(7L, "and now?");

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<ChatModel.Turn>> history = ArgumentCaptor.forClass(List.class);
    verify(model).reply(anyString(), history.capture(), eq("and now?"));
    assertThat(history.getValue()).hasSize(30);
    assertThat(history.getValue().get(0).content()).isEqualTo("turn 10");
  }

  @Test
  void anUnknownConversationIsA404() {
    when(model.configured()).thenReturn(true);
    when(conversations.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.chat(99L, "hi")).isInstanceOf(NoSuchElementException.class);
  }
}
