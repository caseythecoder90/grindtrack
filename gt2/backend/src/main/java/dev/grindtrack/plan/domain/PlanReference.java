package dev.grindtrack.plan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A read-only reference sheet from the plan workbook (Overview, Stripe Target, Weekly Schedule,
 * Resources), stored as row-structured JSON and rendered generically by the frontend.
 */
@Entity
@Table(name = "plan_reference")
public class PlanReference {

  @Id private String sheet;

  @Column(nullable = false)
  private String title;

  @Column(name = "content_json", nullable = false)
  private String contentJson;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  protected PlanReference() {}

  public PlanReference(String sheet, String title, String contentJson, int sortOrder) {
    this.sheet = sheet;
    this.title = title;
    this.contentJson = contentJson;
    this.sortOrder = sortOrder;
  }

  public String getSheet() {
    return sheet;
  }

  public String getTitle() {
    return title;
  }

  public String getContentJson() {
    return contentJson;
  }

  public int getSortOrder() {
    return sortOrder;
  }
}
