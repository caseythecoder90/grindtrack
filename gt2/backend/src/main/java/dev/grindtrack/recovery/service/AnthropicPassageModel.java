package dev.grindtrack.recovery.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import dev.grindtrack.config.AssistantProperties;
import dev.grindtrack.web.UpstreamException;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * One short call per passage, once. The same client and model as the assistant, because it is the
 * same key; the prompt is told who is reading and to keep out of the way.
 */
@Component
public class AnthropicPassageModel implements PassageModel {

  static final String SYSTEM_PROMPT =
      """
      You explain a short Bible passage to a husband and wife reading it together in the \
      morning, inside grindtrack, his tracker. He is in recovery and working the steps; the \
      daily passage was her idea. Neither is a scholar; both want to understand what they just \
      read. The translation is named in the message.

      Plain text only — no markdown, no headings, no bullets, no verse numbers; your words \
      render exactly as written. Three short paragraphs at most, under 180 words in all:
      1. What is happening: who is speaking, to whom, and where this sits in the book. One or \
      two sentences.
      2. What it means: the plain sense of the passage, and any word, name or image a reader \
      today would stumble on. Do not explain what is already obvious.
      3. One sentence at most on what it might mean for an ordinary day — honest, not preachy, \
      no slogans, nothing about recovery unless the passage itself goes there. Leave this \
      paragraph out when the passage does not lend itself to one.
      Do not quote the passage back. Do not add a prayer. Where scholars disagree, say \
      "traditionally" rather than asserting; do not invent history.
      """;

  private final AssistantProperties props;
  private final AnthropicClient client;

  public AnthropicPassageModel(AssistantProperties props) {
    this.props = props;
    this.client =
        props.configured() ? AnthropicOkHttpClient.builder().apiKey(props.apiKey()).build() : null;
  }

  @Override
  public boolean configured() {
    return client != null;
  }

  @Override
  public Explained explain(String reference, String translation, String text) {
    if (client == null) {
      throw new IllegalStateException("explain() called with no API key configured");
    }
    MessageCreateParams params =
        MessageCreateParams.builder()
            .model(props.model())
            .maxTokens(1200L)
            .system(SYSTEM_PROMPT)
            .addUserMessage("The passage is " + reference + " (" + translation + "):\n\n" + text)
            .build();
    try {
      Message response = client.messages().create(params);
      String body =
          response.content().stream()
              .flatMap(block -> block.text().stream())
              .map(t -> t.text())
              .collect(Collectors.joining("\n"))
              .trim();
      if (body.isEmpty()) {
        throw new UpstreamException(
            "the model answered without an explanation ("
                + response.stopReason()
                + ") — try again");
      }
      return new Explained(
          body, props.model(), response.usage().inputTokens(), response.usage().outputTokens());
    } catch (AnthropicServiceException e) {
      throw new UpstreamException("the model call failed (" + e.statusCode() + ") — try again", e);
    }
  }
}
