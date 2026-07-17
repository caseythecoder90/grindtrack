package dev.grindtrack.tracking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "daily_logs")
public class DailyLog {

  @Id
  @Column(name = "log_date")
  private LocalDate logDate;

  @Column(nullable = false)
  private BigDecimal hours = BigDecimal.ZERO;

  /** Comma-separated category names; exposed as a list via {@link #categoryList()}. */
  @Column(nullable = false)
  private String categories = "";

  @Column(nullable = false)
  private String focus = "";

  @Column(nullable = false)
  private String did = "";

  @Column(nullable = false)
  private String wins = "";

  @Column(nullable = false)
  private String blockers = "";

  private Integer energy;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt = OffsetDateTime.now();

  protected DailyLog() {}

  public DailyLog(LocalDate logDate) {
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

  public String getFocus() {
    return focus;
  }

  public String getDid() {
    return did;
  }

  public String getWins() {
    return wins;
  }

  public String getBlockers() {
    return blockers;
  }

  public Integer getEnergy() {
    return energy;
  }

  public void update(
      BigDecimal hours,
      List<String> categoryList,
      String focus,
      String did,
      String wins,
      String blockers,
      Integer energy) {
    this.hours = hours;
    this.categories = categoryList == null ? "" : String.join(",", categoryList);
    this.focus = focus == null ? "" : focus;
    this.did = did == null ? "" : did;
    this.wins = wins == null ? "" : wins;
    this.blockers = blockers == null ? "" : blockers;
    this.energy = energy;
    this.updatedAt = OffsetDateTime.now();
  }
}
