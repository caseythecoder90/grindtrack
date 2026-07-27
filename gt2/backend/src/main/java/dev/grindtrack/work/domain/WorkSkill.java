package dev.grindtrack.work.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * A deliberate operational or domain competency to build on the job (e.g. Splunk, Grafana, querying
 * production databases, tracing the existing system end-to-end) — the experience the role won't
 * force but that makes the résumé and the eventual exit credible. User-managed; never seeded, so no
 * employer specifics live in the public repo.
 */
@Entity
@Table(name = "work_skills")
public class WorkSkill {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String category = "";

  @Column(nullable = false)
  private String detail = "";

  @Column(nullable = false)
  private String status = "not_started";

  @Column(nullable = false)
  private String notes = "";

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt = OffsetDateTime.now();

  protected WorkSkill() {}

  public WorkSkill(String name, String category, String detail, int sortOrder) {
    this.name = name;
    this.category = category == null ? "" : category;
    this.detail = detail == null ? "" : detail;
    this.sortOrder = sortOrder;
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getCategory() {
    return category;
  }

  public String getDetail() {
    return detail;
  }

  public String getStatus() {
    return status;
  }

  public String getNotes() {
    return notes;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public void setName(String name) {
    this.name = name;
    this.updatedAt = OffsetDateTime.now();
  }

  public void setCategory(String category) {
    this.category = category == null ? "" : category;
    this.updatedAt = OffsetDateTime.now();
  }

  public void setDetail(String detail) {
    this.detail = detail == null ? "" : detail;
    this.updatedAt = OffsetDateTime.now();
  }

  public void setStatus(String status) {
    this.status = status;
    this.updatedAt = OffsetDateTime.now();
  }

  public void setNotes(String notes) {
    this.notes = notes == null ? "" : notes;
    this.updatedAt = OffsetDateTime.now();
  }

  public void setSortOrder(int sortOrder) {
    this.sortOrder = sortOrder;
    this.updatedAt = OffsetDateTime.now();
  }
}
