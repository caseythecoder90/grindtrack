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
 * The weekly review as one model call. Not an agent, deliberately.
 *
 * <p>The context endpoint already assembles everything a review needs — that is what it was built
 * for — so there is nothing for a tool loop to go and fetch. One request in, one structured
 * response out: cheaper, faster, deterministic to test, and nothing to babysit at five o'clock on a
 * Friday.
 *
 * <p>Structured output does the parsing. The schema is derived from {@link ReviewDraft}, so the
 * response either fills the review form's exact shape or the call fails loudly — there is no "the
 * model wrapped the JSON in prose" case to handle.
 *
 * <p>Thinking is left at the model's default (adaptive, on) and effort at its default (high): a
 * weekly call is not the place to shave cents at the price of a shallower review.
 */
@Component
public class AnthropicReviewModel implements ReviewModel {

  /**
   * Who the model is when it drafts. The persona rules exist because the failure mode of a review
   * written by a model is flattery and vagueness — the two things a Friday review is for cutting
   * through.
   */
  static final String SYSTEM_PROMPT =
      """
      You draft the Friday weekly review in grindtrack, Casey's tracker for a five-year \
      engineering study plan: Kubernetes certifications (the Kubestronaut path), then Java and \
      Spring certification, Kafka, protocols and distributed systems, aimed at payments and \
      fintech infrastructure. Casey studies before work, works a full engineering job, and reads \
      at lunch. Both weekly targets are in the context; use those figures and never a number from \
      this prompt.

      You are given this week's context as JSON: hours against targets, planned versus actual \
      study time, the lunch streak, plan items in flight and upcoming, the recent days with what \
      Casey wrote on them, and where the plan says this quarter should be going.

      Rules, in order:
      1. Write as Casey, first person. The draft is accepted into the review form as-is, so it \
      must read like something Casey would write, not a report about Casey.
      2. Use only numbers that appear in the context, and always against their targets. Never \
      invent a figure, a session, or a plan item.
      3. Name plan items by their titles. "Keep grinding" is not an adjustment; "move the M1 PKI \
      module's mornings ahead of the book" is.
      4. Be specific and a little blunt. If the week fell short, say where and by how much. No \
      filler praise, no "great job staying committed".
      5. Short sentences. No bullet lists inside fields — the form holds plain prose.
      6. The notes Casey wrote on logged days carry the why; quote or echo them where they \
      explain a number.
      """;

  private final AssistantProperties props;
  private final AnthropicClient client;

  public AnthropicReviewModel(AssistantProperties props) {
    this.props = props;
    // Built once here rather than per call: the SDK client holds a connection pool. Null when
    // unconfigured — the service checks configured() before ever calling draft().
    this.client =
        props.configured() ? AnthropicOkHttpClient.builder().apiKey(props.apiKey()).build() : null;
  }

  @Override
  public boolean configured() {
    return client != null;
  }

  @Override
  public DraftedReview draft(String contextJson) {
    if (client == null) {
      throw new IllegalStateException("draft() called with no API key configured");
    }
    StructuredMessageCreateParams<ReviewDraft> params =
        MessageCreateParams.builder()
            .model(props.model())
            .maxTokens(16000L)
            .system(SYSTEM_PROMPT)
            .outputConfig(ReviewDraft.class)
            .addUserMessage(
                "Here is this week's context. Draft the weekly review.\n\n" + contextJson)
            .build();
    try {
      StructuredMessage<ReviewDraft> response = client.messages().create(params);
      ReviewDraft draft =
          response.content().stream()
              .flatMap(block -> block.text().stream())
              .findFirst()
              .map(text -> text.text())
              .orElseThrow(
                  () ->
                      new UpstreamException(
                          "the model answered without a draft ("
                              + response.stopReason()
                              + ") — try again"));
      return new DraftedReview(
          draft, props.model(), response.usage().inputTokens(), response.usage().outputTokens());
    } catch (AnthropicServiceException e) {
      // The API said no — overloaded, rate limited, bad key. The message is for a person reading
      // the week tab, so it says what to do; the cause keeps the detail for the log.
      throw new UpstreamException("the model call failed (" + e.statusCode() + ") — try again", e);
    }
  }
}
