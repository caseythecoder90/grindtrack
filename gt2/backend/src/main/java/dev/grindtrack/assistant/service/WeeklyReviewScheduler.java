package dev.grindtrack.assistant.service;

import dev.grindtrack.config.AssistantProperties;
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

  public WeeklyReviewScheduler(
      WeeklyReviewService reviews, ReviewModel model, AssistantProperties props) {
    this.reviews = reviews;
    this.model = model;
    this.props = props;
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
    } catch (Exception e) {
      // Deliberately broad: a failed Friday draft must not take the scheduler thread with it.
      // The button on the week tab is the retry.
      log.error("The scheduled weekly review draft failed", e);
    }
  }
}
