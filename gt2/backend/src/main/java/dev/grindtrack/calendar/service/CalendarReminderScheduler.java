package dev.grindtrack.calendar.service;

import dev.grindtrack.calendar.domain.CalendarEvent;
import dev.grindtrack.calendar.domain.CalendarEventRepository;
import dev.grindtrack.config.AssistantProperties;
import dev.grindtrack.config.CalendarProperties;
import dev.grindtrack.push.service.PushService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * A block about to start, to the phone, once.
 *
 * <p>Every minute: today's and tomorrow's timed events whose start is within their own lead of now
 * and that have not been reminded are pushed and marked. The lead is the block's own {@code
 * remindMinutes} when set, else {@code reminderMinutes} from the configuration; a day ahead is the
 * most a block can ask for, which is why tomorrow is scanned too. The mark is a column, not a set
 * in memory, so a restart does not repeat a reminder; a block whose start passed while the app was
 * down is skipped rather than announced late. The day job's blocks are not reminded unless they ask
 * — {@link dev.grindtrack.calendar.domain.EventKind#remindsBeforeStart}.
 */
@Component
public class CalendarReminderScheduler {

  private static final Logger log = LoggerFactory.getLogger(CalendarReminderScheduler.class);
  private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

  private final CalendarEventRepository events;
  private final PushService push;
  private final CalendarProperties props;
  private final AssistantProperties zone;

  public CalendarReminderScheduler(
      CalendarEventRepository events,
      PushService push,
      CalendarProperties props,
      AssistantProperties zone) {
    this.events = events;
    this.push = push;
    this.props = props;
    this.zone = zone;
  }

  @Scheduled(cron = "${grindtrack.calendar.reminder-cron}", zone = "${grindtrack.assistant.zone}")
  @Transactional
  public void remind() {
    LocalDateTime now = LocalDateTime.now(ZoneId.of(zone.zone()));
    List<CalendarEvent> due =
        startingSoon(
            events.findInRange(now.toLocalDate(), now.toLocalDate().plusDays(1)),
            now,
            props.reminderMinutes());
    for (CalendarEvent event : due) {
      // Marked before the send: a push that fails is a reminder missed, not one repeated every
      // minute until the block starts.
      event.markReminded();
      events.save(event);
      try {
        PushService.Outcome outcome = push.send(notification(event, now));
        if (outcome.sent() + outcome.failed() + outcome.gone() > 0) {
          log.info("Pushed the reminder for calendar event {}: {}", event.getId(), outcome);
        }
      } catch (Exception e) {
        log.error("The reminder for calendar event {} failed", event.getId(), e);
      }
    }
  }

  /**
   * The timed events that start after {@code now} and within their own lead of it, not yet
   * reminded. {@code defaultMinutes} is the lead for a block that has not chosen one.
   * Package-private for the test.
   */
  static List<CalendarEvent> startingSoon(
      List<CalendarEvent> upcoming, LocalDateTime now, int defaultMinutes) {
    return upcoming.stream()
        .filter(e -> e.getStartTime() != null && e.getRemindedAt() == null)
        .filter(
            e -> {
              int lead = e.reminderLead(defaultMinutes);
              if (lead <= 0) {
                return false;
              }
              LocalDateTime start = startOf(e);
              return start.isAfter(now) && !start.isAfter(now.plusMinutes(lead));
            })
        .toList();
  }

  private static LocalDateTime startOf(CalendarEvent event) {
    return event.getEventDate().atTime(event.getStartTime());
  }

  /**
   * "etcd lab" / "in 10 minutes · 05:30–07:00 · study block". A lead past an hour and a half reads
   * as a time instead of a count, and one that crosses midnight says so. Package-private for the
   * test.
   */
  static PushService.Notification notification(CalendarEvent event, LocalDateTime now) {
    long minutes = java.time.Duration.between(now, startOf(event)).toMinutes();
    StringBuilder body = new StringBuilder();
    if (minutes <= 90) {
      body.append("in ").append(minutes).append(minutes == 1 ? " minute" : " minutes");
    } else if (event.getEventDate().equals(now.toLocalDate())) {
      body.append("today");
    } else {
      body.append("tomorrow");
    }
    body.append(" · ").append(HHMM.format(event.getStartTime()));
    if (event.getEndTime() != null) {
      body.append("–").append(HHMM.format(event.getEndTime()));
    }
    body.append(" · ").append(event.getKind().wireValue().replace('_', ' '));
    return PushService.Notification.blockStarting(
        event.getId(), event.getTitle(), body.toString(), minutes);
  }
}
