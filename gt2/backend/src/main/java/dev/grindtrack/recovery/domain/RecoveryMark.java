package dev.grindtrack.recovery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * A highlight, a note, or both, on a paragraph of a book — the pencil in the margin.
 *
 * <p>The whole paragraph, or a run of words in it ({@code startOff}/{@code endOff}, offsets into
 * the body). {@code quote} is the words marked; it is how the mark finds its paragraph again after
 * the book is imported afresh, since the paragraphs are replaced then.
 */
@Entity
@Table(name = "recovery_marks")
public class RecoveryMark {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 20)
  private TextSlot slot;

  @Column(nullable = false)
  private int seq;

  @Column(name = "page_label", length = 8)
  private String pageLabel;

  @Column(name = "start_off")
  private Integer startOff;

  @Column(name = "end_off")
  private Integer endOff;

  @Column(nullable = false)
  private String quote;

  /** Null: a note with no highlight. */
  @Column(length = 12)
  private String color;

  private String note;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt = OffsetDateTime.now();

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt = OffsetDateTime.now();

  protected RecoveryMark() {}

  public RecoveryMark(
      TextSlot slot,
      int seq,
      String pageLabel,
      Integer startOff,
      Integer endOff,
      String quote,
      String color,
      String note) {
    this.slot = slot;
    this.seq = seq;
    this.pageLabel = pageLabel;
    this.startOff = startOff;
    this.endOff = endOff;
    this.quote = quote;
    this.color = color;
    this.note = note;
  }

  /** The words are the same; only where they are has moved. */
  public void moveTo(int seq, String pageLabel, Integer startOff, Integer endOff) {
    this.seq = seq;
    this.pageLabel = pageLabel;
    this.startOff = startOff;
    this.endOff = endOff;
  }

  public void setColor(String color) {
    this.color = color;
    this.updatedAt = OffsetDateTime.now();
  }

  public void setNote(String note) {
    this.note = note;
    this.updatedAt = OffsetDateTime.now();
  }

  public boolean isWholeParagraph() {
    return startOff == null;
  }

  public Long getId() {
    return id;
  }

  public TextSlot getSlot() {
    return slot;
  }

  public int getSeq() {
    return seq;
  }

  public String getPageLabel() {
    return pageLabel;
  }

  public Integer getStartOff() {
    return startOff;
  }

  public Integer getEndOff() {
    return endOff;
  }

  public String getQuote() {
    return quote;
  }

  public String getColor() {
    return color;
  }

  public String getNote() {
    return note;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }
}
