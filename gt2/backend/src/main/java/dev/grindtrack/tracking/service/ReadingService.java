package dev.grindtrack.tracking.service;

import dev.grindtrack.tracking.domain.FocusSession;
import dev.grindtrack.tracking.domain.FocusSessionRepository;
import dev.grindtrack.tracking.service.ReadingProgress.Subject;
import dev.grindtrack.tracking.service.ReadingProgress.Takeaway;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * What the lunch habit adds up to.
 *
 * <p>Read-only, and separate from {@link FocusService} for the reason {@code StatsService} is
 * separate from {@code TrackingService}: one writes sessions, one aggregates them, and they are
 * only ever wanted one at a time.
 */
@Service
public class ReadingService {

  /**
   * Lunches a week worth aiming at.
   *
   * <p>Four, not five. A five-of-five target is broken by one meeting that runs long, and a target
   * you break in the first week is a target you stop looking at; four leaves room for the week to
   * happen and still means most days.
   */
  static final int WEEKLY_TARGET = 4;

  /** Enough to reread before an interview without becoming a wall of text. */
  private static final int RECENT_TAKEAWAYS = 12;

  private static final int MINUTES_PER_HOUR = 60;

  private final FocusSessionRepository sessions;

  public ReadingService(FocusSessionRepository sessions) {
    this.sessions = sessions;
  }

  /**
   * @param today passed in rather than read from a clock, so the streak's edge cases are testable
   *     without one — see the conventions doc on why this repo does not inject {@code Clock}
   */
  public ReadingProgress progress(LocalDate today) {
    List<FocusSession> lunches = sessions.findLunchSessions();
    LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

    int sessionsThisWeek = 0;
    int minutesThisWeek = 0;
    int totalMinutes = 0;
    for (FocusSession s : lunches) {
      totalMinutes += s.getDurationMinutes();
      if (!s.getSessionDate().isBefore(monday) && !s.getSessionDate().isAfter(today)) {
        sessionsThisWeek++;
        minutesThisWeek += s.getDurationMinutes();
      }
    }

    return new ReadingProgress(
        weekdayStreak(lunches, today),
        sessionsThisWeek,
        WEEKLY_TARGET,
        hours(minutesThisWeek),
        lunches.size(),
        hours(totalMinutes),
        subjects(lunches),
        takeaways(lunches));
  }

  /**
   * Consecutive <strong>weekdays</strong> with a lunch session, counting back from today.
   *
   * <p>Weekends are skipped rather than counted as misses. Lunch is a working-day habit; a streak
   * that resets every Saturday measures whether it is Saturday, which is the same mistake {@code
   * StatsService} avoids by showing the work scope days-this-month instead of a streak.
   *
   * <p>Counting starts at yesterday when today has nothing yet, so the number does not read as
   * broken at 9am before you have had lunch.
   */
  static int weekdayStreak(List<FocusSession> lunches, LocalDate today) {
    Set<LocalDate> days = new HashSet<>();
    for (FocusSession s : lunches) {
      days.add(s.getSessionDate());
    }

    LocalDate cursor = previousWeekdayOrSame(today);
    if (!days.contains(cursor)) {
      cursor = previousWeekday(cursor);
    }
    int streak = 0;
    while (days.contains(cursor)) {
      streak++;
      cursor = previousWeekday(cursor);
    }
    return streak;
  }

  /** Groups sessions by what they went into, most recently touched first. */
  private static List<Subject> subjects(List<FocusSession> lunches) {
    // Insertion-ordered: findLunchSessions returns newest first, so the first time a subject is
    // seen is its most recent session, and the map preserves that order for free.
    Map<String, List<FocusSession>> grouped = new LinkedHashMap<>();
    for (FocusSession s : lunches) {
      grouped.computeIfAbsent(s.subjectKey(), k -> new ArrayList<>()).add(s);
    }

    List<Subject> out = new ArrayList<>(grouped.size());
    for (List<FocusSession> group : grouped.values()) {
      FocusSession newest = group.get(0);
      int minutes = group.stream().mapToInt(FocusSession::getDurationMinutes).sum();
      LocalDate lastOn =
          group.stream()
              .map(FocusSession::getSessionDate)
              .max(Comparator.naturalOrder())
              .orElseThrow();
      out.add(
          new Subject(
              newest.getPlanItemId(),
              label(newest),
              newest.getKind().wireValue(),
              group.size(),
              hours(minutes),
              lastOn.toString()));
    }
    return out;
  }

  private static List<Takeaway> takeaways(List<FocusSession> lunches) {
    return lunches.stream()
        .filter(s -> !s.getTakeaway().isBlank())
        .limit(RECENT_TAKEAWAYS)
        .map(
            s ->
                new Takeaway(
                    s.getId(),
                    s.getSessionDate().toString(),
                    label(s),
                    s.getKind().wireValue(),
                    s.getTakeaway()))
        .toList();
  }

  /** Never blank: a session with no subject at all still has to be listed as something. */
  private static String label(FocusSession session) {
    return session.getTopic().isBlank() ? "unlabelled" : session.getTopic();
  }

  private static LocalDate previousWeekdayOrSame(LocalDate date) {
    return isWeekend(date) ? previousWeekday(date) : date;
  }

  private static LocalDate previousWeekday(LocalDate date) {
    LocalDate cursor = date.minusDays(1);
    while (isWeekend(cursor)) {
      cursor = cursor.minusDays(1);
    }
    return cursor;
  }

  private static boolean isWeekend(LocalDate date) {
    return date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
  }

  private static double hours(int minutes) {
    return Math.round(minutes / (double) MINUTES_PER_HOUR * 10.0) / 10.0;
  }
}
