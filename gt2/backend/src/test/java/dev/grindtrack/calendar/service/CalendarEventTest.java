package dev.grindtrack.calendar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.grindtrack.calendar.domain.CalendarEvent;
import dev.grindtrack.calendar.domain.EventKind;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

/** The two rules an event enforces about itself. */
class CalendarEventTest {

  private static final LocalDate DAY = LocalDate.of(2026, 9, 12);

  private static CalendarEvent block() {
    return new CalendarEvent(
        "CKA course + labs", EventKind.STUDY_BLOCK, DAY, LocalTime.of(6, 0), LocalTime.of(8, 0));
  }

  @Test
  void anEventWithNoStartTimeIsAllDay() {
    CalendarEvent allDay =
        new CalendarEvent("Dog — flea & tick", EventKind.PERSONAL, DAY, null, null);
    assertThat(allDay.isAllDay()).isTrue();
    assertThat(block().isAllDay()).isFalse();
  }

  @Test
  void anEndTimeNeedsAStartAndMustComeAfterIt() {
    CalendarEvent e = block();
    assertThatThrownBy(() -> e.setTimes(null, LocalTime.of(8, 0)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("needs a start time");
    assertThatThrownBy(() -> e.setTimes(LocalTime.of(9, 0), LocalTime.of(8, 0)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("after the start");
    assertThatThrownBy(() -> e.setTimes(LocalTime.of(8, 0), LocalTime.of(8, 0)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void onlyAStudyBlockCarriesAPlanItem() {
    CalendarEvent studying = block();
    studying.setPlanItem(42L);
    assertThat(studying.getPlanItemId()).isEqualTo(42L);

    CalendarEvent dentist =
        new CalendarEvent("Dentist", EventKind.APPOINTMENT, DAY, LocalTime.of(9, 0), null);
    dentist.setPlanItem(42L);
    // Dropped, not rejected — the same rule a focus session follows for its reading
    // subject. Hours against a dentist would be invisible and wrong.
    assertThat(dentist.getPlanItemId()).isNull();
  }

  @Test
  void changingTheKindDropsALinkTheNewKindCannotHold() {
    CalendarEvent event = block();
    event.setPlanItem(42L);
    event.setKind(EventKind.APPOINTMENT);
    assertThat(event.getPlanItemId()).isNull();
  }
}
