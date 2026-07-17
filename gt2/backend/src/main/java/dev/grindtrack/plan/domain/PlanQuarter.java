package dev.grindtrack.plan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One quarter of the roadmap narrative (context only — nothing checkable here). */
@Entity
@Table(name = "plan_quarters")
public class PlanQuarter {

  @Id private Integer qtr;

  @Column(name = "window_label", nullable = false)
  private String windowLabel;

  @Column(name = "year_num", nullable = false)
  private int yearNum;

  @Column(name = "primary_focus", nullable = false)
  private String primaryFocus = "";

  @Column(name = "secondary_focus", nullable = false)
  private String secondaryFocus = "";

  @Column(name = "career_track", nullable = false)
  private String careerTrack = "";

  @Column(nullable = false)
  private String deliverables = "";

  protected PlanQuarter() {}

  public PlanQuarter(
      int qtr,
      String windowLabel,
      int yearNum,
      String primaryFocus,
      String secondaryFocus,
      String careerTrack,
      String deliverables) {
    this.qtr = qtr;
    this.windowLabel = windowLabel;
    this.yearNum = yearNum;
    this.primaryFocus = primaryFocus == null ? "" : primaryFocus;
    this.secondaryFocus = secondaryFocus == null ? "" : secondaryFocus;
    this.careerTrack = careerTrack == null ? "" : careerTrack;
    this.deliverables = deliverables == null ? "" : deliverables;
  }

  public Integer getQtr() {
    return qtr;
  }

  public String getWindowLabel() {
    return windowLabel;
  }

  public int getYearNum() {
    return yearNum;
  }

  public String getPrimaryFocus() {
    return primaryFocus;
  }

  public String getSecondaryFocus() {
    return secondaryFocus;
  }

  public String getCareerTrack() {
    return careerTrack;
  }

  public String getDeliverables() {
    return deliverables;
  }
}
