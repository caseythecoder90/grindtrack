package dev.grindtrack.recovery.service;

import dev.grindtrack.push.service.PushService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Five to eight: the day's readings, to the phone, for reading together later in the morning. One
 * notification naming the three — the reflection, the passage, the chapter — and nothing when there
 * is nothing to read.
 */
@Component
public class RecoveryReadingScheduler {

  private static final Logger log = LoggerFactory.getLogger(RecoveryReadingScheduler.class);

  private final RecoveryService recovery;
  private final PushService push;

  public RecoveryReadingScheduler(RecoveryService recovery, PushService push) {
    this.recovery = recovery;
    this.push = push;
  }

  @Scheduled(cron = "${grindtrack.recovery.readings-cron}", zone = "${grindtrack.assistant.zone}")
  public void send() {
    try {
      PushService.Notification notification = notification(recovery.readingsToday());
      if (notification == null) {
        return;
      }
      PushService.Outcome outcome = push.send(notification);
      if (outcome.sent() + outcome.failed() + outcome.gone() > 0) {
        log.info("Pushed the readings: {}", outcome);
      }
    } catch (Exception e) {
      log.error("The readings push failed", e);
    }
  }

  /**
   * The line for the lock screen, or null when there is nothing to read. Package-private for the
   * test.
   */
  static PushService.Notification notification(List<String> pieces) {
    if (pieces.isEmpty()) {
      return null;
    }
    return PushService.Notification.readings(String.join(" · ", pieces));
  }
}
