package dev.grindtrack.calendar.service;

import dev.grindtrack.calendar.domain.RecurringTask;
import dev.grindtrack.calendar.domain.RecurringTaskCompletion;
import dev.grindtrack.calendar.domain.RecurringTaskCompletionRepository;
import dev.grindtrack.calendar.domain.RecurringTaskRepository;
import dev.grindtrack.calendar.domain.TaskCategory;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Recurring upkeep: what is due, and recording that it was done. */
@Service
public class UpkeepService {

  private final RecurringTaskRepository tasks;
  private final RecurringTaskCompletionRepository completions;

  public UpkeepService(
      RecurringTaskRepository tasks, RecurringTaskCompletionRepository completions) {
    this.tasks = tasks;
    this.completions = completions;
  }

  /**
   * Active tasks, most overdue first.
   *
   * <p>Sorted here rather than in the query because "next due" is derived from two columns; asking
   * Postgres to order by an expression over a table this small buys nothing and puts the definition
   * of the ordering somewhere the entity's own rule cannot be seen.
   */
  public List<UpkeepItem> due(LocalDate today) {
    return tasks.findByActiveOrderByTitleAsc(true).stream()
        .map(task -> UpkeepItem.of(task, today))
        .sorted(
            Comparator.comparingLong(UpkeepItem::daysOverdue)
                .reversed()
                .thenComparing(item -> item.task().getTitle()))
        .toList();
  }

  public List<RecurringTask> archived() {
    return tasks.findByActiveOrderByTitleAsc(false);
  }

  public List<RecurringTaskCompletion> history(Long taskId) {
    return completions.findByTaskIdOrderByDoneOnDesc(taskId);
  }

  @Transactional
  public RecurringTask create(
      String title, TaskCategory category, int intervalDays, LocalDate lastDoneOn, String notes) {
    RecurringTask task = new RecurringTask(title, category, intervalDays, lastDoneOn);
    task.update(null, null, null, notes);
    return tasks.save(task);
  }

  /**
   * Records that a task was done, and rolls its clock forward.
   *
   * <p>The completion row and the task's own {@code lastDoneOn} are written together, which is what
   * the transaction is for: a logged completion whose task still reads as overdue would show the
   * item in the wrong group forever.
   *
   * <p>A second tap on the same day is not a second completion — the schema says so with a unique
   * constraint, and this checks first so the user gets their task back rather than a 500.
   */
  @Transactional
  public Optional<RecurringTask> markDone(Long id, LocalDate on) {
    return tasks
        .findById(id)
        .map(
            task -> {
              boolean alreadyToday =
                  completions.findByTaskIdOrderByDoneOnDesc(id).stream()
                      .anyMatch(done -> done.getDoneOn().equals(on));
              if (!alreadyToday) {
                completions.save(new RecurringTaskCompletion(id, on));
              }
              task.markDone(on);
              return tasks.save(task);
            });
  }

  @Transactional
  public Optional<RecurringTask> update(
      Long id,
      String title,
      TaskCategory category,
      Integer intervalDays,
      String notes,
      Boolean active,
      LocalDate lastDoneOn) {

    return tasks
        .findById(id)
        .map(
            task -> {
              task.update(title, category, intervalDays, notes);
              if (active != null) {
                task.setActive(active);
              }
              // A correction, not a completion: it does not log a row, because the point of
              // fixing a mistyped date is that the completion it recorded did not happen.
              if (lastDoneOn != null) {
                task.correctLastDone(lastDoneOn);
              }
              return tasks.save(task);
            });
  }

  @Transactional
  public void delete(Long id) {
    // The completions have ON DELETE CASCADE, but Hibernate does not know that and would
    // leave them behind on a soft path. Explicit is cheaper than finding out.
    completions.deleteByTaskId(id);
    tasks.deleteById(id);
  }
}
