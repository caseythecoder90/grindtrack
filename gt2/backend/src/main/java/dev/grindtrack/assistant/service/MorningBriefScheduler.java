package dev.grindtrack.assistant.service;

import dev.grindtrack.config.AssistantProperties;
import dev.grindtrack.push.service.PushService;
import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drafts the brief before the morning block starts, so it is waiting when the today tab opens.
 *
 * <p>Same shape as {@link WeeklyReviewScheduler}: cron and zone from configuration, because six in
 * the morning means the owner's six; off is a valid state; and a failure is logged rather than
 * allowed to take the scheduler thread with it — the button on the today tab is the retry.
 */
@Component
public class MorningBriefScheduler {

  private static final Logger log = LoggerFactory.getLogger(MorningBriefScheduler.class);

  private final MorningBriefService briefs;
  private final BriefModel model;
  private final AssistantProperties props;
  private final PushService push;

  public MorningBriefScheduler(
      MorningBriefService briefs, BriefModel model, AssistantProperties props, PushService push) {
    this.briefs = briefs;
    this.model = model;
    this.props = props;
    this.push = push;
  }

  @Scheduled(cron = "${grindtrack.assistant.brief-cron}", zone = "${grindtrack.assistant.zone}")
  public void draftThisMornings() {
    if (!model.configured()) {
      return;
    }
    LocalDate today = LocalDate.now(ZoneId.of(props.zone()));
    try {
      MorningBriefService.Brief brief = briefs.generate(today);
      log.info("Drafted the morning brief for {} (~${})", today, brief.costUsd());
      tell(PushService.Notification.morningBrief(brief.draft().headline()));
    } catch (Exception e) {
      log.error("The scheduled morning brief failed", e);
    }
  }

  /**
   * The push is the one thing here that happens after the draft is stored, and its failure is its
   * own: a brief that drafted and did not buzz the phone is still a brief on the today tab.
   */
  private void tell(PushService.Notification notification) {
    try {
      PushService.Outcome outcome = push.send(notification);
      if (outcome.sent() + outcome.failed() + outcome.gone() > 0) {
        log.info("Pushed the morning brief: {}", outcome);
      }
    } catch (Exception e) {
      log.error("The morning brief drafted but the push failed", e);
    }
  }
}
