package dev.grindtrack.relationship.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Something you thought of on a good day, kept for a day when you have no ideas.
 *
 * <p>That is the whole job. Nobody is short of goodwill on a Tuesday evening; they are short of a
 * specific thing to do. {@code effort} exists so the list can lead with the two-minute options,
 * because the deciding factor is almost never the idea itself.
 */
@Entity
@Table(name = "relationship_ideas")
public class Idea {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private IdeaKind kind = IdeaKind.GIFT;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false)
  private String detail = "";

  /**
   * Free text, not a link to an occasion row.
   *
   * <p>"her birthday" is useful on an idea long before you have bothered to create the occasion,
   * and the association only ever needs to be advisory.
   */
  @Column private String occasion;

  @Column(name = "est_cost")
  private BigDecimal estCost;

  @Enumerated(EnumType.STRING)
  @Column
  private Effort effort;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private IdeaStatus status = IdeaStatus.IDEA;

  /** The moment this became, once it was acted on. Closes the loop. */
  @Column(name = "done_moment_id")
  private Long doneMomentId;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt = OffsetDateTime.now();

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt = OffsetDateTime.now();

  protected Idea() {}

  public Idea(IdeaKind kind, String title) {
    this.kind = kind == null ? IdeaKind.GIFT : kind;
    this.title = title.trim();
  }

  public void update(
      IdeaKind kind,
      String title,
      String detail,
      String occasion,
      BigDecimal estCost,
      Effort effort,
      IdeaStatus status) {
    this.kind = kind == null ? IdeaKind.GIFT : kind;
    this.title = title.trim();
    this.detail = detail == null ? "" : detail.trim();
    this.occasion = occasion == null || occasion.isBlank() ? null : occasion.trim();
    this.estCost = estCost == null || estCost.signum() < 0 ? null : estCost;
    this.effort = effort;
    this.status = status == null ? IdeaStatus.IDEA : status;
    this.updatedAt = OffsetDateTime.now();
  }

  /** Marks the idea done and records which moment it turned into. */
  public void completedAs(Long momentId) {
    this.status = IdeaStatus.DONE;
    this.doneMomentId = momentId;
    this.updatedAt = OffsetDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public IdeaKind getKind() {
    return kind;
  }

  public String getTitle() {
    return title;
  }

  public String getDetail() {
    return detail;
  }

  public String getOccasion() {
    return occasion;
  }

  public BigDecimal getEstCost() {
    return estCost;
  }

  public Effort getEffort() {
    return effort;
  }

  public IdeaStatus getStatus() {
    return status;
  }

  public Long getDoneMomentId() {
    return doneMomentId;
  }
}
