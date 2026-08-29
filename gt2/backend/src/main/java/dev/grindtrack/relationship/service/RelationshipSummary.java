package dev.grindtrack.relationship.service;

import dev.grindtrack.relationship.domain.Idea;
import dev.grindtrack.relationship.domain.Moment;
import java.util.List;

/**
 * Everything the relationship tab shows in one read, mirroring {@code tracking/service/Stats}.
 *
 * <p>These five shapes were nested inside {@link RelationshipService}, which put nine record
 * declarations between the class comment and its first method. They are the service's own results
 * rather than wire shapes, so they stay in this package — but as a file of their own, so that
 * reading the service means reading what it computes rather than scrolling past what it returns.
 *
 * <p>{@code readyIdeas} and {@code lately} are entities, not view records. Mapping an entity to the
 * shape a browser receives is the controller's job, and having the service do it too is how the
 * same six lines came to exist in both layers.
 *
 * @param readyIdeas least-effort-first, because on an ordinary evening the deciding factor is how
 *     much something takes rather than how good the idea is
 */
public record RelationshipSummary(
    List<Recency> recency,
    Closeness closeness,
    List<Upcoming> upcoming,
    List<Idea> readyIdeas,
    List<Moment> lately) {

  /**
   * @param daysSince null when nothing of this kind has ever been logged, which is a blank rather
   *     than a zero — "never" and "today" must not look alike
   */
  public record Recency(String kind, String lastOn, Long daysSince, String note) {}

  /**
   * @param tone CALM, NEUTRAL or SUGGEST. Never a warning, and never a failure — the frontend has
   *     no red state for this and must not acquire one
   */
  public record Perspective(String headline, String detail, String tone) {}

  /**
   * What the intimacy card shows: the literal recent dates, a count against your own baseline, and
   * a sentence.
   *
   * @param recentDates the last few, as dates rather than as a rate. This is the thing that
   *     actually settles the question, so it comes first
   * @param typicalPerMonth your own trailing average, the only baseline used anywhere
   */
  public record Closeness(
      List<String> recentDates,
      Long daysSince,
      long lastThirtyDays,
      Integer typicalPerMonth,
      Perspective perspective) {}

  /** An occasion with its next date resolved, and how many ideas are already waiting for it. */
  public record Upcoming(
      Long id, String label, String on, long daysAway, Integer years, int ideaCount, String note) {}
}
