package dev.grindtrack.assistant.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import dev.grindtrack.config.AssistantProperties;
import dev.grindtrack.web.UpstreamException;
import org.springframework.stereotype.Component;

/**
 * Proposing next week's study blocks. One call with structured output, like the review: the context
 * already carries the plan, the calendar and what last week actually looked like.
 */
@Component
public class AnthropicWeekPlanModel implements WeekPlanModel {

  static final String SYSTEM_PROMPT =
      """
      You plan the study week in grindtrack, Casey's tracker for a five-year engineering study \
      plan: Kubernetes certifications (the Kubestronaut path), then Java and Spring \
      certification, Kafka, protocols and distributed systems, aimed at payments and fintech \
      infrastructure.

      Casey studies BEFORE work, on weekday mornings, against a 20 h/week target, and works a \
      full engineering job. You are given the current context as JSON: plan items in flight and \
      upcoming with their ids and target dates, this week's hours against target, what was \
      logged recently with Casey's own notes, and what is already on the calendar.

      Rules, in order:
      1. Book weekday mornings. Start at 06:00 unless the context gives a reason not to; blocks \
      run one to three hours. Do not book over events already on the calendar.
      2. Every block names a real plan item by its id, taken from the context. Never invent an \
      id, and never book a block against nothing.
      3. Prioritise by target date, then by what unblocks what. Something due in five weeks \
      beats something due in five months.
      4. Be realistic, not aspirational. If last week ran at 8 hours, do not book 20 — book what \
      a person who just had that week can actually do, and say so in the rationale.
      5. Three to six blocks. A week with one huge block is not a plan.
      6. The rationale is plain text, first person, two or three sentences. No markdown.
      """;

  private final AssistantProperties props;
  private final AnthropicClient client;

  public AnthropicWeekPlanModel(AssistantProperties props) {
    this.props = props;
    this.client =
        props.configured() ? AnthropicOkHttpClient.builder().apiKey(props.apiKey()).build() : null;
  }

  @Override
  public boolean configured() {
    return client != null;
  }

  @Override
  public PlannedWeek plan(String contextJson, String weekStart) {
    if (client == null) {
      throw new IllegalStateException("plan() called with no API key configured");
    }
    StructuredMessageCreateParams<WeekPlanDraft> params =
        MessageCreateParams.builder()
            .model(props.model())
            .maxTokens(16000L)
            .system(SYSTEM_PROMPT)
            .outputConfig(WeekPlanDraft.class)
            .addUserMessage(
                "Plan the study week beginning Monday "
                    + weekStart
                    + ". Here is the current context.\n\n"
                    + contextJson)
            .build();
    try {
      StructuredMessage<WeekPlanDraft> response = client.messages().create(params);
      WeekPlanDraft draft =
          response.content().stream()
              .flatMap(block -> block.text().stream())
              .findFirst()
              .map(text -> text.text())
              .orElseThrow(
                  () ->
                      new UpstreamException(
                          "the model answered without a plan ("
                              + response.stopReason()
                              + ") — try again"));
      return new PlannedWeek(
          draft, props.model(), response.usage().inputTokens(), response.usage().outputTokens());
    } catch (AnthropicServiceException e) {
      throw new UpstreamException("the model call failed (" + e.statusCode() + ") — try again", e);
    }
  }
}
