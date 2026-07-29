package dev.grindtrack.todo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * A single actionable item, tagged {@code work} or {@code personal} so the list can be filtered to
 * one side of the day. Deliberately separate from {@code plan_items}: those are the fixed 4-year
 * roadmap (certs, books, projects), while these are short-lived and user-created.
 */
@Entity
@Table(name = "todos")
public class Todo {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String title;

  /** "work" or "personal" — the filter the list is built around. */
  @Column(nullable = false, length = 10)
  private String kind = "personal";

  @Column(nullable = false)
  private boolean done;

  /** Optional; null means no deadline rather than "overdue". */
  @Column(name = "due_date")
  private LocalDate dueDate;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  /** Set when {@code done} flips true, cleared when it flips back. */
  @Column(name = "completed_at")
  private OffsetDateTime completedAt;

  @Column(name = "created_at", insertable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt = OffsetDateTime.now();

  protected Todo() {}

  public Todo(String title, String kind, LocalDate dueDate, int sortOrder) {
    this.title = title;
    this.kind = "work".equals(kind) ? "work" : "personal";
    this.dueDate = dueDate;
    this.sortOrder = sortOrder;
  }

  public Long getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public String getKind() {
    return kind;
  }

  public boolean isDone() {
    return done;
  }

  public LocalDate getDueDate() {
    return dueDate;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public OffsetDateTime getCompletedAt() {
    return completedAt;
  }

  public void setTitle(String title) {
    this.title = title;
    touch();
  }

  public void setKind(String kind) {
    this.kind = "work".equals(kind) ? "work" : "personal";
    touch();
  }

  /** Flipping this keeps {@code completedAt} in step, so un-checking an item really resets it. */
  public void setDone(boolean done) {
    this.done = done;
    this.completedAt = done ? OffsetDateTime.now() : null;
    touch();
  }

  public void setDueDate(LocalDate dueDate) {
    this.dueDate = dueDate;
    touch();
  }

  public void setSortOrder(int sortOrder) {
    this.sortOrder = sortOrder;
    touch();
  }

  private void touch() {
    this.updatedAt = OffsetDateTime.now();
  }
}
