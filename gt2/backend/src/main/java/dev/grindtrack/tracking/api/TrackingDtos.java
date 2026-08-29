package dev.grindtrack.tracking.api;

import dev.grindtrack.tracking.domain.DailyLog;
import dev.grindtrack.tracking.domain.FocusSession;
import dev.grindtrack.tracking.domain.WeeklyReview;
import java.math.BigDecimal;
import java.util.List;

/**
 * Request/response shapes for the tracking API — daily logs, weekly reviews, focus sessions and the
 * public heatmap.
 *
 * <p>Named for its feature like every other {@code <Feature>Dtos} in the codebase. It was {@code
 * Dtos}, which reads fine in this package and not at all in an import list next to {@code
 * FinanceDtos} and {@code WorkDtos}.
 */
public final class TrackingDtos {

  private TrackingDtos() {}

  public record DayRequest(
      BigDecimal hours,
      List<String> categories,
      String focus,
      String did,
      String wins,
      String blockers,
      Integer energy) {}

  public record DayResponse(
      String logDate,
      BigDecimal hours,
      List<String> categories,
      String focus,
      String did,
      String wins,
      String blockers,
      Integer energy) {

    static DayResponse from(DailyLog log) {
      return new DayResponse(
          log.getLogDate().toString(),
          log.getHours(),
          log.categoryList(),
          log.getFocus(),
          log.getDid(),
          log.getWins(),
          log.getBlockers(),
          log.getEnergy());
    }
  }

  public record WeekRequest(
      String summary,
      String wins,
      String blockers,
      String adjustments,
      String nextFocus,
      Boolean onTrack) {}

  public record WeekResponse(
      String weekStart,
      String summary,
      String wins,
      String blockers,
      String adjustments,
      String nextFocus,
      Boolean onTrack) {

    static WeekResponse from(WeeklyReview review) {
      return new WeekResponse(
          review.getWeekStart().toString(),
          review.getSummary(),
          review.getWins(),
          review.getBlockers(),
          review.getAdjustments(),
          review.getNextFocus(),
          review.getOnTrack());
    }
  }

  /**
   * @param completed false when the timer was stopped early; the partial minutes are still logged
   * @param kind study or work; absent means study
   */
  public record FocusSessionRequest(
      String date, String startedAt, Integer durationMinutes, Boolean completed, String kind) {}

  public record FocusSessionResponse(
      Long id, String startedAt, int durationMinutes, boolean completed, String kind) {

    public static FocusSessionResponse from(FocusSession session) {
      return new FocusSessionResponse(
          session.getId(),
          session.getStartedAt().toString(),
          session.getDurationMinutes(),
          session.isCompleted(),
          session.getKind().wireValue());
    }
  }

  /** The backup file's shape. A record rather than a map so the download's schema is declared. */
  public record ExportResponse(List<DayResponse> dailyLogs, List<WeekResponse> weeklyReviews) {}

  public record PublicStats(int streak, double totalHours, long daysLogged, List<PublicDay> days) {}

  public record PublicDay(String date, double hours) {}
}
