package dev.grindtrack.todo.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.grindtrack.push.service.PushService;
import dev.grindtrack.todo.domain.Todo;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** What the lock screen says: the most urgent thing first, a few names, and nothing when done. */
class TodoReminderSchedulerTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 9, 13);

  private static Todo open(String title, LocalDate due, int order) {
    return new Todo(title, "personal", due, order);
  }

  @Test
  void nothingOpenMeansNoReminder() {
    Todo done = open("paid", null, 0);
    done.setDone(true);
    assertThat(TodoReminderScheduler.notification(List.of(done), TODAY)).isNull();
    assertThat(TodoReminderScheduler.notification(List.of(), TODAY)).isNull();
  }

  @Test
  void overdueLeadsThenDueTodayThenTheRestAndTheTitleCountsTheMostUrgent() {
    List<Todo> todos =
        List.of(
            open("renew the domain", null, 0),
            open("call the dentist", TODAY, 1),
            open("pay the water bill", TODAY.minusDays(2), 2),
            open("book the car service", TODAY.minusDays(1), 3),
            open("read chapter 11", null, 4));

    PushService.Notification n = TodoReminderScheduler.notification(todos, TODAY);

    assertThat(n.title()).isEqualTo("2 todos are overdue");
    assertThat(n.body())
        .isEqualTo("pay the water bill · book the car service · call the dentist · +2 more");
    assertThat(n.tab()).isEqualTo("todos");
    assertThat(n.tag()).isEqualTo("todos");
  }

  @Test
  void withNothingDatedTheTitleJustCountsWhatIsWaiting() {
    PushService.Notification one =
        TodoReminderScheduler.notification(List.of(open("renew the domain", null, 0)), TODAY);
    assertThat(one.title()).isEqualTo("1 todo is waiting");
    assertThat(one.body()).isEqualTo("renew the domain");

    PushService.Notification today =
        TodoReminderScheduler.notification(List.of(open("call the dentist", TODAY, 0)), TODAY);
    assertThat(today.title()).isEqualTo("1 todo is due today");
  }
}
