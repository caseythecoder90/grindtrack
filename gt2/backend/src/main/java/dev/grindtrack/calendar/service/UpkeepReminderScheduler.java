package dev.grindtrack.calendar.service;

import dev.grindtrack.config.AssistantProperties;
import dev.grindtrack.push.service.PushService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The upkeep that is overdue or due today, to the phone, once a morning — for as long as anything
 * is. Same shape as the todo reminder: the most overdue thing named first, a few names, one tag so
 * the device shows one.
 */
@Component
public class UpkeepReminderScheduler {

  private static final Logger log = LoggerFactory.getLogger(UpkeepReminderScheduler.class);
  private static final int NAMED = 3;

  private final UpkeepService upkeep;
  private final PushService push;
  private final AssistantProperties zone;

  public UpkeepReminderScheduler(UpkeepService upkeep, PushService push, AssistantProperties zone) {
    this.upkeep = upkeep;
    this.push = push;
    this.zone = zone;
  }

  @Scheduled(cron = "${grindtrack.calendar.upkeep-cron}", zone = "${grindtrack.assistant.zone}")
  public void remind() {
    LocalDate today = LocalDate.now(ZoneId.of(zone.zone()));
    PushService.Notification notification = notification(upkeep.due(today));
    if (notification == null) {
      return;
    }
    try {
      PushService.Outcome outcome = push.send(notification);
      if (outcome.sent() + outcome.failed() + outcome.gone() > 0) {
        log.info("Pushed the upkeep reminder: {}", outcome);
      }
    } catch (Exception e) {
      log.error("The upkeep reminder failed", e);
    }
  }

  /**
   * The reminder for what is overdue or due today, or null when nothing is. The list arrives most
   * overdue first. Package-private for the test.
   */
  static PushService.Notification notification(List<UpkeepItem> items) {
    List<UpkeepItem> due = items.stream().filter(i -> i.daysOverdue() >= 0).toList();
    if (due.isEmpty()) {
      return null;
    }
    long overdue = due.stream().filter(i -> i.daysOverdue() > 0).count();
    String title;
    if (overdue > 0) {
      title = overdue == 1 ? "1 upkeep item is overdue" : overdue + " upkeep items are overdue";
    } else {
      title =
          due.size() == 1
              ? "1 upkeep item is due today"
              : due.size() + " upkeep items are due today";
    }
    String names =
        due.stream().limit(NAMED).map(i -> i.task().getTitle()).collect(Collectors.joining(" · "));
    if (due.size() > NAMED) {
      names += " · +" + (due.size() - NAMED) + " more";
    }
    return PushService.Notification.upkeepDue(title, names);
  }
}
