package dev.grindtrack.plan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * One trackable unit of the 4-year plan: a milestone, cert, protocol module, book, or project.
 * Content is loaded via the authenticated import endpoint (the repo is public, so plan text never
 * lives in git); status/notes/completed_at are the user's progress and survive re-imports.
 */
@Entity
@Table(name = "plan_items")
public class PlanItem {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "item_type", nullable = false)
  private String itemType;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false)
  private String details = "";

  @Column(name = "target_label", nullable = false)
  private String targetLabel = "";

  @Column(name = "target_date")
  private LocalDate targetDate;

  @Column(name = "year_num")
  private Integer yearNum;

  private Integer qtr;

  private String tier;

  @Column(nullable = false)
  private String status = "not_started";

  @Column(nullable = false)
  private String notes = "";

  @Column(name = "completed_at")
  private OffsetDateTime completedAt;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  protected PlanItem() {}

  public PlanItem(
      String itemType,
      String title,
      String details,
      String targetLabel,
      LocalDate targetDate,
      Integer yearNum,
      Integer qtr,
      String tier,
      String status,
      String notes,
      int sortOrder) {
    this.itemType = itemType;
    this.title = title;
    this.details = details == null ? "" : details;
    this.targetLabel = targetLabel == null ? "" : targetLabel;
    this.targetDate = targetDate;
    this.yearNum = yearNum;
    this.qtr = qtr;
    this.tier = tier;
    this.status = status;
    this.notes = notes == null ? "" : notes;
    this.sortOrder = sortOrder;
  }

  public Long getId() {
    return id;
  }

  public String getItemType() {
    return itemType;
  }

  public String getTitle() {
    return title;
  }

  public String getDetails() {
    return details;
  }

  public String getTargetLabel() {
    return targetLabel;
  }

  public LocalDate getTargetDate() {
    return targetDate;
  }

  public Integer getYearNum() {
    return yearNum;
  }

  public Integer getQtr() {
    return qtr;
  }

  public String getTier() {
    return tier;
  }

  public String getStatus() {
    return status;
  }

  public String getNotes() {
    return notes;
  }

  public OffsetDateTime getCompletedAt() {
    return completedAt;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  /** Sets status, stamping completed_at on transition to done and clearing it on leaving done. */
  public void setStatus(String status) {
    if ("done".equals(status) && !"done".equals(this.status)) {
      this.completedAt = OffsetDateTime.now();
    } else if (!"done".equals(status)) {
      this.completedAt = null;
    }
    this.status = status;
  }

  public void setNotes(String notes) {
    this.notes = notes == null ? "" : notes;
  }

  /** Used by import to carry existing progress onto the re-imported item. */
  public void carryProgressFrom(PlanItem previous) {
    this.status = previous.status;
    this.completedAt = previous.completedAt;
    if (!previous.notes.isBlank()) {
      this.notes = previous.notes;
    }
  }
}
