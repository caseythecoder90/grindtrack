package dev.grindtrack.recovery.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.grindtrack.push.service.PushService;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecoveryReadingSchedulerTest {

  @Test
  void oneLineNamingTheReadingsOpeningTheRecoveryTab() {
    PushService.Notification n =
        RecoveryReadingScheduler.notification(
            List.of("I Am a Miracle", "Psalm 23", "Big Book: More About Alcoholism"));

    assertThat(n.title()).isEqualTo("today's readings");
    assertThat(n.body()).isEqualTo("I Am a Miracle · Psalm 23 · Big Book: More About Alcoholism");
    assertThat(n.tab()).isEqualTo("recovery");
    assertThat(n.tag()).isEqualTo("readings");
  }

  @Test
  void nothingToReadIsNoNotification() {
    assertThat(RecoveryReadingScheduler.notification(List.of())).isNull();
  }
}
