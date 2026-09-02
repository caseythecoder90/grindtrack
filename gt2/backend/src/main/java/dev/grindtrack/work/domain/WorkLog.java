package dev.grindtrack.work.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * One day of actual day-job work: hours put in, what the day was for, what got done, blockers, and
 * what was learned. Separate from {@code daily_logs} (personal study time) — this is the 40h/ week
 * accountability and impact journal for the paid role.
 */
@Entity
@Table(name = "work_logs")
public class WorkLog {

  @Id
  @Column(name = "log_date")
  private LocalDate logDate;

  @Column(nullable = false)
  private BigDecimal hours = BigDecimal.ZERO;

  /** Comma-separated category names; exposed as a list via {@link #categoryList()}. */
  @Column(nullable = false)
  private String categories = "";

  @Column(nullable = false)
  private String project = "";

  @Column(nullable = false)
  private String goals = "";

  @Column(nullable = false)
  private String did = "";

  @Column(nullable = false)
  private String blockers = "";

  @Column(nullable = false)
  private String learnings = "";

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt = OffsetDateTime.now();

  protected WorkLog() {}

  public WorkLog(LocalDate logDate) {
    this.logDate = logDate;
  }

  public LocalDate getLogDate() {
    return logDate;
  }

  public BigDecimal getHours() {
    return hours;
  }

  public List<String> categoryList() {
    if (categories == null || categories.isBlank()) {
      return List.of();
    }
    return Arrays.stream(categories.split(",")).map(String::trim).toList();
  }

  public String getProject() {
    return project;
  }

  public String getGoals() {
    return goals;
  }

  public String getDid() {
    return did;
  }

  public String getBlockers() {
    return blockers;
  }

  public String getLearnings() {
    return learnings;
  }

  private static final BigDecimal MAX_DAY_HOURS = BigDecimal.valueOf(24);

  /** Adds hours (e.g. from a finished work focus session), clamped to the DB's 0-24 range. */
  public void addHours(BigDecimal delta) {
    BigDecimal sum = this.hours.add(delta);
    this.hours = sum.compareTo(MAX_DAY_HOURS) > 0 ? MAX_DAY_HOURS : sum;
    this.updatedAt = OffsetDateTime.now();
  }

  /**
   * Sets the day's hours outright — the number a person typed into the form.
   *
   * <p>Separate from {@link #update} for the same reason as {@code DailyLog.setHours}: the form
   * sets a total and the focus timer adds to one, and folding them together let a stale form
   * silently undo logged sessions.
   */
  public void setHours(BigDecimal hours) {
    this.hours = hours;
    this.updatedAt = OffsetDateTime.now();
  }

  /** The written entry. Deliberately does not touch hours — see {@link #setHours}. */
  public void update(
      List<String> categoryList,
      String project,
      String goals,
      String did,
      String blockers,
      String learnings) {
    this.categories = categoryList == null ? "" : String.join(",", categoryList);
    this.project = project == null ? "" : project;
    this.goals = goals == null ? "" : goals;
    this.did = did == null ? "" : did;
    this.blockers = blockers == null ? "" : blockers;
    this.learnings = learnings == null ? "" : learnings;
    this.updatedAt = OffsetDateTime.now();
  }
}
