package dev.grindtrack.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * One photo or clip in the bucket, and what the page needs to know to lay it out before it loads.
 *
 * <p>The row is the app's memory of the object: the bucket has the bytes, the row has the key, the
 * kind, the size and the shape. The poster is a small JPEG made on the phone before the upload —
 * the thumbnail of a photo, or a frame of a video — so the thread never loads a full-size object to
 * show a list. Uploaded first, attached to a message second, and gone with the message when that is
 * unsent — unless it is a sticker, a picture kept in the tray to send again, which outlives any one
 * message.
 */
@Entity
@Table(name = "chat_media")
public class ChatMedia {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "owner_id", nullable = false, updatable = false)
  private Long ownerId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16, updatable = false)
  private MediaKind kind;

  @Column(name = "object_key", nullable = false, unique = true, updatable = false)
  private String objectKey;

  @Column(name = "poster_key", updatable = false)
  private String posterKey;

  @Column(name = "content_type", nullable = false, length = 100, updatable = false)
  private String contentType;

  @Column(nullable = false, updatable = false)
  private long bytes;

  @Column(updatable = false)
  private Integer width;

  @Column(updatable = false)
  private Integer height;

  @Column(name = "duration_ms", updatable = false)
  private Integer durationMs;

  @Column(nullable = false)
  private boolean sticker;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt = OffsetDateTime.now();

  protected ChatMedia() {}

  public ChatMedia(
      Long ownerId,
      MediaKind kind,
      String objectKey,
      String posterKey,
      String contentType,
      long bytes,
      Integer width,
      Integer height,
      Integer durationMs) {
    this.ownerId = ownerId;
    this.kind = kind;
    this.objectKey = objectKey;
    this.posterKey = posterKey;
    this.contentType = contentType;
    this.bytes = bytes;
    this.width = width;
    this.height = height;
    this.durationMs = durationMs;
  }

  /** Into the tray, or out of it. */
  public void setSticker(boolean sticker) {
    this.sticker = sticker;
  }

  public boolean isSticker() {
    return sticker;
  }

  public Long getId() {
    return id;
  }

  public Long getOwnerId() {
    return ownerId;
  }

  public MediaKind getKind() {
    return kind;
  }

  public String getObjectKey() {
    return objectKey;
  }

  public String getPosterKey() {
    return posterKey;
  }

  public String getContentType() {
    return contentType;
  }

  public long getBytes() {
    return bytes;
  }

  public Integer getWidth() {
    return width;
  }

  public Integer getHeight() {
    return height;
  }

  public Integer getDurationMs() {
    return durationMs;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}
