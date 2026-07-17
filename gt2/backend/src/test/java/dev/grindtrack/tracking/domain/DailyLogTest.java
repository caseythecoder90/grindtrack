package dev.grindtrack.tracking.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class DailyLogTest {

  private static final LocalDate DATE = LocalDate.of(2026, 7, 15);

  @Test
  void addHoursAccumulates() {
    DailyLog log = new DailyLog(DATE);
    log.addHours(new BigDecimal("0.4"));
    log.addHours(new BigDecimal("0.4"));
    assertThat(log.getHours()).isEqualByComparingTo("0.8");
  }

  @Test
  void addHoursClampsAtTwentyFour() {
    DailyLog log = new DailyLog(DATE);
    log.addHours(new BigDecimal("23.9"));
    log.addHours(new BigDecimal("0.5"));
    assertThat(log.getHours()).isEqualByComparingTo("24");
  }

  @Test
  void addHoursReachingExactlyTwentyFourIsNotClamped() {
    DailyLog log = new DailyLog(DATE);
    log.addHours(new BigDecimal("23.5"));
    log.addHours(new BigDecimal("0.5"));
    assertThat(log.getHours()).isEqualByComparingTo("24.0");
  }

  @Test
  void categoryListSplitsAndTrimsTheStoredString() {
    DailyLog log = new DailyLog(DATE);
    log.update(BigDecimal.ONE, List.of("java", "aws"), null, null, null, null, null);
    assertThat(log.categoryList()).containsExactly("java", "aws");
  }

  @Test
  void categoryListIsEmptyWhenNoCategoriesWereSet() {
    DailyLog log = new DailyLog(DATE);
    log.update(BigDecimal.ONE, null, null, null, null, null, null);
    assertThat(log.categoryList()).isEmpty();
  }

  @Test
  void updateNormalizesNullTextFieldsToEmptyStrings() {
    DailyLog log = new DailyLog(DATE);
    log.update(BigDecimal.ONE, null, null, null, null, null, null);
    assertThat(log.getFocus()).isEmpty();
    assertThat(log.getDid()).isEmpty();
    assertThat(log.getWins()).isEmpty();
    assertThat(log.getBlockers()).isEmpty();
    assertThat(log.getEnergy()).isNull();
  }
}
