package dev.grindtrack.calendar.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.grindtrack.calendar.domain.RecurringTask;
import dev.grindtrack.calendar.domain.TaskCategory;
import dev.grindtrack.push.service.PushService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * What the morning says about upkeep: the overdue count, a few names, nothing when nothing is due.
 */
class UpkeepReminderSchedulerTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 9, 16);

  private static UpkeepItem item(String title, int daysOverdue) {
    RecurringTask task =
        new RecurringTask(title, TaskCategory.HOME, 30, TODAY.minusDays(30 + daysOverdue));
    return UpkeepItem.of(task, TODAY);
  }

  @Test
  void nothingDueMeansNoReminder() {
    assertThat(UpkeepReminderScheduler.notification(List.of())).isNull();
    assertThat(UpkeepReminderScheduler.notification(List.of(item("filter", -5)))).isNull();
  }

  @Test
  void overdueCountsInTheTitleAndTheNamesFollowMostOverdueFirst() {
    PushService.Notification n =
        UpkeepReminderScheduler.notification(
            List.of(
                item("change the filter", 4),
                item("flea and tick", 1),
                item("oil", 0),
                item("gutters", 0)));
    assertThat(n.title()).isEqualTo("2 upkeep items are overdue");
    assertThat(n.body()).isEqualTo("change the filter · flea and tick · oil · +1 more");
    assertThat(n.tab()).isEqualTo("cal");
    assertThat(n.tag()).isEqualTo("upkeep");
  }

  @Test
  void dueTodayAloneSaysSo() {
    PushService.Notification n = UpkeepReminderScheduler.notification(List.of(item("oil", 0)));
    assertThat(n.title()).isEqualTo("1 upkeep item is due today");
    assertThat(n.body()).isEqualTo("oil");
  }
}
