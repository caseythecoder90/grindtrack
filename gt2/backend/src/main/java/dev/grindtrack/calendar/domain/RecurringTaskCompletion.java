package dev.grindtrack.calendar.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * One time a recurring task was done.
 *
 * <p>{@code RecurringTask.lastDoneOn} answers "when did I last"; this answers "have I actually been
 * keeping this up". The second question needs the series, and it cannot be recovered once a single
 * mutable field has been overwritten twelve times.
 */
@Entity
@Table(name = "recurring_task_completions")
public class RecurringTaskCompletion {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "task_id", nullable = false)
  private Long taskId;

  @Column(name = "done_on", nullable = false)
  private LocalDate doneOn;

  protected RecurringTaskCompletion() {}

  public RecurringTaskCompletion(Long taskId, LocalDate doneOn) {
    this.taskId = taskId;
    this.doneOn = doneOn;
  }

  public Long getId() {
    return id;
  }

  public Long getTaskId() {
    return taskId;
  }

  public LocalDate getDoneOn() {
    return doneOn;
  }
}
