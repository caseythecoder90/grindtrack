package dev.grindtrack.tracking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.grindtrack.tracking.domain.FocusKind;
import dev.grindtrack.tracking.domain.FocusSession;
import dev.grindtrack.tracking.domain.FocusSessionRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The lunch rollup, and mainly the streak.
 *
 * <p>The streak is the only part of this feature with a wrong answer that still looks plausible,
 * which is exactly the kind of thing that quietly stops being motivating.
 */
class ReadingServiceTest {

  /** A Wednesday, so the surrounding weekend is a short walk in either direction. */
  private static final LocalDate WED = LocalDate.of(2026, 9, 2);

  private FocusSessionRepository sessions;
  private ReadingService service;

  @BeforeEach
  void setUp() {
    sessions = mock(FocusSessionRepository.class);
    service = new ReadingService(sessions);
  }

  private static FocusSession session(LocalDate date, FocusKind kind, int minutes, String topic) {
    FocusSession s =
        new FocusSession(date, OffsetDateTime.parse(date + "T12:30:00Z"), minutes, true, kind);
    s.setSubject(null, topic);
    return s;
  }

  private static FocusSession onItem(LocalDate date, long planItemId, int minutes, String topic) {
    FocusSession s =
        new FocusSession(
            date, OffsetDateTime.parse(date + "T12:30:00Z"), minutes, true, FocusKind.READING);
    s.setSubject(planItemId, topic);
    return s;
  }

  /** The repository hands them back newest first; several rollups depend on that order. */
  private void given(FocusSession... all) {
    List<FocusSession> ordered = new ArrayList<>(List.of(all));
    ordered.sort(Comparator.comparing(FocusSession::getSessionDate).reversed());
    when(sessions.findLunchSessions()).thenReturn(ordered);
  }

  // ---------- the streak ----------

  @Test
  void aWeekendDoesNotBreakTheStreak() {
    // Fri 28th, Mon 31st, Tue 1st, Wed 2nd — four weekdays in a row with a two-day gap in it.
    given(
        session(LocalDate.of(2026, 8, 28), FocusKind.READING, 40, "DDIA"),
        session(LocalDate.of(2026, 8, 31), FocusKind.REVIEW, 40, "grindtrack"),
        session(LocalDate.of(2026, 9, 1), FocusKind.READING, 40, "DDIA"),
        session(WED, FocusKind.READING, 40, "DDIA"));

    assertThat(service.progress(WED).weekdayStreak()).isEqualTo(4);
  }

  @Test
  void aMissedWeekdayBreaksIt() {
    // Nothing on Tuesday the 1st.
    given(
        session(LocalDate.of(2026, 8, 31), FocusKind.READING, 40, "DDIA"),
        session(WED, FocusKind.READING, 40, "DDIA"));

    assertThat(service.progress(WED).weekdayStreak()).isEqualTo(1);
  }

  @Test
  void todayNotLoggedYetStillCountsYesterdaysStreak() {
    // At 9am the streak must not read as broken before you have had lunch.
    given(
        session(LocalDate.of(2026, 9, 1), FocusKind.READING, 40, "DDIA"),
        session(LocalDate.of(2026, 8, 31), FocusKind.READING, 40, "DDIA"));

    assertThat(service.progress(WED).weekdayStreak()).isEqualTo(2);
  }

  @Test
  void aStreakCheckedOnASaturdayCountsBackFromFriday() {
    LocalDate saturday = LocalDate.of(2026, 9, 5);
    given(
        session(LocalDate.of(2026, 9, 3), FocusKind.READING, 40, "DDIA"),
        session(LocalDate.of(2026, 9, 4), FocusKind.READING, 40, "DDIA"));

    assertThat(service.progress(saturday).weekdayStreak()).isEqualTo(2);
  }

  @Test
  void noSessionsIsAStreakOfZeroRatherThanAnError() {
    given();
    assertThat(service.progress(WED).weekdayStreak()).isZero();
  }

  // ---------- the week ----------

