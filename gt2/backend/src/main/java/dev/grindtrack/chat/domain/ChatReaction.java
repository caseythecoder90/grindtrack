package dev.grindtrack.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** One person's one emoji on one message. The database keeps the triple unique. */
@Entity
@Table(name = "chat_reactions")
public class ChatReaction {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "message_id", nullable = false, updatable = false)
  private Long messageId;

  @Column(name = "user_id", nullable = false, updatable = false)
  private Long userId;

  @Column(nullable = false, length = 32, updatable = false)
  private String emoji;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt = OffsetDateTime.now();

  protected ChatReaction() {}

  public ChatReaction(Long messageId, Long userId, String emoji) {
    this.messageId = messageId;
    this.userId = userId;
    this.emoji = emoji;
  }

  public Long getId() {
    return id;
  }

  public Long getMessageId() {
    return messageId;
  }

  public Long getUserId() {
    return userId;
  }

  public String getEmoji() {
    return emoji;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}
