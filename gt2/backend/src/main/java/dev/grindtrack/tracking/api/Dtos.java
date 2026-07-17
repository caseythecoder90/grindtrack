package dev.grindtrack.tracking.api;

import dev.grindtrack.tracking.domain.DailyLog;
import dev.grindtrack.tracking.domain.FocusSession;
import dev.grindtrack.tracking.domain.WeeklyReview;
import java.math.BigDecimal;
import java.util.List;

/** Request/response shapes for the tracking API. */
public final class Dtos {

  private Dtos() {}

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

  public record FocusSessionResponse(
      Long id, String startedAt, int durationMinutes, boolean completed) {

    static FocusSessionResponse from(FocusSession session) {
      return new FocusSessionResponse(
          session.getId(),
          session.getStartedAt().toString(),
          session.getDurationMinutes(),
          session.isCompleted());
    }
  }

  public record PublicStats(int streak, double totalHours, long daysLogged, List<PublicDay> days) {}

  public record PublicDay(String date, double hours) {}
}
