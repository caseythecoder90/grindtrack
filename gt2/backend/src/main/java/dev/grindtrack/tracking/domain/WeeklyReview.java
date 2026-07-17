package dev.grindtrack.tracking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "weekly_reviews")
public class WeeklyReview {

  @Id
  @Column(name = "week_start")
  private LocalDate weekStart;

  @Column(nullable = false)
  private String summary = "";

  @Column(nullable = false)
  private String wins = "";

  @Column(nullable = false)
  private String blockers = "";

  @Column(nullable = false)
  private String adjustments = "";

  @Column(name = "next_focus", nullable = false)
  private String nextFocus = "";

  @Column(name = "on_track")
  private Boolean onTrack;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt = OffsetDateTime.now();

  protected WeeklyReview() {}

  public WeeklyReview(LocalDate weekStart) {
    this.weekStart = weekStart;
  }

  public LocalDate getWeekStart() {
    return weekStart;
  }

  public String getSummary() {
    return summary;
  }

  public String getWins() {
    return wins;
  }

  public String getBlockers() {
    return blockers;
  }

  public String getAdjustments() {
    return adjustments;
  }

  public String getNextFocus() {
    return nextFocus;
  }

  public Boolean getOnTrack() {
    return onTrack;
  }

  public void update(
      String summary,
      String wins,
      String blockers,
      String adjustments,
      String nextFocus,
      Boolean onTrack) {
    this.summary = summary == null ? "" : summary;
    this.wins = wins == null ? "" : wins;
    this.blockers = blockers == null ? "" : blockers;
    this.adjustments = adjustments == null ? "" : adjustments;
    this.nextFocus = nextFocus == null ? "" : nextFocus;
    this.onTrack = onTrack;
    this.updatedAt = OffsetDateTime.now();
  }
}
