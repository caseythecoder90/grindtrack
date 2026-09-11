package dev.grindtrack.assistant.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import com.fasterxml.jackson.core.type.TypeReference;
import dev.grindtrack.assistant.domain.AssistantMessage;
import dev.grindtrack.config.AssistantProperties;
import dev.grindtrack.web.UpstreamException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * The chat loop: ask, let the model read, answer.
 *
 * <p>A manual tool loop rather than the SDK's runner, because the runner instantiates tool classes
 * itself and these tools are thin wrappers over Spring services — the loop is thirty lines and
 * owning it keeps the dispatch in {@link AssistantToolExecutor}, where a test can reach it.
 *
 * <p>Two rules the loop enforces. Every tool result of a round goes back in <em>one</em> user
 * message — splitting them trains the model out of parallel calls. And the loop is bounded: a model
 * that is still reading after six rounds is not going to be saved by a seventh; the turn fails with
 * a message rather than running up a bill.
 */
@Component
public class AnthropicChatModel implements ChatModel {

  static final String SYSTEM_PROMPT =
      """
      You are the assistant inside grindtrack, Casey's tracker for a five-year engineering study \
      plan: Kubernetes certifications (the Kubestronaut path), then Java and Spring \
      certification, Kafka, protocols and distributed systems, aimed at payments and fintech \
      infrastructure. Casey studies mornings against a 20 h/week target, works a full \
      engineering job (40 h/week), and reads at lunch.

      You are given the current context as JSON: this week against its targets, planned versus \
      actual study time, the lunch streak, in-flight and upcoming plan items, coming calendar \
      days, and the recent logged days with Casey's own notes. Four read tools can fetch what \
      the context does not carry: the full plan, past daily logs, calendar ranges, and a day's \
      focus sessions. Use them when the question needs them; do not use them to re-fetch what \
      the context already says.

      Rules, in order:
      1. Ground every number in the context or a tool result, always against its target where \
      one exists. Never invent a figure, a session, or a plan item.
      2. Name plan items by their titles.
      3. Be specific and a little blunt, like a good coach. If the answer is "you are behind, \
      cut something", say that and say what.
      4. Keep it short. A question deserves a paragraph or two, not an essay. Plain text only \
      — no markdown, no bold, no headings; your words render exactly as written. Bullets only \
      when listing genuinely separate items.
      5. You can read everything and change nothing. If asked to log, schedule or edit \
      something, say you cannot and point at the tab that can.
      """;

  private final AssistantProperties props;
  private final AssistantToolExecutor executor;
  private final AnthropicClient client;

  public AnthropicChatModel(AssistantProperties props, AssistantToolExecutor executor) {
    this.props = props;
    this.executor = executor;
    this.client =
        props.configured() ? AnthropicOkHttpClient.builder().apiKey(props.apiKey()).build() : null;
  }

  @Override
  public boolean configured() {
    return client != null;
  }

  @Override
  public Reply reply(String contextJson, List<Turn> history, String userMessage) {
    if (client == null) {
      throw new IllegalStateException("reply() called with no API key configured");
    }
    List<MessageParam> messages = new ArrayList<>();
    for (Turn turn : history) {
      messages.add(
          MessageParam.builder()
              .role(
                  AssistantMessage.ROLE_ASSISTANT.equals(turn.role())
                      ? MessageParam.Role.ASSISTANT
                      : MessageParam.Role.USER)
              .content(turn.content())
              .build());
    }
    messages.add(MessageParam.builder().role(MessageParam.Role.USER).content(userMessage).build());

    long inputTokens = 0;
    long outputTokens = 0;
    try {
      for (int round = 0; round < 6; round++) {
        Message response = client.messages().create(params(contextJson, messages));
        inputTokens += response.usage().inputTokens();
        outputTokens += response.usage().outputTokens();

        if (response.stopReason().filter(StopReason.TOOL_USE::equals).isEmpty()) {
          return new Reply(text(response), inputTokens, outputTokens);
        }
        messages.add(response.toParam());
        messages.add(toolResults(response));
      }
    } catch (AnthropicServiceException e) {
      throw new UpstreamException("the model call failed (" + e.statusCode() + ") — try again", e);
    }
    throw new UpstreamException("the assistant kept reading instead of answering — try again");
  }

  private MessageCreateParams params(String contextJson, List<MessageParam> messages) {
    MessageCreateParams.Builder builder =
        MessageCreateParams.builder()
            .model(props.model())
            .maxTokens(8000L)
            .system(SYSTEM_PROMPT + "\n\nCurrent context:\n" + contextJson);
    for (AssistantToolExecutor.ToolSpec spec : executor.specs()) {
      Tool.InputSchema.Properties.Builder properties = Tool.InputSchema.Properties.builder();
      spec.properties()
          .forEach(
              (name, schema) -> properties.putAdditionalProperty(name, JsonValue.from(schema)));
      builder.addTool(
          Tool.builder()
              .name(spec.name())
              .description(spec.description())
              .inputSchema(Tool.InputSchema.builder().properties(properties.build()).build())
              .build());
    }
    messages.forEach(builder::addMessage);
    return builder.build();
  }

  /** Every tool call of the round, executed, answered in one user message — order preserved. */
  private MessageParam toolResults(Message response) {
    List<ContentBlockParam> results = new ArrayList<>();
    for (ContentBlock block : response.content()) {
      block
          .toolUse()
          .ifPresent(
              use ->
                  results.add(
                      ContentBlockParam.ofToolResult(
                          ToolResultBlockParam.builder()
                              .toolUseId(use.id())
                              .content(executor.execute(use.name(), args(use)))
                              .build())));
    }
    return MessageParam.builder()
        .role(MessageParam.Role.USER)
        .contentOfBlockParams(results)
        .build();
  }

  /** Tool inputs arrive as JSON; parse, never string-match — escaping varies by model. */
  private static Map<String, String> args(ToolUseBlock use) {
    return use._input().convert(new TypeReference<Map<String, String>>() {});
  }

  private static String text(Message response) {
    return response.content().stream()
        .flatMap(block -> block.text().stream())
        .map(TextBlock::text)
        .collect(Collectors.joining("\n"))
        .trim();
  }
}
