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
 * One structured call, once a morning.
 *
 * <p>The same shape as {@link AnthropicReviewModel} and for the same reasons; the only thing that
 * differs is the prompt, which is told what a brief is <em>not</em> as firmly as what it is.
 */
@Component
public class AnthropicBriefModel implements BriefModel {

  static final String SYSTEM_PROMPT =
      """
      You write the morning brief inside grindtrack, Casey's tracker for a five-year engineering \
      study plan: Kubernetes certifications first, then Java and Spring, Kafka, protocols and \
      distributed systems, aimed at payments and fintech infrastructure. He studies on weekday \
      mornings before work and reads at lunch. Both weekly targets are in the context; use those \
      figures and never a number from this prompt.

      You are given today's context as JSON: the date, this week so far against its targets, \
      plan items in flight and upcoming with their target dates, the coming calendar days, \
      upkeep that is due, open todos, and the recent logged days with Casey's own notes.

      Rules, in order:
      1. It is a brief, not a plan and not a review. Say what is already true about today and \
      offer one thing. Do not schedule the day and do not grade yesterday.
      2. Every number comes from the context, always beside its target where one exists. Never \
      invent a figure, an event, or a plan item; name plan items by their titles.
      3. If yesterday's notes say something, use it — a blocker written last night is the most \
      useful sentence you have. Quote a short phrase rather than paraphrasing.
      4. If nothing is booked and nothing is due, say so in one line; do not manufacture urgency.
      5. Short. Three pieces, a few sentences in all, read in under a minute. Plain text only — \
      no markdown, no bold, no headings, no bullets; your words render exactly as written.
      """;

  private final AssistantProperties props;
  private final AnthropicClient client;

  public AnthropicBriefModel(AssistantProperties props) {
    this.props = props;
    this.client =
        props.configured() ? AnthropicOkHttpClient.builder().apiKey(props.apiKey()).build() : null;
  }

  @Override
  public boolean configured() {
    return client != null;
  }

  @Override
  public DraftedBrief draft(String contextJson) {
    if (client == null) {
      throw new IllegalStateException("draft() called with no API key configured");
    }
    StructuredMessageCreateParams<BriefDraft> params =
        MessageCreateParams.builder()
            .model(props.model())
            .maxTokens(4000L)
            .system(SYSTEM_PROMPT)
            .outputConfig(BriefDraft.class)
            .addUserMessage("Here is today's context. Write the morning brief.\n\n" + contextJson)
            .build();
    try {
      StructuredMessage<BriefDraft> response = client.messages().create(params);
      BriefDraft draft =
          response.content().stream()
              .flatMap(block -> block.text().stream())
              .findFirst()
              .map(text -> text.text())
              .orElseThrow(
                  () ->
                      new UpstreamException(
                          "the model answered without a brief ("
                              + response.stopReason()
                              + ") — try again"));
      return new DraftedBrief(
          draft, props.model(), response.usage().inputTokens(), response.usage().outputTokens());
    } catch (AnthropicServiceException e) {
      throw new UpstreamException("the model call failed (" + e.statusCode() + ") — try again", e);
    }
  }
}
