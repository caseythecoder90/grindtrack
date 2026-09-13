package dev.grindtrack.recovery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * An imported book: the row the paragraphs or the dated entries hang from. Replacing a book deletes
 * this row, and the database cascades to everything under it.
 */
@Entity
@Table(name = "recovery_texts")
public class RecoveryText {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 16)
  private TextSlot slot;

  @Column(nullable = false, length = 120)
  private String title;

  @Column(name = "imported_at", nullable = false)
  private OffsetDateTime importedAt = OffsetDateTime.now();

  @Column(name = "paragraph_count", nullable = false)
  private int paragraphCount;

  @Column(name = "word_count", nullable = false)
  private int wordCount;

  protected RecoveryText() {}

  public RecoveryText(TextSlot slot, String title, int paragraphCount, int wordCount) {
    this.slot = slot;
    this.title = title;
    this.paragraphCount = paragraphCount;
    this.wordCount = wordCount;
  }

  public Long getId() {
    return id;
  }

  public TextSlot getSlot() {
    return slot;
  }

  public String getTitle() {
    return title;
  }

  public OffsetDateTime getImportedAt() {
    return importedAt;
  }

  public int getParagraphCount() {
    return paragraphCount;
  }

  public int getWordCount() {
    return wordCount;
  }
}
