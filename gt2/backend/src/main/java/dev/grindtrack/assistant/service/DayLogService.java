package dev.grindtrack.assistant.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.grindtrack.assistant.domain.AssistantReport;
import dev.grindtrack.assistant.domain.AssistantReportRepository;
import dev.grindtrack.config.AssistantProperties;
import dev.grindtrack.tracking.domain.DailyLog;
import dev.grindtrack.tracking.service.TrackingService;
import dev.grindtrack.web.BadRequestException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drafting a day's log in conversation, and saving one once a person says yes.
 *
 * <p>The same two-halves shape as {@link WeekPlanService}, for the same reason: nothing a model
 * produced reaches the log without {@link #accept} being called, and accept re-reads the stored
 * draft rather than trusting a request body. The difference is that nothing here calls a model —
 * the chat turn that called the tool is the drafter, so there is no second call and no second bill.
 *
 * <p>A draft is a set of changes, not a whole day. {@link #preview} merges it over the day as it
 * currently stands, and that merged result is what the card shows and what accept writes. Doing the
 * merge at read time rather than at draft time means a day edited on the phone after the draft was
 * made is still the base — the draft never resurrects a stale copy of the rest.
 */
@Service
public class DayLogService {

  private final TrackingService tracking;
  private final AssistantReportRepository reports;
  private final AssistantProperties props;
  private final ObjectMapper mapper;

  public DayLogService(
      TrackingService tracking,
      AssistantReportRepository reports,
      AssistantProperties props,
      ObjectMapper mapper) {
    this.tracking = tracking;
    this.reports = reports;
    this.props = props;
    this.mapper = mapper;
  }

  /**
   * Store a draft for a day. Writes a report row; writes nothing to the log.
   *
   * <p>Validation here is the model's argument being checked, so every failure is a sentence the
   * tool can hand back for the model to correct. The day's real limits are enforced again by {@link
   * TrackingService#saveDay} at accept time — this is the early, friendlier copy.
   */
  @Transactional
  public Draft propose(LocalDate date, DayLogDraft draft) {
    if (date.isAfter(LocalDate.now())) {
      throw new BadRequestException("cannot log a day that has not happened yet: " + date);
    }
    if (draft.hours() != null && (draft.hours().signum() < 0 || draft.hours().doubleValue() > 24)) {
      throw new BadRequestException("hours must be 0-24");
    }
    if (draft.energy() != null && (draft.energy() < 1 || draft.energy() > 5)) {
      throw new BadRequestException("energy must be 1-5");
    }
    if (isEmpty(draft)) {
      throw new BadRequestException("the draft changes nothing — say what was logged");
    }
    AssistantReport report =
        reports
            .findByKindAndWeekStart(AssistantReport.KIND_DAY_LOG, date)
            .orElseGet(() -> new AssistantReport(AssistantReport.KIND_DAY_LOG, date));
    // Tokens are zero on purpose: the chat turn that made this draft carries its own bill, and
    // counting them here too would charge the month twice for one call.
    report.replaceDraft(props.model(), 0, 0, toJson(draft));
    reports.save(report);
    return toView(report, draft);
  }

  @Transactional(readOnly = true)
  public Optional<Draft> find(LocalDate date) {
    return reports
        .findByKindAndWeekStart(AssistantReport.KIND_DAY_LOG, date)
        .map(r -> toView(r, parse(r.getDraftJson())));
  }

  /**
   * Save the drafted changes over the day. Reads the stored draft, never the request; merges over
   * the day as it is <em>now</em>; and the log's own validation runs on the result.
   */
  @Transactional
  public Accepted accept(LocalDate date) {
    DayLogDraft draft =
        reports
            .findByKindAndWeekStart(AssistantReport.KIND_DAY_LOG, date)
            .map(r -> parse(r.getDraftJson()))
            .orElseThrow(() -> new BadRequestException("no drafted log for " + date));
    Preview merged = preview(date, draft);
    tracking.saveDay(
        date,
        draft.hours(), // null means leave the hours alone, exactly as the form does
        merged.categories(),
        merged.focus(),
        merged.did(),
        merged.wins(),
        merged.blockers(),
        merged.energy());
    return new Accepted(date.toString());
  }

  /** The day as it would read after the draft is saved over it. */
  private Preview preview(LocalDate date, DayLogDraft draft) {
    DailyLog now = tracking.day(date).orElse(null);
    return new Preview(
        draft.hours() != null ? draft.hours() : now == null ? BigDecimal.ZERO : now.getHours(),
        draft.categories() != null
            ? draft.categories()
            : now == null ? List.of() : now.categoryList(),
        pick(draft.focus(), now == null ? "" : now.getFocus()),
        pick(draft.did(), now == null ? "" : now.getDid()),
        pick(draft.wins(), now == null ? "" : now.getWins()),
        pick(draft.blockers(), now == null ? "" : now.getBlockers()),
        draft.energy() != null ? draft.energy() : now == null ? null : now.getEnergy(),
        now != null);
  }

  private static String pick(String drafted, String current) {
    return drafted != null ? drafted : current;
  }

  private static boolean isEmpty(DayLogDraft d) {
    return d.hours() == null
        && d.categories() == null
        && d.focus() == null
        && d.did() == null
        && d.wins() == null
        && d.blockers() == null
        && d.energy() == null;
  }

  private DayLogDraft parse(String json) {
    try {
      return mapper.readValue(json, DayLogDraft.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("stored draft no longer parses as DayLogDraft", e);
    }
  }

  private String toJson(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("could not serialize", e);
    }
  }

  private Draft toView(AssistantReport r, DayLogDraft draft) {
    return new Draft(
        r.getWeekStart().toString(),
        r.getGeneratedAt().toString(),
        draft,
        preview(r.getWeekStart(), draft));
  }

  /**
   * @param changes what the conversation said, and only that — the card marks these fields
   * @param result the whole day as it will read once saved
   */
  public record Draft(String logDate, String generatedAt, DayLogDraft changes, Preview result) {}

  /**
   * @param existed whether the day already had an entry — "update" and "create" are different
   *     things to say on a button
   */
  public record Preview(
      BigDecimal hours,
      List<String> categories,
      String focus,
      String did,
      String wins,
      String blockers,
      Integer energy,
      boolean existed) {}

  public record Accepted(String logDate) {}
}
