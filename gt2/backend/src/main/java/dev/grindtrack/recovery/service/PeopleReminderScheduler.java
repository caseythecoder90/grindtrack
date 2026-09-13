package dev.grindtrack.recovery.service;

import dev.grindtrack.push.service.PushService;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Six in the evening: who is due a call. One notification naming them — the sponsor still to be
 * asked first, then whoever is furthest past their cadence — and nothing when nobody is.
 */
@Component
public class PeopleReminderScheduler {

  private static final Logger log = LoggerFactory.getLogger(PeopleReminderScheduler.class);

  /** Enough to read on a lock screen; the tab has the rest. */
  private static final int NAMED = 3;

  private final RecoveryService recovery;
  private final PushService push;

  public PeopleReminderScheduler(RecoveryService recovery, PushService push) {
    this.recovery = recovery;
    this.push = push;
  }

  @Scheduled(cron = "${grindtrack.recovery.people-cron}", zone = "${grindtrack.assistant.zone}")
  public void remind() {
    try {
      PushService.Notification notification = notification(recovery.peopleToCall());
      if (notification == null) {
        return;
      }
      PushService.Outcome outcome = push.send(notification);
      if (outcome.sent() + outcome.failed() + outcome.gone() > 0) {
        log.info("Pushed the people reminder: {}", outcome);
      }
    } catch (Exception e) {
      log.error("The people reminder failed", e);
    }
  }

  /** The line for the lock screen, or null when nobody is due. Package-private for the test. */
  static PushService.Notification notification(List<RecoveryService.PersonView> due) {
    if (due.isEmpty()) {
      return null;
    }
    List<String> names = new ArrayList<>();
    for (RecoveryService.PersonView v : due.subList(0, Math.min(NAMED, due.size()))) {
      if (v.state().equals("ask")) {
        names.add("ask " + v.name());
      } else {
        names.add(
            v.name()
                + " ("
                + v.overdueDays()
                + (v.overdueDays() == 1 ? " day" : " days")
                + " over)");
      }
    }
    if (due.size() > NAMED) {
      names.add("+" + (due.size() - NAMED) + " more");
    }
    String title =
        due.size() == 1
            ? (due.get(0).state().equals("ask") ? "someone to ask" : "a call to make")
            : due.size() + " calls to make";
    return PushService.Notification.peopleToCall(title, String.join(" · ", names));
  }
}
