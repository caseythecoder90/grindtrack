package dev.grindtrack.calendar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.grindtrack.calendar.domain.RecurringTask;
import dev.grindtrack.calendar.domain.RecurringTaskCompletion;
import dev.grindtrack.calendar.domain.RecurringTaskCompletionRepository;
import dev.grindtrack.calendar.domain.RecurringTaskRepository;
import dev.grindtrack.calendar.domain.TaskCategory;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UpkeepServiceTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 9, 8);

  @Mock private RecurringTaskRepository tasks;
  @Mock private RecurringTaskCompletionRepository completions;

  private UpkeepService service;

  @BeforeEach
  void setUp() {
    service = new UpkeepService(tasks, completions);
  }

  private static RecurringTask task(String title, int interval, LocalDate lastDone) {
    return new RecurringTask(title, TaskCategory.HOME, interval, lastDone);
  }

  @Test
  void dueSortsMostOverdueFirst() {
    when(tasks.findByActiveOrderByTitleAsc(true))
        .thenReturn(
            List.of(
                task("Dentist", 180, LocalDate.of(2026, 8, 1)), // due in 144 days
                task("HVAC filter", 90, LocalDate.of(2026, 5, 1)), // 40 days overdue
                task("Oil change", 180, LocalDate.of(2026, 3, 1)) // 8 days overdue
                ));

    assertThat(service.due(TODAY))
        .extracting(item -> item.task().getTitle())
        .containsExactly("HVAC filter", "Oil change", "Dentist");
  }

  @Test
  void markDoneLogsACompletionAndMovesTheClock() {
    RecurringTask filter = task("HVAC filter", 90, LocalDate.of(2026, 5, 1));
    when(tasks.findById(1L)).thenReturn(Optional.of(filter));
    when(completions.findByTaskIdOrderByDoneOnDesc(1L)).thenReturn(List.of());
    when(tasks.save(any(RecurringTask.class))).thenAnswer(inv -> inv.getArgument(0));

    RecurringTask saved = service.markDone(1L, TODAY).orElseThrow();

    verify(completions).save(any(RecurringTaskCompletion.class));
    assertThat(saved.getLastDoneOn()).isEqualTo(TODAY);
    assertThat(saved.nextDue(TODAY)).isEqualTo(LocalDate.of(2026, 12, 7));
  }

  @Test
  void aSecondTapOnTheSameDayIsNotASecondCompletion() {
    // The schema says so with a unique constraint; checking here is what turns a double
    // tap into the task coming back rather than a 500.
    RecurringTask filter = task("HVAC filter", 90, TODAY);
    when(tasks.findById(1L)).thenReturn(Optional.of(filter));
    when(completions.findByTaskIdOrderByDoneOnDesc(1L))
        .thenReturn(List.of(new RecurringTaskCompletion(1L, TODAY)));
    when(tasks.save(any(RecurringTask.class))).thenAnswer(inv -> inv.getArgument(0));

    assertThat(service.markDone(1L, TODAY)).isPresent();
    verify(completions, never()).save(any());
  }

  @Test
  void correctingTheLastDoneDateDoesNotLogACompletion() {
    // Fixing a typo is not the same act as doing the task, and a history that records
    // corrections as completions stops being a record of anything.
    RecurringTask filter = task("HVAC filter", 90, LocalDate.of(2026, 5, 1));
    when(tasks.findById(1L)).thenReturn(Optional.of(filter));
    when(tasks.save(any(RecurringTask.class))).thenAnswer(inv -> inv.getArgument(0));

    RecurringTask saved =
        service.update(1L, null, null, null, null, null, LocalDate.of(2026, 6, 1)).orElseThrow();

    assertThat(saved.getLastDoneOn()).isEqualTo(LocalDate.of(2026, 6, 1));
    verify(completions, never()).save(any());
  }

  @Test
  void markDoneOnAnUnknownIdIsEmptyRatherThanAThrow() {
    when(tasks.findById(99L)).thenReturn(Optional.empty());
    assertThat(service.markDone(99L, TODAY)).isEmpty();
    verify(completions, never()).save(any());
  }
}
