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
 * One thing that happened.
 *
 * <p>A date, not a timestamp: these get logged the next morning, and the hour is not information
 * worth keeping about any of them.
 */
@Entity
@Table(name = "relationship_moments")
public class Moment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "occurred_on", nullable = false)
  private LocalDate occurredOn;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private MomentKind kind;

  @Column(nullable = false)
  private String note = "";

  /**
   * Optional 1–3: how the week felt, to you.
   *
   * <p>Not a rating of her and not a rating of the event. It is the only subjective number in the
   * feature, and it is never charted — only shown as context on the week it belongs to. A trend
   * line of this would turn a log into a scorecard, which is the one thing this tab must not
   * become.
   */
  @Column(name = "felt_close")
  private Short feltClose;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt = OffsetDateTime.now();

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt = OffsetDateTime.now();

  protected Moment() {}

  public Moment(LocalDate occurredOn, MomentKind kind) {
    this.occurredOn = occurredOn;
    this.kind = kind;
  }

  public void update(LocalDate occurredOn, MomentKind kind, String note, Short feltClose) {
    this.occurredOn = occurredOn;
    this.kind = kind;
    this.note = note == null ? "" : note.trim();
    this.feltClose = feltClose == null || feltClose < 1 || feltClose > 3 ? null : feltClose;
    this.updatedAt = OffsetDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public LocalDate getOccurredOn() {
    return occurredOn;
  }

  public MomentKind getKind() {
    return kind;
  }

  public String getNote() {
    return note;
  }

  public Short getFeltClose() {
    return feltClose;
  }
}
