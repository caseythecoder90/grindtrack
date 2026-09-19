package dev.grindtrack.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * How far one person has got, as two message ids.
 *
 * <p>Everything up to {@code deliveredId} reached one of their devices; everything up to {@code
 * readId} was on their screen. Two watermarks rather than a row per message per person: a two
 * person room needs "seen" under the last message, not a ledger. They only ever move forward — a
 * phone that comes back with an old number cannot un-read anything.
 */
@Entity
@Table(name = "chat_cursors")
public class ChatCursor {

  @Id
  @Column(name = "user_id")
  private Long userId;

  @Column(name = "delivered_id", nullable = false)
  private long deliveredId;

  @Column(name = "read_id", nullable = false)
  private long readId;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt = OffsetDateTime.now();

  protected ChatCursor() {}

  public ChatCursor(Long userId) {
    this.userId = userId;
  }

  /** Forward only. Returns whether anything moved, so a no-op is not broadcast. */
  public boolean advance(Long deliveredUpTo, Long readUpTo) {
    boolean moved = false;
    if (deliveredUpTo != null && deliveredUpTo > deliveredId) {
      deliveredId = deliveredUpTo;
      moved = true;
    }
    if (readUpTo != null && readUpTo > readId) {
      readId = readUpTo;
      moved = true;
    }
    // Read implies delivered: a message on the screen got to the device.
    if (readId > deliveredId) {
      deliveredId = readId;
      moved = true;
    }
    if (moved) {
      updatedAt = OffsetDateTime.now();
    }
    return moved;
  }

  public Long getUserId() {
    return userId;
  }

  public long getDeliveredId() {
    return deliveredId;
  }

  public long getReadId() {
    return readId;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }
}
