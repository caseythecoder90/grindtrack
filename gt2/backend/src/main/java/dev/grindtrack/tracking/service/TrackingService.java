package dev.grindtrack.tracking.service;

import dev.grindtrack.tracking.domain.DailyLog;
import dev.grindtrack.tracking.domain.DailyLogRepository;
import dev.grindtrack.tracking.domain.WeeklyReview;
import dev.grindtrack.tracking.domain.WeeklyReviewRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The personal study tracker: daily logs and weekly reviews.
 *
 * <p>Extracted from {@code TrackingController}, which held two repositories directly. Three rules
 * moved with it because they are true about a log rather than about a request: a day cannot hold
 * more than 24 hours, energy runs 1-5, and a week starts on Monday.
 *
 * <p>That last one is the reason this extraction was worth doing beyond consistency. {@link
 * #mondayOf} is a business rule — it is why a review saved against a Wednesday lands on the same
 * row as one saved against the Friday of that week — and it was sitting in a private static method
 * on a controller where nothing could test it directly.
 */
@Service
public class TrackingService {

  /** More than this in one day is a paste accident rather than a set of categories. */
  private static final int MAX_CATEGORIES = 50;

  private static final int MAX_CATEGORY_CHARS = 100;

  private final DailyLogRepository dailyLogs;
  private final WeeklyReviewRepository weeklyReviews;

  public TrackingService(DailyLogRepository dailyLogs, WeeklyReviewRepository weeklyReviews) {
    this.dailyLogs = dailyLogs;
    this.weeklyReviews = weeklyReviews;
  }

  // ------------------------------------------------------------------- days

  public List<DailyLog> daysBetween(LocalDate from, LocalDate to) {
    return dailyLogs.findByLogDateBetweenOrderByLogDate(from, to);
  }

  public Optional<DailyLog> day(LocalDate date) {
    return dailyLogs.findById(date);
  }

  /** Upsert: the date is the primary key, so one call covers create and update. */
  @Transactional
  public DailyLog saveDay(
      LocalDate date,
      BigDecimal hours,
      List<String> categories,
      String focus,
      String did,
      String wins,
      String blockers,
      Integer energy) {

    BigDecimal safeHours = hours == null ? BigDecimal.ZERO : hours;
    if (safeHours.signum() < 0 || safeHours.doubleValue() > 24) {
      throw new IllegalArgumentException("hours must be 0-24");
    }
    if (energy != null && (energy < 1 || energy > 5)) {
      throw new IllegalArgumentException("energy must be 1-5");
    }
    if (categories != null
        && (categories.size() > MAX_CATEGORIES
            || categories.stream().anyMatch(c -> c == null || c.length() > MAX_CATEGORY_CHARS))) {
      throw new IllegalArgumentException(
          "too many categories, or a category name over " + MAX_CATEGORY_CHARS + " chars");
    }

    DailyLog log = dailyLogs.findById(date).orElseGet(() -> new DailyLog(date));
    log.update(safeHours, categories, focus, did, wins, blockers, energy);
    return dailyLogs.save(log);
  }

  @Transactional
  public void deleteDay(LocalDate date) {
    dailyLogs.deleteById(date);
  }

  // ------------------------------------------------------------------ weeks

  /**
   * The Monday of whatever week a date falls in.
   *
   * <p>A weekly review is keyed by its Monday, so saving against any day of the week has to resolve
   * to the same row. Without this a review written on Friday would silently create a second row for
   * the week you already reviewed on Wednesday.
   */
  public static LocalDate mondayOf(LocalDate date) {
    return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
  }

  public Optional<WeeklyReview> week(LocalDate anyDayInWeek) {
    return weeklyReviews.findById(mondayOf(anyDayInWeek));
  }

  @Transactional
  public WeeklyReview saveWeek(
      LocalDate anyDayInWeek,
      String summary,
      String wins,
      String blockers,
      String adjustments,
      String nextFocus,
      Boolean onTrack) {

    LocalDate monday = mondayOf(anyDayInWeek);
    WeeklyReview review = weeklyReviews.findById(monday).orElseGet(() -> new WeeklyReview(monday));
    review.update(summary, wins, blockers, adjustments, nextFocus, onTrack);
    return weeklyReviews.save(review);
  }

  // ----------------------------------------------------------------- export

  /** Everything, for the JSON backup. Ordered so the file is stable between downloads. */
  public List<DailyLog> allDays() {
    return dailyLogs.findAll().stream()
        .sorted(java.util.Comparator.comparing(DailyLog::getLogDate))
        .toList();
  }

  public List<WeeklyReview> allWeeks() {
    return weeklyReviews.findAll().stream()
        .sorted(java.util.Comparator.comparing(WeeklyReview::getWeekStart))
        .toList();
  }
}
