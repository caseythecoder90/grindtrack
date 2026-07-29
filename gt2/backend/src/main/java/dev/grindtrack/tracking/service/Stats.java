package dev.grindtrack.tracking.service;

import java.util.List;

/**
 * Aggregated tracking stats computed by {@link StatsService}.
 *
 * <p>All three scopes are computed on every request so the UI can switch between them without a
 * refetch — the whole dataset is a few hundred rows a year, so the extra folds cost nothing next to
 * a round trip.
 */
public record Stats(ScopeStats study, ScopeStats work, ScopeStats all) {

  /**
   * One scope's aggregations. Every scope carries every field; which of them are worth showing is a
   * UI decision. In particular {@code streak} is close to meaningless for work — weekends off reset
   * it every Saturday — so that view shows {@code daysThisMonth} instead.
   */
  public record ScopeStats(
      double totalHours,
      long daysLogged,
      int streak,
      long daysThisMonth,
      List<WeekHours> weeks,
      List<CategoryHours> categories,
      List<DayHours> days) {}

  public record WeekHours(String weekStart, double hours) {}

  public record CategoryHours(String category, double hours) {}

  /** One heatmap cell: an ISO date and the hours logged on it. */
  public record DayHours(String date, double hours) {}
}
