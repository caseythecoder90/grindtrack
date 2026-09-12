package dev.grindtrack.assistant.service;

import dev.grindtrack.config.AssistantProperties;
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

  public MorningBriefScheduler(
      MorningBriefService briefs, BriefModel model, AssistantProperties props) {
    this.briefs = briefs;
    this.model = model;
    this.props = props;
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
    } catch (Exception e) {
      log.error("The scheduled morning brief failed", e);
    }
  }
}
