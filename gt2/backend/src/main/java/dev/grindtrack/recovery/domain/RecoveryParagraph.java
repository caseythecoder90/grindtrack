package dev.grindtrack.recovery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One paragraph of a book read in order. {@code seq} is the reading order across the whole book,
 * from zero; the cursor in the settings row is a seq. Each paragraph knows the page it starts on,
 * so the day's two pages can be found and the reader can show where a page turns.
 */
@Entity
@Table(name = "recovery_paragraphs")
public class RecoveryParagraph {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "text_id", nullable = false)
  private Long textId;

  @Column(name = "chapter_no", nullable = false)
  private int chapterNo;

  @Column(name = "chapter_title", nullable = false, length = 120)
  private String chapterTitle;

  @Column(nullable = false)
  private int seq;

  @Column(nullable = false)
  private String body;

  @Column(nullable = false)
  private int words;

  /** The page this paragraph starts on, as printed: "xvi", "58". Null before the first number. */
  @Column(name = "page_label", length = 8)
  private String pageLabel;

  /**
   * The same page as a running number across the whole book, from zero: what the assignment counts.
   */
  @Column(name = "page_seq", nullable = false)
  private int pageSeq;

  protected RecoveryParagraph() {}

  public RecoveryParagraph(
      Long textId,
      int chapterNo,
      String chapterTitle,
      int seq,
      String body,
      int words,
      String pageLabel,
      int pageSeq) {
    this.textId = textId;
    this.chapterNo = chapterNo;
    this.chapterTitle = chapterTitle;
    this.seq = seq;
    this.body = body;
    this.words = words;
    this.pageLabel = pageLabel;
    this.pageSeq = pageSeq;
  }

  public String getPageLabel() {
    return pageLabel;
  }

  public int getPageSeq() {
    return pageSeq;
  }

  public Long getId() {
    return id;
  }

  public Long getTextId() {
    return textId;
  }

  public int getChapterNo() {
    return chapterNo;
  }

  public String getChapterTitle() {
    return chapterTitle;
  }

  public int getSeq() {
    return seq;
  }

  public String getBody() {
    return body;
  }

  public int getWords() {
    return words;
  }
}
