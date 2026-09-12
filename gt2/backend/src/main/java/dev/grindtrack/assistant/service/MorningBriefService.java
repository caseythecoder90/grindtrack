package dev.grindtrack.assistant.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.grindtrack.assistant.domain.AssistantReport;
import dev.grindtrack.assistant.domain.AssistantReportRepository;
import dev.grindtrack.web.ServiceOffException;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * This morning's brief: drafted once by the scheduler, read from the today tab, redrafted on a
 * click if the morning has changed.
 *
 * <p>One row per day in {@code assistant_reports}, so a redraft replaces rather than accumulates.
 * It costs what one small call costs, and it is the one thing the assistant does that a person sees
 * without asking for it, which is why the prompt is told so firmly to stay short.
 */
@Service
public class MorningBriefService {

  private final ContextService contextService;
  private final BriefModel model;
  private final AssistantReportRepository reports;
  private final ObjectMapper mapper;

  public MorningBriefService(
      ContextService contextService,
      BriefModel model,
      AssistantReportRepository reports,
      ObjectMapper mapper) {
    this.contextService = contextService;
    this.model = model;
    this.reports = reports;
    this.mapper = mapper;
  }

  /** Draft the brief for a day. Replaces any earlier draft for that day. Spends money. */
  @Transactional
  public Brief generate(LocalDate date) {
    if (!model.configured()) {
      throw new ServiceOffException(
          "the assistant is off — set ANTHROPIC_API_KEY on the deployment to turn it on");
    }
    BriefModel.DraftedBrief drafted = model.draft(toJson(contextService.build(date)));
    AssistantReport report =
        reports
            .findByKindAndWeekStart(AssistantReport.KIND_MORNING_BRIEF, date)
            .orElseGet(() -> new AssistantReport(AssistantReport.KIND_MORNING_BRIEF, date));
    report.replaceDraft(
        drafted.model(), drafted.inputTokens(), drafted.outputTokens(), toJson(drafted.draft()));
    reports.save(report);
    return toView(report, drafted.draft());
  }

  @Transactional(readOnly = true)
  public Optional<Brief> find(LocalDate date) {
    return reports
        .findByKindAndWeekStart(AssistantReport.KIND_MORNING_BRIEF, date)
        .map(r -> toView(r, parse(r.getDraftJson())));
  }

  private BriefDraft parse(String json) {
    try {
      return mapper.readValue(json, BriefDraft.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("stored draft no longer parses as BriefDraft", e);
    }
  }

  private String toJson(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("could not serialize", e);
    }
  }

  private static Brief toView(AssistantReport r, BriefDraft draft) {
    return new Brief(
        r.getWeekStart().toString(),
        r.getGeneratedAt().toString(),
        r.getModel(),
        Costs.usd(r.getInputTokens(), r.getOutputTokens(), 0, 0),
        draft);
  }

  public record Brief(
      String date, String generatedAt, String model, double costUsd, BriefDraft draft) {}
}
