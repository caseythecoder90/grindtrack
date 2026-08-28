package dev.grindtrack.relationship.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * An article, book or podcast about relationships — and, more importantly, what you took from it.
 *
 * <p>{@code takeaway} is the field that matters. The point of reading one of these is what you
 * would do differently; a reading list without that column is a list of things you can say you
 * read. A takeaway that turns into something concrete is meant to graduate into an {@link Idea}.
 */
@Entity
@Table(name = "relationship_reading")
public class Reading {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String title;

  @Column private String url;

  @Column private String source;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ReadingKind kind = ReadingKind.ARTICLE;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ReadingStatus status = ReadingStatus.TO_READ;

  @Column(nullable = false)
  private String takeaway = "";

  @Column(name = "read_on")
  private LocalDate readOn;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt = OffsetDateTime.now();

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt = OffsetDateTime.now();

  protected Reading() {}

  public Reading(String title, ReadingKind kind) {
    this.title = title.trim();
    this.kind = kind == null ? ReadingKind.ARTICLE : kind;
  }

  public void update(String title, String url, String source, ReadingKind kind) {
    this.title = title.trim();
    this.url = url == null || url.isBlank() ? null : url.trim();
    this.source = source == null || source.isBlank() ? null : source.trim();
    this.kind = kind == null ? ReadingKind.ARTICLE : kind;
    this.updatedAt = OffsetDateTime.now();
  }

  /**
   * Marks it read. The takeaway is what makes this worth recording, so a blank one still marks it
   * read rather than blocking — but the form asks for it, and the list shows which are missing.
   */
  public void markRead(String takeaway, LocalDate readOn) {
    this.status = ReadingStatus.READ;
    this.takeaway = takeaway == null ? "" : takeaway.trim();
    this.readOn = readOn == null ? LocalDate.now() : readOn;
    this.updatedAt = OffsetDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public String getUrl() {
    return url;
  }

  public String getSource() {
    return source;
  }

  public ReadingKind getKind() {
    return kind;
  }

  public ReadingStatus getStatus() {
    return status;
  }

  public String getTakeaway() {
    return takeaway;
  }

  public LocalDate getReadOn() {
    return readOn;
  }
}
