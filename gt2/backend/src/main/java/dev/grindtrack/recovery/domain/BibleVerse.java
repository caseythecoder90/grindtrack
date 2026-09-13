package dev.grindtrack.recovery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One verse. {@code para} is true when the verse opens a paragraph, which is what the daily
 * passages are cut on; lines of poetry inside a verse are separated by a newline.
 */
@Entity
@Table(name = "bible_verses")
public class BibleVerse {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** USFM code: GEN … REV. */
  @Column(nullable = false, length = 3)
  private String book;

  @Column(name = "book_ord", nullable = false)
  private int bookOrd;

  @Column(nullable = false)
  private int chapter;

  @Column(nullable = false)
  private int verse;

  @Column(nullable = false)
  private boolean para;

  @Column(nullable = false)
  private String text;

  protected BibleVerse() {}

  public BibleVerse(String book, int bookOrd, int chapter, int verse, boolean para, String text) {
    this.book = book;
    this.bookOrd = bookOrd;
    this.chapter = chapter;
    this.verse = verse;
    this.para = para;
    this.text = text;
  }

  public Long getId() {
    return id;
  }

  public String getBook() {
    return book;
  }

  public int getBookOrd() {
    return bookOrd;
  }

  public int getChapter() {
    return chapter;
  }

  public int getVerse() {
    return verse;
  }

  public boolean isPara() {
    return para;
  }

  public String getText() {
    return text;
  }
}
