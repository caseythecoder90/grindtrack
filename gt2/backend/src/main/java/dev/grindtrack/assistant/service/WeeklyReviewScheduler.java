package dev.grindtrack.assistant.service;

import dev.grindtrack.config.AssistantProperties;
import dev.grindtrack.push.service.PushService;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drafts the review on Friday afternoon so it is waiting when the week tab opens, instead of the
 * week tab starting a thirty-second model call on click.
 *
 * <p>Cron and timezone come from configuration — "Friday at five" means the owner's Friday. The
 * default is 17:00 America/New_York; both are one line in application.yml to change.
 */
@Component
public class WeeklyReviewScheduler {

  private static final Logger log = LoggerFactory.getLogger(WeeklyReviewScheduler.class);

  private final WeeklyReviewService reviews;
  private final ReviewModel model;
  private final AssistantProperties props;
  private final PushService push;

  public WeeklyReviewScheduler(
      WeeklyReviewService reviews, ReviewModel model, AssistantProperties props, PushService push) {
    this.reviews = reviews;
    this.model = model;
    this.props = props;
    this.push = push;
  }

  @Scheduled(cron = "${grindtrack.assistant.review-cron}", zone = "${grindtrack.assistant.zone}")
  public void draftThisWeeksReview() {
    if (!model.configured()) {
      // Off is a valid state, not an error: the app deploys before the key exists.
      return;
    }
    LocalDate monday =
        LocalDate.now(ZoneId.of(props.zone()))
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    try {
      WeeklyReviewService.Report report = reviews.generate(monday);
      log.info(
          "Drafted the weekly review for {} ({} in / {} out, ~${})",
          monday,
          report.inputTokens(),
          report.outputTokens(),
          report.costUsd());
      tell(PushService.Notification.weeklyReview());
    } catch (Exception e) {
      // Deliberately broad: a failed Friday draft must not take the scheduler thread with it.
      // The button on the week tab is the retry.
      log.error("The scheduled weekly review draft failed", e);
    }
  }

  /** Its own try: a review that drafted and did not buzz the phone is still a review. */
  private void tell(PushService.Notification notification) {
    try {
      PushService.Outcome outcome = push.send(notification);
      if (outcome.sent() + outcome.failed() + outcome.gone() > 0) {
        log.info("Pushed the weekly review: {}", outcome);
      }
    } catch (Exception e) {
      log.error("The weekly review drafted but the push failed", e);
    }
  }
}
