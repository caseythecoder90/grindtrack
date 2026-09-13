package dev.grindtrack.recovery.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class MilestonesTest {

  private static final LocalDate DATE = LocalDate.of(2024, 9, 5);

  @Test
  void dayOneIsTheDateItself() {
    assertThat(Milestones.daysSober(DATE, DATE)).isEqualTo(1);
    assertThat(Milestones.daysSober(DATE, LocalDate.of(2026, 9, 13))).isEqualTo(739);
  }

  @Test
  void spelledOutTheWayItIsSaid() {
    assertThat(Milestones.spelledOut(DATE, LocalDate.of(2026, 9, 13))).isEqualTo("2 years, 8 days");
    assertThat(Milestones.spelledOut(DATE, LocalDate.of(2024, 9, 5))).isEqualTo("0 days");
    assertThat(Milestones.spelledOut(DATE, LocalDate.of(2025, 9, 5))).isEqualTo("1 year");
    assertThat(Milestones.spelledOut(DATE, LocalDate.of(2024, 10, 6))).isEqualTo("1 month, 1 day");
  }

  @Test
  void theNextNumberWorthMarking() {
    assertThat(Milestones.next(739)).isEqualTo(new Milestones.Milestone(750, "750 days"));
    assertThat(Milestones.next(29)).isEqualTo(new Milestones.Milestone(30, "30 days"));
    assertThat(Milestones.next(100)).isEqualTo(new Milestones.Milestone(180, "6 months"));
    assertThat(Milestones.next(364)).isEqualTo(new Milestones.Milestone(365, "1 year"));
    assertThat(Milestones.next(365)).isEqualTo(new Milestones.Milestone(500, "500 days"));
    assertThat(Milestones.next(1000)).isEqualTo(new Milestones.Milestone(1095, "3 years"));
  }
}
