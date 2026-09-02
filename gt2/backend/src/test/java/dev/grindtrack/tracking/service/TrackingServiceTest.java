package dev.grindtrack.tracking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.grindtrack.tracking.domain.DailyLog;
import dev.grindtrack.tracking.domain.DailyLogRepository;
import dev.grindtrack.tracking.domain.WeeklyReview;
import dev.grindtrack.tracking.domain.WeeklyReviewRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The study tracker's rules, now testable without MockMvc.
 *
 * <p>{@link TrackingService#mondayOf} is the one that most needed extracting: it decides that a
 * review saved on any day lands on that week's row, and it was a private static method on a
 * controller where it could only be exercised through a PUT.
 */
class TrackingServiceTest {

  private static final LocalDate DAY = LocalDate.of(2026, 8, 27);

  private DailyLogRepository dailyLogs;
  private WeeklyReviewRepository weeklyReviews;
  private TrackingService service;

  @BeforeEach
  void setUp() {
    dailyLogs = mock(DailyLogRepository.class);
    weeklyReviews = mock(WeeklyReviewRepository.class);
    service = new TrackingService(dailyLogs, weeklyReviews);

    when(dailyLogs.findById(any())).thenReturn(Optional.empty());
    when(dailyLogs.save(any(DailyLog.class))).thenAnswer(i -> i.getArgument(0));
    when(weeklyReviews.findById(any())).thenReturn(Optional.empty());
    when(weeklyReviews.save(any(WeeklyReview.class))).thenAnswer(i -> i.getArgument(0));
  }

  // ---------- a week starts on Monday, whatever day you wrote the review ----------

  @Test
  void anyDayOfTheWeekResolvesToThatWeeksMonday() {
    // 2026-08-27 is a Thursday.
    assertThat(TrackingService.mondayOf(DAY)).isEqualTo(LocalDate.of(2026, 8, 24));
  }

  @Test
  void aMondayIsAlreadyItsOwnWeekStart() {
    LocalDate monday = LocalDate.of(2026, 8, 24);
    assertThat(TrackingService.mondayOf(monday)).isEqualTo(monday);
  }

  @Test
  void aSundayBelongsToTheWeekThatIsEndingNotTheOneStarting() {
    // The classic off-by-one: ISO weeks run Monday to Sunday, so 30 August 2026 (a Sunday) is the
    // end of the week beginning the 24th, not the start of the next one.
    assertThat(TrackingService.mondayOf(LocalDate.of(2026, 8, 30)))
        .isEqualTo(LocalDate.of(2026, 8, 24));
  }

  @Test
  void twoReviewsInTheSameWeekLandOnOneRow() {
    WeeklyReview existing = new WeeklyReview(LocalDate.of(2026, 8, 24));
    when(weeklyReviews.findById(LocalDate.of(2026, 8, 24))).thenReturn(Optional.of(existing));

    WeeklyReview saved =
        service.saveWeek(DAY, "written thursday", null, null, null, null, Boolean.TRUE);

    assertThat(saved).isSameAs(existing);
    assertThat(saved.getWeekStart()).isEqualTo(LocalDate.of(2026, 8, 24));
  }

  // ---------- daily log invariants ----------

  @Test
  void aDayCannotHoldMoreHoursThanADayHas() {
    assertThatThrownBy(
            () -> service.saveDay(DAY, new BigDecimal("24.5"), null, null, null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("hours must be 0-24");
  }

  @Test
  void energyRunsOneToFive() {
    assertThatThrownBy(() -> service.saveDay(DAY, BigDecimal.ONE, null, null, null, null, null, 6))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("energy must be 1-5");
    assertThatThrownBy(() -> service.saveDay(DAY, BigDecimal.ONE, null, null, null, null, null, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("energy must be 1-5");
  }

  @Test
  void noEnergyIsFineBecauseItIsOptional() {
    assertThat(service.saveDay(DAY, BigDecimal.ONE, null, null, null, null, null, null))
        .isNotNull();
  }

  @Test
  void anImplausibleNumberOfCategoriesIsRejected() {
    assertThatThrownBy(
            () ->
                service.saveDay(
                    DAY,
                    BigDecimal.ONE,
                    Collections.nCopies(51, "x"),
                    null,
                    null,
                    null,
                    null,
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("too many categories");
  }

  @Test
  void savingADayThatAlreadyExistsUpdatesIt() {
    DailyLog existing = new DailyLog(DAY);
    when(dailyLogs.findById(DAY)).thenReturn(Optional.of(existing));

    assertThat(service.saveDay(DAY, new BigDecimal("3"), null, null, null, null, null, null))
        .isSameAs(existing);
    assertThat(existing.getHours()).isEqualByComparingTo("3");
  }

  @Test
  void savingWithoutHoursLeavesTheStoredHoursAlone() {
    // The lost update this guards: the form loads 2.0, a focus session pushes the stored value
    // to 2.8, then the form saves a note. Re-sending the stale 2.0 used to wipe the session's
    // contribution — and from a second device that is exactly what happened.
    DailyLog existing = new DailyLog(DAY);
    existing.setHours(new BigDecimal("2.8"));
    when(dailyLogs.findById(DAY)).thenReturn(Optional.of(existing));
    when(dailyLogs.save(any())).thenAnswer(i -> i.getArgument(0));

    DailyLog saved =
        service.saveDay(DAY, null, List.of("java"), "focus", "did", "wins", "blockers", 3);

    assertThat(saved.getHours()).isEqualByComparingTo("2.8");
    assertThat(saved.getDid()).isEqualTo("did");
  }

  @Test
  void anExplicitHoursValueStillWins() {
    // Typing in the box is a deliberate statement about the day's total, and must be honoured.
    DailyLog existing = new DailyLog(DAY);
    existing.setHours(new BigDecimal("2.8"));
    when(dailyLogs.findById(DAY)).thenReturn(Optional.of(existing));
    when(dailyLogs.save(any())).thenAnswer(i -> i.getArgument(0));

    DailyLog saved =
        service.saveDay(DAY, new BigDecimal("4.0"), null, null, null, null, null, null);

    assertThat(saved.getHours()).isEqualByComparingTo("4.0");
  }
}
