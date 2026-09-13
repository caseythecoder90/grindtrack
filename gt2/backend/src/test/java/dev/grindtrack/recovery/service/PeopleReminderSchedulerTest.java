package dev.grindtrack.recovery.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.grindtrack.push.service.PushService;
import java.util.List;
import org.junit.jupiter.api.Test;

class PeopleReminderSchedulerTest {

  private static RecoveryService.PersonView person(String name, String state, long over) {
    return new RecoveryService.PersonView(
        1L, name, "friend", 7, null, null, null, null, over, state, 0);
  }

  @Test
  void theAskComesFirstThenWhoIsOverAndTheRestIsACount() {
    PushService.Notification n =
        PeopleReminderScheduler.notification(
            List.of(
                person("Dan", "ask", 0),
                person("Mike", "overdue", 3),
                person("Jake", "overdue", 1),
                person("Sam", "overdue", 1)));

    assertThat(n.title()).isEqualTo("4 calls to make");
    assertThat(n.body()).isEqualTo("ask Dan · Mike (3 days over) · Jake (1 day over) · +1 more");
    assertThat(n.tab()).isEqualTo("recovery");
    assertThat(n.tag()).isEqualTo("people");
  }

  @Test
  void oneIsSaidInTheSingularAndNoneIsNothing() {
    assertThat(PeopleReminderScheduler.notification(List.of(person("Dan", "ask", 0))).title())
        .isEqualTo("someone to ask");
    assertThat(PeopleReminderScheduler.notification(List.of(person("Mike", "overdue", 2))).title())
        .isEqualTo("a call to make");
    assertThat(PeopleReminderScheduler.notification(List.of())).isNull();
  }
}
