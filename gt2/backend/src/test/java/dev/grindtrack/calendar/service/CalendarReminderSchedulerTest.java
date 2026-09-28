package dev.grindtrack.calendar.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.grindtrack.calendar.domain.CalendarEvent;
import dev.grindtrack.calendar.domain.EventKind;
import dev.grindtrack.push.service.PushService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Which blocks are announced, when, and what the lock screen says. */
class CalendarReminderSchedulerTest {

  private static final LocalDate DAY = LocalDate.of(2026, 9, 16);
  private static final LocalDateTime NOW = DAY.atTime(5, 20);

  private static CalendarEvent at(String title, EventKind kind, LocalTime start, LocalTime end) {
    return new CalendarEvent(title, kind, DAY, start, end);
  }

  @Test
  void onlyTimedBlocksStartingWithinTheWindowAndNotYetRemindedAreDue() {
    CalendarEvent soon =
        at("etcd lab", EventKind.STUDY_BLOCK, LocalTime.of(5, 30), LocalTime.of(7, 0));
    CalendarEvent edge = at("standup", EventKind.APPOINTMENT, LocalTime.of(5, 30), null);
    CalendarEvent later = at("dentist", EventKind.APPOINTMENT, LocalTime.of(9, 0), null);
    CalendarEvent started = at("gym", EventKind.PERSONAL, LocalTime.of(5, 20), null);
    CalendarEvent allDay = at("dog — flea and tick", EventKind.PERSONAL, null, null);
    CalendarEvent work = at("work", EventKind.WORK_BLOCK, LocalTime.of(5, 25), LocalTime.of(14, 0));
    CalendarEvent done = at("run", EventKind.PERSONAL, LocalTime.of(5, 25), null);
    done.markReminded();

    List<CalendarEvent> due =
        CalendarReminderScheduler.startingSoon(
            List.of(soon, edge, later, started, allDay, work, done), NOW, 10);

    assertThat(due).containsExactly(soon, edge);
  }

  @Test
  void aBlockWithItsOwnLeadIsRemindedOnThatLeadInsteadOfTheDefault() {
    CalendarEvent dentist = at("dentist", EventKind.APPOINTMENT, LocalTime.of(6, 15), null);
    dentist.setRemindMinutes(60);
    CalendarEvent quiet = at("gym", EventKind.PERSONAL, LocalTime.of(5, 25), null);
    quiet.setRemindMinutes(0);
    CalendarEvent work = at("work", EventKind.WORK_BLOCK, LocalTime.of(5, 25), LocalTime.of(14, 0));
    work.setRemindMinutes(5);
    CalendarEvent tooFar = at("call", EventKind.APPOINTMENT, LocalTime.of(6, 30), null);
    tooFar.setRemindMinutes(60);

    List<CalendarEvent> due =
        CalendarReminderScheduler.startingSoon(List.of(dentist, quiet, work, tooFar), NOW, 10);

    // The dentist asked for an hour and is 55 minutes out; the gym asked for nothing; the work
    // block
    // is reminded because it asked, which the kind alone would not do; the call is 70 minutes out.
    assertThat(due).containsExactly(dentist, work);
  }

  @Test
  void aDayAheadReachesIntoTomorrow() {
    CalendarEvent tomorrow =
        new CalendarEvent(
            "exam", EventKind.APPOINTMENT, DAY.plusDays(1), LocalTime.of(5, 0), LocalTime.of(7, 0));
    tomorrow.setRemindMinutes(1440);
    CalendarEvent tomorrowDefault =
        new CalendarEvent(
            "standup", EventKind.APPOINTMENT, DAY.plusDays(1), LocalTime.of(5, 25), null);

    assertThat(CalendarReminderScheduler.startingSoon(List.of(tomorrow, tomorrowDefault), NOW, 10))
        .containsExactly(tomorrow);
    assertThat(CalendarReminderScheduler.notification(tomorrow, NOW).body())
        .isEqualTo("tomorrow · 05:00–07:00 · appointment");

    CalendarEvent laterToday = at("dentist", EventKind.APPOINTMENT, LocalTime.of(9, 0), null);
    assertThat(CalendarReminderScheduler.notification(laterToday, NOW).body())
        .isEqualTo("today · 09:00 · appointment");
  }

  @Test
  void changingTheLeadForgetsThatItWasReminded() {
    CalendarEvent block = at("etcd lab", EventKind.STUDY_BLOCK, LocalTime.of(5, 30), null);
    block.markReminded();
    block.setRemindMinutes(60);
    assertThat(block.getRemindedAt()).isNull();
    assertThat(block.reminderLead(10)).isEqualTo(60);
    block.setRemindMinutes(null);
    assertThat(block.reminderLead(10)).isEqualTo(10);
    assertThat(at("work", EventKind.WORK_BLOCK, LocalTime.of(9, 0), null).reminderLead(10))
        .isZero();
  }

  @Test
  void movingABlockForgetsThatItWasReminded() {
    CalendarEvent block = at("etcd lab", EventKind.STUDY_BLOCK, LocalTime.of(5, 30), null);
    block.markReminded();
    assertThat(block.getRemindedAt()).isNotNull();
    block.setTimes(LocalTime.of(6, 0), null);
    assertThat(block.getRemindedAt()).isNull();
    block.markReminded();
    block.setEventDate(DAY.plusDays(1));
    assertThat(block.getRemindedAt()).isNull();
  }

  @Test
  void theNotificationNamesTheBlockAndSaysWhen() {
    CalendarEvent block =
        at("etcd lab", EventKind.STUDY_BLOCK, LocalTime.of(5, 30), LocalTime.of(7, 0));
    PushService.Notification n = CalendarReminderScheduler.notification(block, NOW);
    assertThat(n.title()).isEqualTo("etcd lab");
    assertThat(n.body()).isEqualTo("in 10 minutes · 05:30–07:00 · study block");
    assertThat(n.tab()).isEqualTo("cal");
    assertThat(n.tag()).startsWith("block-");
    assertThat(n.ttlSeconds()).isEqualTo(600);

    CalendarEvent open = at("call Mike", EventKind.APPOINTMENT, LocalTime.of(5, 21), null);
    assertThat(CalendarReminderScheduler.notification(open, NOW).body())
        .isEqualTo("in 1 minute · 05:21 · appointment");
  }
}
