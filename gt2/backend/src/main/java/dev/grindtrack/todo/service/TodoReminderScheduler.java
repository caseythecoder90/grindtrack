package dev.grindtrack.todo.service;

import dev.grindtrack.config.AssistantProperties;
import dev.grindtrack.push.service.PushService;
import dev.grindtrack.todo.domain.Todo;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nags, on purpose. The todo list is a page nobody opens, so what is on it comes to the phone
 * instead, at the times configured, for as long as anything stays open.
 *
 * <p>Overdue first, then due today, then the rest, so the sentence on the lock screen names the
 * thing that matters most. Off is quiet: no open todos means no push, and no VAPID pair means
 * {@link PushService#send} answers zeros.
 */
@Component
public class TodoReminderScheduler {

  private static final Logger log = LoggerFactory.getLogger(TodoReminderScheduler.class);

  /** Enough to read on a lock screen; the tab has the rest. */
  private static final int NAMED = 3;

  private final TodoService todos;
  private final PushService push;
  private final AssistantProperties zone;

  public TodoReminderScheduler(TodoService todos, PushService push, AssistantProperties zone) {
    this.todos = todos;
    this.push = push;
    this.zone = zone;
  }

  @Scheduled(cron = "${grindtrack.todos.reminder-cron}", zone = "${grindtrack.assistant.zone}")
  public void remind() {
    LocalDate today = LocalDate.now(ZoneId.of(zone.zone()));
    PushService.Notification notification = notification(todos.list(null), today);
    if (notification == null) {
      return;
    }
    try {
      PushService.Outcome outcome = push.send(notification);
      if (outcome.sent() + outcome.failed() + outcome.gone() > 0) {
        log.info("Pushed the todo reminder: {}", outcome);
      }
    } catch (Exception e) {
      log.error("The todo reminder failed", e);
    }
  }

  /** The reminder for what is open, or null when nothing is. Package-private for the test. */
  static PushService.Notification notification(List<Todo> all, LocalDate today) {
    List<Todo> open = all.stream().filter(t -> !t.isDone()).toList();
    if (open.isEmpty()) {
      return null;
    }
    long overdue =
        open.stream().filter(t -> t.getDueDate() != null && t.getDueDate().isBefore(today)).count();
    long dueToday = open.stream().filter(t -> today.equals(t.getDueDate())).count();
    List<Todo> ordered =
        open.stream()
            .sorted(
                Comparator.comparing(
                        (Todo t) ->
                            t.getDueDate() == null
                                ? 2
                                : t.getDueDate().isBefore(today)
                                    ? 0
                                    : t.getDueDate().equals(today) ? 1 : 2)
                    .thenComparing(t -> t.getDueDate() == null ? LocalDate.MAX : t.getDueDate())
                    .thenComparing(Todo::getSortOrder))
            .toList();
    String names =
        ordered.stream().limit(NAMED).map(Todo::getTitle).collect(Collectors.joining(" · "));
    if (ordered.size() > NAMED) {
      names += " · +" + (ordered.size() - NAMED) + " more";
    }
    String title;
    if (overdue > 0) {
      title = overdue == 1 ? "1 todo is overdue" : overdue + " todos are overdue";
    } else if (dueToday > 0) {
      title = dueToday == 1 ? "1 todo is due today" : dueToday + " todos are due today";
    } else {
      title = open.size() == 1 ? "1 todo is waiting" : open.size() + " todos are waiting";
    }
    return PushService.Notification.todosWaiting(title, names);
  }
}