  @Test
  void thisWeekCountsFromMondayAndIgnoresLastWeek() {
    given(
        session(LocalDate.of(2026, 8, 28), FocusKind.READING, 60, "DDIA"), // previous Friday
        session(LocalDate.of(2026, 8, 31), FocusKind.READING, 30, "DDIA"), // Monday
        session(WED, FocusKind.REVIEW, 45, "grindtrack"));

    ReadingProgress progress = service.progress(WED);

    assertThat(progress.sessionsThisWeek()).isEqualTo(2);
    assertThat(progress.hoursThisWeek()).isEqualTo(1.3); // 75 minutes
    assertThat(progress.weeklyTarget()).isEqualTo(ReadingService.WEEKLY_TARGET);
    // Totals still span everything.
    assertThat(progress.totalSessions()).isEqualTo(3);
    assertThat(progress.totalHours()).isEqualTo(2.3); // 135 minutes
  }

  // ---------- what it went into ----------

  @Test
  void sessionsAgainstThePlanItemAreGroupedByIdNotByTitle() {
    // The workbook renamed the book between the two sessions; they are still one subject.
    given(
        onItem(LocalDate.of(2026, 8, 31), 7L, 40, "Designing Data-Intensive Applications"),
        onItem(WED, 7L, 50, "DDIA — Kleppmann"));

    List<ReadingProgress.Subject> subjects = service.progress(WED).subjects();

    assertThat(subjects).hasSize(1);
    assertThat(subjects.get(0).planItemId()).isEqualTo(7L);
    assertThat(subjects.get(0).sessions()).isEqualTo(2);
    assertThat(subjects.get(0).hours()).isEqualTo(1.5);
    // Labelled by the most recent session, so a rename shows through.
    assertThat(subjects.get(0).label()).isEqualTo("DDIA — Kleppmann");
    assertThat(subjects.get(0).lastOn()).isEqualTo(WED.toString());
  }

  @Test
  void codeReviewGroupsByTopicCaseInsensitively() {
    // No plan item to point at, so the typed repo name is the identity. "Grindtrack" and
    // "grindtrack" being two subjects would make the count useless within a fortnight.
    given(
        session(LocalDate.of(2026, 8, 31), FocusKind.REVIEW, 40, "grindtrack"),
        session(WED, FocusKind.REVIEW, 20, "Grindtrack"));

    assertThat(service.progress(WED).subjects()).hasSize(1);
    assertThat(service.progress(WED).subjects().get(0).sessions()).isEqualTo(2);
  }

  @Test
  void subjectsAreOrderedMostRecentlyTouchedFirst() {
    given(
        session(LocalDate.of(2026, 8, 31), FocusKind.READING, 40, "RFC 2119"),
        session(WED, FocusKind.REVIEW, 40, "grindtrack"));

    assertThat(service.progress(WED).subjects())
        .extracting(ReadingProgress.Subject::label)
        .containsExactly("grindtrack", "RFC 2119");
  }

  // ---------- takeaways ----------

  @Test
  void onlySessionsWithSomethingWrittenAppearInTheTakeawayLog() {
    FocusSession written = session(WED, FocusKind.READING, 40, "DDIA");
    written.setTakeaway("Partitioning is a choice about which queries get slow.");
    given(written, session(LocalDate.of(2026, 8, 31), FocusKind.READING, 40, "DDIA"));

    List<ReadingProgress.Takeaway> takeaways = service.progress(WED).recentTakeaways();

    assertThat(takeaways).hasSize(1);
    assertThat(takeaways.get(0).label()).isEqualTo("DDIA");
    assertThat(takeaways.get(0).text()).startsWith("Partitioning");
  }

  @Test
  void aSessionWithNoSubjectIsStillListedRatherThanDropped() {
    given(session(WED, FocusKind.READING, 40, ""));

    assertThat(service.progress(WED).subjects())
        .extracting(ReadingProgress.Subject::label)
        .containsExactly("unlabelled");
  }
}
