package dev.grindtrack.assistant.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.grindtrack.assistant.domain.AssistantMessage;
import dev.grindtrack.assistant.domain.AssistantMessageRepository;
import dev.grindtrack.assistant.domain.AssistantReport;
import dev.grindtrack.assistant.domain.AssistantReportRepository;
import dev.grindtrack.assistant.service.ReviewModel.DraftedReview;
import dev.grindtrack.config.AssistantProperties;
import dev.grindtrack.web.BadRequestException;
import dev.grindtrack.web.ServiceOffException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drafting the Friday review: assemble the week's context, have the model fill the review form's
 * shape, keep the result where a person can accept it.
 *
 * <p>Everything around the model call — the Monday rule, the anchor clamp, the upsert, the cost
 * arithmetic — lives here and runs against a fake model in tests. The one thing this class never
 * does is write to {@code weekly_reviews}: accepting a draft is the user's click, in the UI,
 * copying fields into the form they already own.
 */
@Service
public class WeeklyReviewService {

  /**
   * Display pricing for the status endpoint, in dollars per million tokens (claude-opus-5).
   * Hard-coded knowingly: this is a label on a personal dashboard, not billing. If the model in
   * config changes, change these with it — the status endpoint carries the model name so a mismatch
   * is at least visible.
   */
  private static final double INPUT_USD_PER_MTOK = 5.00;

  private static final double OUTPUT_USD_PER_MTOK = 25.00;

  /** Five-minute-TTL cache write, as a multiple of the base input rate. */
  private static final double CACHE_WRITE_MULTIPLIER = 1.25;

  /** Cache read, as a multiple of the base input rate — the whole point of caching. */
  private static final double CACHE_READ_MULTIPLIER = 0.1;

  private final ContextService contextService;
  private final ReviewModel model;
  private final AssistantReportRepository reports;
  private final AssistantMessageRepository chatMessages;
  private final AssistantProperties props;
  private final ObjectMapper mapper;

  public WeeklyReviewService(
      ContextService contextService,
      ReviewModel model,
      AssistantReportRepository reports,
      AssistantMessageRepository chatMessages,
      AssistantProperties props,
      ObjectMapper mapper) {
    this.contextService = contextService;
    this.model = model;
    this.reports = reports;
    this.chatMessages = chatMessages;
    this.props = props;
    this.mapper = mapper;
  }

  /**
   * Draft (or redraft) the review for the week starting {@code weekStart}.
   *
   * <p>The context is built as of a day <em>inside</em> that week — today when the week is the
   * current one, its Sunday once it has passed — because "this week" in the context means the week
   * containing the anchor date. Without the clamp, redrafting last week's review on a Monday would
   * quietly review the new, empty week instead.
   */
  @Transactional
  public Report generate(LocalDate weekStart) {
    requireOn();
    if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
      throw new BadRequestException("weekStart must be a Monday");
    }
    LocalDate today = LocalDate.now();
    LocalDate sunday = weekStart.plusDays(6);
    LocalDate anchor =
        today.isAfter(sunday) ? sunday : today.isBefore(weekStart) ? weekStart : today;

    DraftedReview drafted = model.draft(toJson(contextService.build(anchor)));

