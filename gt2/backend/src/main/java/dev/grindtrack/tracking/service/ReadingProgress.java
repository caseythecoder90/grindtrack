package dev.grindtrack.tracking.service;

import java.util.List;

/**
 * The lunch habit, as the four things that keep it running.
 *
 * <p>A file of its own beside {@link ReadingService}, like {@code Stats} beside {@code
 * StatsService}. Nothing here is a projection of an entity — every field is worked out.
 *
 * @param weekdayStreak consecutive weekdays with a lunch session
 * @param sessionsThisWeek lunch sessions since Monday, against {@code weeklyTarget}
 * @param subjects what the hours went into, most-recent-first
 * @param recentTakeaways the written notes, newest first — the part worth rereading
 */
public record ReadingProgress(
    int weekdayStreak,
    int sessionsThisWeek,
    int weeklyTarget,
    double hoursThisWeek,
    int totalSessions,
    double totalHours,
    List<Subject> subjects,
    List<Takeaway> recentTakeaways) {

  /**
   * One thing being worked through, whether a plan item or a repo.
   *
   * @param planItemId null for anything with no row in the plan — a code review, a postmortem
   * @param label the item's title, or the typed topic
   */
  public record Subject(
      Long planItemId, String label, String kind, int sessions, double hours, String lastOn) {}

  /** One session's written note. */
  public record Takeaway(Long sessionId, String on, String label, String kind, String text) {}
}
