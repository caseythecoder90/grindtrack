package dev.grindtrack.assistant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * One text turn. Tool calls made while producing an assistant turn are deliberately not stored —
 * they are working state inside a request, and their yield is already in the text.
 */
@Entity
@Table(name = "assistant_messages")
public class AssistantMessage {

  public static final String ROLE_USER = "user";
  public static final String ROLE_ASSISTANT = "assistant";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "conversation_id", nullable = false)
  private Long conversationId;

  @Column(nullable = false)
  private String role;

  @Column(nullable = false)
  private String content;

  @Column(name = "input_tokens", nullable = false)
  private long inputTokens;

  @Column(name = "output_tokens", nullable = false)
  private long outputTokens;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected AssistantMessage() {}

  public AssistantMessage(
      Long conversationId, String role, String content, long inputTokens, long outputTokens) {
    this.conversationId = conversationId;
    this.role = role;
    this.content = content;
    this.inputTokens = inputTokens;
    this.outputTokens = outputTokens;
    this.createdAt = OffsetDateTime.now();
  }

  public String getRole() {
    return role;
  }

  public String getContent() {
    return content;
  }

  public long getInputTokens() {
    return inputTokens;
  }

  public long getOutputTokens() {
    return outputTokens;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}