    AssistantReport report =
        reports
            .findByKindAndWeekStart(AssistantReport.KIND_WEEKLY_REVIEW, weekStart)
            .orElseGet(() -> new AssistantReport(AssistantReport.KIND_WEEKLY_REVIEW, weekStart));
    report.replaceDraft(
        drafted.model(), drafted.inputTokens(), drafted.outputTokens(), toJson(drafted.draft()));
    return toView(reports.save(report));
  }

  @Transactional(readOnly = true)
  public Optional<Report> find(LocalDate weekStart) {
    return reports
        .findByKindAndWeekStart(AssistantReport.KIND_WEEKLY_REVIEW, weekStart)
        .map(this::toView);
  }

  /** Whether the assistant can run, and what it has cost so far this month. */
  @Transactional(readOnly = true)
  public Status status() {
    OffsetDateTime monthStart =
        OffsetDateTime.now()
            .with(TemporalAdjusters.firstDayOfMonth())
            .toLocalDate()
            .atStartOfDay(OffsetDateTime.now().getOffset())
            .toOffsetDateTime();
    List<AssistantReport> thisMonth = reports.findByGeneratedAtGreaterThanEqual(monthStart);
    // Chat spend counts the same as report spend: the bill is the bill.
    List<AssistantMessage> chat = chatMessages.findByCreatedAtGreaterThanEqual(monthStart);
    long in =
        thisMonth.stream().mapToLong(AssistantReport::getInputTokens).sum()
            + chat.stream().mapToLong(AssistantMessage::getInputTokens).sum();
    long out =
        thisMonth.stream().mapToLong(AssistantReport::getOutputTokens).sum()
            + chat.stream().mapToLong(AssistantMessage::getOutputTokens).sum();
    // Only chat caches — a review is one call a week with nothing to reuse, so a breakpoint there
    // would buy a write premium and never a read.
    long cacheWrite = chat.stream().mapToLong(AssistantMessage::getCacheWriteTokens).sum();
    long cacheRead = chat.stream().mapToLong(AssistantMessage::getCacheReadTokens).sum();
    return new Status(
        model.configured(),
        props.model(),
        thisMonth.size(),
        in,
        out,
        cacheWrite,
        cacheRead,
        cost(in, out, cacheWrite, cacheRead),
        cacheSaving(cacheWrite, cacheRead));
  }

  private void requireOn() {
    if (!model.configured()) {
      throw new ServiceOffException(
          "the assistant is off — set ANTHROPIC_API_KEY on the deployment to turn it on");
    }
  }

  private static double cost(long inputTokens, long outputTokens) {
    return cost(inputTokens, outputTokens, 0, 0);
  }

  /**
   * What it actually cost, cached tokens priced as cached tokens.
   *
   * <p>The API reports cache writes and reads in their own fields and leaves them out of {@code
   * inputTokens}, so ignoring them here would not merely lose the saving — it would report a month
   * as cheaper than the invoice says. A write is 1.25x the base input rate, a read 0.1x.
   */
  private static double cost(
      long inputTokens, long outputTokens, long cacheWriteTokens, long cacheReadTokens) {
    double dollars =
        inputTokens / 1_000_000.0 * INPUT_USD_PER_MTOK
            + cacheWriteTokens / 1_000_000.0 * INPUT_USD_PER_MTOK * CACHE_WRITE_MULTIPLIER
            + cacheReadTokens / 1_000_000.0 * INPUT_USD_PER_MTOK * CACHE_READ_MULTIPLIER
            + outputTokens / 1_000_000.0 * OUTPUT_USD_PER_MTOK;
    return Math.round(dollars * 10_000.0) / 10_000.0; // four decimal places: cents matter here
  }

  /**
   * What caching is worth this month, net — and it can be negative.
   *
   * <p>A read saves 0.9x the base rate on a token that would otherwise have been billed in full; a
   * write costs 0.25x extra on a token that may never be read back. Reporting only the reads would
   * flatter the feature. A negative number here means the turns are too short or too far apart for
   * the prefix to be reused, and the breakpoints should come out.
   */
  private static double cacheSaving(long cacheWriteTokens, long cacheReadTokens) {
    double dollars =
        (cacheReadTokens * (1 - CACHE_READ_MULTIPLIER)
                - cacheWriteTokens * (CACHE_WRITE_MULTIPLIER - 1))
            / 1_000_000.0
            * INPUT_USD_PER_MTOK;
    return Math.round(dollars * 10_000.0) / 10_000.0;
  }

  private String toJson(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("could not serialize", e);
    }
  }

  private Report toView(AssistantReport r) {
    ReviewDraft draft;
    try {
      draft = mapper.readValue(r.getDraftJson(), ReviewDraft.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("stored draft no longer parses as ReviewDraft", e);
    }
    return new Report(
        r.getWeekStart().toString(),
        r.getGeneratedAt().toString(),
        r.getModel(),
        r.getInputTokens(),
        r.getOutputTokens(),
        cost(r.getInputTokens(), r.getOutputTokens()),
        draft);
  }

  /** A stored draft plus what it cost — the week tab renders this directly. */
  public record Report(
      String weekStart,
      String generatedAt,
      String model,
      long inputTokens,
      long outputTokens,
      double costUsd,
      ReviewDraft draft) {}

  /**
   * @param configured false shows the week tab a "set the key" note instead of a broken button
   */
  public record Status(
      boolean configured,
      String model,
      int reportsThisMonth,
      long inputTokens,
      long outputTokens,
      long cacheWriteTokens,
      long cacheReadTokens,
      double costThisMonthUsd,
      double cacheSavingUsd) {}
}
