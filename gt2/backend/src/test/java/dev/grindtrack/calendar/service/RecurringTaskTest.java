package dev.grindtrack.calendar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.grindtrack.calendar.domain.RecurringTask;
import dev.grindtrack.calendar.domain.TaskCategory;
import dev.grindtrack.calendar.service.UpkeepItem.State;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** The derived-due-date rules, which are the whole point of the table. */
class RecurringTaskTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 9, 8);

  private static RecurringTask task(int intervalDays, LocalDate lastDone) {
    return new RecurringTask("Dog — flea & tick", TaskCategory.PET, intervalDays, lastDone);
  }

  @Test
  void nextDueIsLastDonePlusTheInterval() {
    assertThat(task(30, LocalDate.of(2026, 8, 13)).nextDue(TODAY))
        .isEqualTo(LocalDate.of(2026, 9, 12));
  }

  @Test
  void aTaskNeverDoneIsDueToday() {
    // Not the epoch: it belongs at the top of the list, but it must not claim to be
    // twenty thousand days overdue.
    RecurringTask fresh = task(30, null);
    assertThat(fresh.nextDue(TODAY)).isEqualTo(TODAY);
    assertThat(fresh.daysOverdue(TODAY)).isZero();
  }

  @Test
  void daysOverdueIsNegativeBeforeTheDueDateAndPositiveAfter() {
    assertThat(task(30, LocalDate.of(2026, 8, 13)).daysOverdue(TODAY)).isEqualTo(-4);
    assertThat(task(30, LocalDate.of(2026, 8, 1)).daysOverdue(TODAY)).isEqualTo(8);
    assertThat(task(30, LocalDate.of(2026, 8, 9)).daysOverdue(TODAY)).isZero();
  }

  @Test
  void markDoneCountsFromWhenItWasDoneNotFromWhenItWasDue() {
    // A filter changed three weeks late resets the clock from the change. Counting from
    // the due date instead would compound the drift every cycle.
    RecurringTask late = task(90, LocalDate.of(2026, 3, 1));
    late.markDone(LocalDate.of(2026, 6, 20));
    assertThat(late.nextDue(TODAY)).isEqualTo(LocalDate.of(2026, 9, 18));
  }

  @Test
  void markDoneRejectsADateBeforeTheLastCompletion() {
    RecurringTask t = task(30, LocalDate.of(2026, 8, 13));
    assertThatThrownBy(() -> t.markDone(LocalDate.of(2026, 7, 1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("earlier than the last time");
  }

  @Test
  void stateBucketsAtTheBoundaries() {
    assertThat(UpkeepItem.of(task(30, LocalDate.of(2026, 8, 9)), TODAY).state())
        .isEqualTo(State.OVERDUE); // due today counts as overdue — it needs doing
    assertThat(UpkeepItem.of(task(30, LocalDate.of(2026, 8, 16)), TODAY).state())
        .isEqualTo(State.DUE_SOON); // exactly seven days out
    assertThat(UpkeepItem.of(task(30, LocalDate.of(2026, 8, 17)), TODAY).state())
        .isEqualTo(State.LATER); // eight
  }
}
