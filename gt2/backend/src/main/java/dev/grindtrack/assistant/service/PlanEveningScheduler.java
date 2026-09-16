package dev.grindtrack.assistant.service;

import dev.grindtrack.config.AssistantProperties;
import dev.grindtrack.push.service.PushService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Where the plan stands, to the phone, at the end of the day.
 *
 * <p>Three facts and no adjectives: the week's study hours against the target, the next target and
 * how many days are left to it, and tomorrow's first block. The figures are the same ones the
 * morning brief is given, from {@link ContextService}, so the two never disagree. Nothing is sent
 * until a plan is imported. The motivation line in words is the morning's, written by the model;
 * this is the evening's, written by the numbers.
 */
@Component
public class PlanEveningScheduler {

  private static final Logger log = LoggerFactory.getLogger(PlanEveningScheduler.class);
  private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("MMM d");

  private final ContextService context;
  private final PushService push;
  private final AssistantProperties zone;

  public PlanEveningScheduler(ContextService context, PushService push, AssistantProperties zone) {
    this.context = context;
    this.push = push;
    this.zone = zone;
  }

  @Scheduled(cron = "${grindtrack.plan.evening-cron}", zone = "${grindtrack.assistant.zone}")
  public void send() {
    LocalDate today = LocalDate.now(ZoneId.of(zone.zone()));
    try {
      PushService.Notification notification = notification(context.build(today), today);
      if (notification == null) {
        return;
      }
      PushService.Outcome outcome = push.send(notification);
      if (outcome.sent() + outcome.failed() + outcome.gone() > 0) {
        log.info("Pushed the evening plan line: {}", outcome);
      }
    } catch (Exception e) {
      log.error("The evening plan push failed", e);
    }
  }

  /** The line, or null before a plan exists. Package-private for the test. */
  static PushService.Notification notification(AssistantContext ctx, LocalDate today) {
    if (ctx.quarter() == null && ctx.planInFlight().isEmpty() && ctx.planUpcoming().isEmpty()) {
      return null;
    }
    long week = ChronoUnit.WEEKS.between(ContextService.PLAN_START, today) + 1;
    String title = week < 1 ? "the plan starts soon" : "week " + week + " of the plan";

    List<String> pieces = new ArrayList<>();
    AssistantContext.Week w = ctx.week();
    pieces.add(hours(w.studyHours()) + " of " + hours(w.studyTarget()) + " h this week");

    nextTarget(ctx, today)
        .ifPresent(
            item -> {
              LocalDate due = LocalDate.parse(item.targetDate());
              long days = ChronoUnit.DAYS.between(today, due);
              String when = days == 0 ? "today" : days == 1 ? "tomorrow" : "in " + days + " days";
              pieces.add(item.title() + " · " + MONTH_DAY.format(due) + " · " + when);
            });

    String tomorrow = today.plusDays(1).toString();
    pieces.add(
        ctx.upcomingEvents().stream()
            .filter(e -> tomorrow.equals(e.date()) && e.startTime() != null)
            .filter(e -> !"work_block".equals(e.kind()))
            .findFirst()
            .map(e -> "tomorrow " + e.startTime().substring(0, 5) + " · " + e.title())
            .orElse("nothing booked tomorrow"));

    return PushService.Notification.planEvening(title, String.join(" · ", pieces));
  }

  /** The dated plan item due soonest from today, in flight or upcoming. */
  private static Optional<AssistantContext.PlanItemSummary> nextTarget(
      AssistantContext ctx, LocalDate today) {
    return Stream.concat(ctx.planInFlight().stream(), ctx.planUpcoming().stream())
        .filter(i -> i.targetDate() != null)
        .filter(i -> !LocalDate.parse(i.targetDate()).isBefore(today))
        .min(Comparator.comparing(AssistantContext.PlanItemSummary::targetDate));
  }

  /** "7.5", "8" — a figure the way it is said. */
  private static String hours(double value) {
    return value == Math.rint(value)
        ? Long.toString((long) value)
        : String.format(java.util.Locale.ROOT, "%.1f", value);
  }
}
