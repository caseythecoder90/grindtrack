package dev.grindtrack.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One message in the one room.
 *
 * <p>The id is the order: a phone that knows the last id it saw asks for everything after it and
 * gets the gap, whatever its clock says. {@code clientId} is the phone's own name for the message,
 * generated before the first attempt to send, so a retry after a dropped connection finds the row
 * it already made instead of making a second. Unsending clears the body and dates the row rather
 * than deleting it, so the other side's list keeps its shape and shows "unsent" where the words
 * were.
 */
@Entity
@Table(name = "chat_messages")
public class ChatMessage {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "sender_id", nullable = false, updatable = false)
  private Long senderId;

  @Column(nullable = false)
  private String body;

  @Column(name = "client_id", nullable = false, unique = true, updatable = false)
  private UUID clientId;

  @Column(name = "sent_at", nullable = false, updatable = false)
  private OffsetDateTime sentAt = OffsetDateTime.now();

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;

  protected ChatMessage() {}

  public ChatMessage(Long senderId, String body, UUID clientId) {
    this.senderId = senderId;
    this.body = body;
    this.clientId = clientId;
  }

  /** Unsend: the words go, the place stays. */
  public void unsend() {
    this.body = "";
    this.deletedAt = OffsetDateTime.now();
  }

  public boolean isUnsent() {
    return deletedAt != null;
  }

  public Long getId() {
    return id;
  }

  public Long getSenderId() {
    return senderId;
  }

  public String getBody() {
    return body;
  }

  public UUID getClientId() {
    return clientId;
  }

  public OffsetDateTime getSentAt() {
    return sentAt;
  }

  public OffsetDateTime getDeletedAt() {
    return deletedAt;
  }
}
