package dev.grindtrack.assistant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** One thread with the assistant. The title is the first message, cut to fit a list row. */
@Entity
@Table(name = "assistant_conversations")
public class AssistantConversation {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String title;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "last_message_at", nullable = false)
  private OffsetDateTime lastMessageAt;

  protected AssistantConversation() {}

  public AssistantConversation(String title) {
    this.title = title;
    this.createdAt = OffsetDateTime.now();
    this.lastMessageAt = this.createdAt;
  }

  public void touch() {
    this.lastMessageAt = OffsetDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public OffsetDateTime getLastMessageAt() {
    return lastMessageAt;
  }
}
