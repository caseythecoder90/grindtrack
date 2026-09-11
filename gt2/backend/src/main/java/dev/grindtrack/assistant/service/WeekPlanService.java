package dev.grindtrack.assistant.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.grindtrack.assistant.domain.AssistantReport;
import dev.grindtrack.assistant.domain.AssistantReportRepository;
import dev.grindtrack.calendar.domain.EventKind;
import dev.grindtrack.calendar.service.CalendarService;
import dev.grindtrack.plan.service.PlanService;
import dev.grindtrack.web.BadRequestException;
import dev.grindtrack.web.ServiceOffException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proposing a study week, and booking one once a person says yes.
 *
 * <p>The two halves are deliberately separate methods with a table row between them. Proposing
 * costs money and writes nothing; accepting writes to the calendar and costs nothing. Nothing the
 * model produced reaches the calendar without {@link #accept} being called, and accept re-reads the
 * stored draft rather than trusting anything a client sends.
 */
@Service
public class WeekPlanService {

  private final ContextService contextService;
  private final WeekPlanModel model;
  private final AssistantReportRepository reports;
  private final CalendarService calendar;
  private final PlanService plan;
  private final ObjectMapper mapper;

  public WeekPlanService(
      ContextService contextService,
      WeekPlanModel model,
      AssistantReportRepository reports,
      CalendarService calendar,
      PlanService plan,
      ObjectMapper mapper) {
    this.contextService = contextService;
    this.model = model;
    this.reports = reports;
    this.calendar = calendar;
    this.plan = plan;
    this.mapper = mapper;
  }

  /** Draft a week of blocks. Writes a report row; writes nothing to the calendar. */
  @Transactional
  public Draft propose(LocalDate weekStart) {
    if (!model.configured()) {
      throw new ServiceOffException(
          "the assistant is off — set ANTHROPIC_API_KEY on the deployment to turn it on");
    }
    requireMonday(weekStart);
    WeekPlanModel.PlannedWeek planned =
        model.plan(toJson(contextService.build(LocalDate.now())), weekStart.toString());

    AssistantReport report =
        reports
            .findByKindAndWeekStart(AssistantReport.KIND_WEEK_PLAN, weekStart)
            .orElseGet(() -> new AssistantReport(AssistantReport.KIND_WEEK_PLAN, weekStart));
    report.replaceDraft(
        planned.model(), planned.inputTokens(), planned.outputTokens(), toJson(planned.draft()));
    reports.save(report);
    return toView(report, planned.draft());
  }

  @Transactional(readOnly = true)
  public Optional<Draft> find(LocalDate weekStart) {
    return reports
        .findByKindAndWeekStart(AssistantReport.KIND_WEEK_PLAN, weekStart)
        .map(r -> toView(r, parse(r.getDraftJson())));
  }

  /**
   * Book the drafted blocks.
   *
   * <p>Reads the stored draft rather than accepting blocks from the request: what gets written must
   * be what was shown and approved, not whatever a client chose to send back. Every block is
   * validated here too — the model is told to use real plan item ids and real times, and this is
   * where that stops being a matter of trust.
   */
  @Transactional
  public Accepted accept(LocalDate weekStart) {
    requireMonday(weekStart);
    WeekPlanDraft draft =
        reports
            .findByKindAndWeekStart(AssistantReport.KIND_WEEK_PLAN, weekStart)
            .map(r -> parse(r.getDraftJson()))
            .orElseThrow(() -> new BadRequestException("no drafted plan for that week"));

    Set<Long> knownItems = plan.allItems().stream().map(i -> i.getId()).collect(Collectors.toSet());
    LocalDate sunday = weekStart.plusDays(6);
    int booked = 0;
    for (WeekPlanDraft.Block block : draft.blocks()) {
      LocalDate date = date(block.date());
      if (date.isBefore(weekStart) || date.isAfter(sunday)) {
        throw new BadRequestException("a drafted block falls outside that week: " + block.date());
      }
      if (block.planItemId() != null && !knownItems.contains(block.planItemId())) {
        throw new BadRequestException(
            "a drafted block names a plan item that does not exist: " + block.planItemId());
      }
      LocalTime start = time(block.startTime());
      LocalTime end = time(block.endTime());
      if (!end.isAfter(start)) {
        throw new BadRequestException("a drafted block ends before it starts: " + block.title());
      }
      calendar.create(
          block.title(), EventKind.STUDY_BLOCK, date, start, end, block.planItemId(), "");
      booked++;
    }
    return new Accepted(weekStart.toString(), booked);
  }

  private static void requireMonday(LocalDate weekStart) {
    if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
      throw new BadRequestException("weekStart must be a Monday");
    }
  }

  private static LocalDate date(String value) {
    try {
      return LocalDate.parse(value);
    } catch (DateTimeParseException | NullPointerException e) {
      throw new BadRequestException("a drafted block has an unreadable date: " + value);
    }
  }

  private static LocalTime time(String value) {
    try {
      return LocalTime.parse(value);
    } catch (DateTimeParseException | NullPointerException e) {
      throw new BadRequestException("a drafted block has an unreadable time: " + value);
    }
  }

  private WeekPlanDraft parse(String json) {
    try {
      return mapper.readValue(json, WeekPlanDraft.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("stored draft no longer parses as WeekPlanDraft", e);
    }
  }

  private String toJson(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("could not serialize", e);
    }
  }

  private Draft toView(AssistantReport r, WeekPlanDraft draft) {
    return new Draft(
        r.getWeekStart().toString(),
        r.getGeneratedAt().toString(),
        r.getModel(),
        r.getInputTokens(),
        r.getOutputTokens(),
        draft.rationale(),
        draft.blocks());
  }

  public record Draft(
      String weekStart,
      String generatedAt,
      String model,
      long inputTokens,
      long outputTokens,
      String rationale,
      List<WeekPlanDraft.Block> blocks) {}

  public record Accepted(String weekStart, int blocksBooked) {}
}
