package dev.grindtrack.tracking.service;

import java.util.List;

/** Aggregated tracking stats computed by {@link StatsService}. */
public record Stats(
    double totalHours,
    long daysLogged,
    int streak,
    List<WeekHours> weeks,
    List<CategoryHours> categories) {

  public record WeekHours(String weekStart, double hours) {}

  public record CategoryHours(String category, double hours) {}
}
