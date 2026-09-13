package dev.grindtrack.recovery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * A file as uploaded, kept so the book can be parsed again without being uploaded again. A few
 * megabytes for the whole book; the bytes are never sent back out.
 */
@Entity
@Table(name = "recovery_files")
public class RecoveryFile {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 16)
  private TextSlot slot;

  @Column(nullable = false)
  private int ordinal;

  @Column(nullable = false, length = 200)
  private String filename;

  @Column(nullable = false)
  private byte[] bytes;

  @Column(nullable = false)
  private int size;

  @Column(name = "uploaded_at", nullable = false)
  private OffsetDateTime uploadedAt = OffsetDateTime.now();

  protected RecoveryFile() {}

  public RecoveryFile(TextSlot slot, int ordinal, String filename, byte[] bytes) {
    this.slot = slot;
    this.ordinal = ordinal;
    this.filename = filename;
    this.bytes = bytes;
    this.size = bytes.length;
  }

  public Long getId() {
    return id;
  }

  public TextSlot getSlot() {
    return slot;
  }

  public int getOrdinal() {
    return ordinal;
  }

  public String getFilename() {
    return filename;
  }

  public byte[] getBytes() {
    return bytes;
  }

  public int getSize() {
    return size;
  }

  public OffsetDateTime getUploadedAt() {
    return uploadedAt;
  }
}
