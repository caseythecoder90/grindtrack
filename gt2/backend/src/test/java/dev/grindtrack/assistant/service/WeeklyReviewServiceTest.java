package dev.grindtrack.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeeklyReviewServiceTest {

  private static final ReviewDraft DRAFT =
      new ReviewDraft("summary", "wins", "blockers", "adjustments", "next", true);

  @Mock private ContextService contextService;
  @Mock private ReviewModel model;
  @Mock private AssistantReportRepository reports;
  @Mock private AssistantMessageRepository chatMessages;

  private WeeklyReviewService service;

  @BeforeEach
  void setUp() {
    AssistantProperties props =
        new AssistantProperties("key", "claude-opus-5", "America/New_York", "0 0 17 * * FRI");
    service =
        new WeeklyReviewService(
            contextService, model, reports, chatMessages, props, new ObjectMapper());
  }

  private void modelAnswers() {
    when(model.configured()).thenReturn(true);
    when(model.draft(anyString())).thenReturn(new DraftedReview(DRAFT, "claude-opus-5", 4000, 900));
    when(reports.save(any())).thenAnswer(inv -> inv.getArgument(0));
  }

  @Test
  void generateRefusesWhenNoKeyIsConfigured() {
    when(model.configured()).thenReturn(false);

    assertThatThrownBy(() -> service.generate(monday(0)))
        .isInstanceOf(ServiceOffException.class)
        .hasMessageContaining("ANTHROPIC_API_KEY");
    verify(model, never()).draft(anyString());
  }

  @Test
  void generateRefusesANonMonday() {
    when(model.configured()).thenReturn(true);

    assertThatThrownBy(() -> service.generate(monday(0).plusDays(2)))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("Monday");
    verify(model, never()).draft(anyString());
  }

  @Test
  void generatingTheCurrentWeekAnchorsTheContextOnToday() {
    modelAnswers();
    when(reports.findByKindAndWeekStart(any(), any())).thenReturn(Optional.empty());

    service.generate(monday(0));

    verify(contextService).build(LocalDate.now());
  }

  @Test
  void redraftingAPastWeekAnchorsOnItsSundayNotOnToday() {
    // Without the clamp, redrafting last week on a Monday would review the new, empty week.
    modelAnswers();
    when(reports.findByKindAndWeekStart(any(), any())).thenReturn(Optional.empty());
    LocalDate lastMonday = monday(-1);

    service.generate(lastMonday);

    verify(contextService).build(lastMonday.plusDays(6));
  }

  @Test
  void regeneratingReplacesTheExistingRowRatherThanAddingOne() {
    modelAnswers();
    AssistantReport existing = new AssistantReport(AssistantReport.KIND_WEEKLY_REVIEW, monday(0));
    existing.replaceDraft("old-model", 1, 1, "{}");
    when(reports.findByKindAndWeekStart(AssistantReport.KIND_WEEKLY_REVIEW, monday(0)))
        .thenReturn(Optional.of(existing));

    service.generate(monday(0));

    ArgumentCaptor<AssistantReport> saved = ArgumentCaptor.forClass(AssistantReport.class);
    verify(reports).save(saved.capture());
    assertThat(saved.getValue()).isSameAs(existing);
    assertThat(saved.getValue().getModel()).isEqualTo("claude-opus-5");
    assertThat(saved.getValue().getInputTokens()).isEqualTo(4000);
  }

  @Test
  void theStoredDraftRoundTripsThroughJsonIntact() {
    modelAnswers();
    when(reports.findByKindAndWeekStart(any(), any())).thenReturn(Optional.empty());

    WeeklyReviewService.Report report = service.generate(monday(0));

    assertThat(report.draft()).isEqualTo(DRAFT);
    assertThat(report.weekStart()).isEqualTo(monday(0).toString());
  }

  @Test
  void statusSumsTheMonthAndPricesIt() {
    when(model.configured()).thenReturn(true);
    AssistantReport a = new AssistantReport(AssistantReport.KIND_WEEKLY_REVIEW, monday(-1));
    a.replaceDraft("claude-opus-5", 100_000, 20_000, "{}");
    AssistantReport b = new AssistantReport(AssistantReport.KIND_WEEKLY_REVIEW, monday(0));
    b.replaceDraft("claude-opus-5", 100_000, 20_000, "{}");
    when(reports.findByGeneratedAtGreaterThanEqual(any(OffsetDateTime.class)))
        .thenReturn(List.of(a, b));
    // Chat spend joins the same bill: 100k in at $5 + 20k out at $25 = another $1.
    when(chatMessages.findByCreatedAtGreaterThanEqual(any(OffsetDateTime.class)))
        .thenReturn(List.of(new AssistantMessage(1L, "assistant", "hi", 100_000, 20_000, 0, 0)));

    WeeklyReviewService.Status status = service.status();

    assertThat(status.configured()).isTrue();
    assertThat(status.reportsThisMonth()).isEqualTo(2);
    assertThat(status.inputTokens()).isEqualTo(300_000);
    assertThat(status.outputTokens()).isEqualTo(60_000);
    // 0.3 MTok in at $5 + 0.06 MTok out at $25 = $1.50 + $1.50 = $3 exactly.
    assertThat(status.costThisMonthUsd()).isEqualTo(3.0);
  }

  /**
   * Cached tokens are not free and they are not full price. The API leaves them out of {@code
   * input_tokens} entirely, so a month priced without them reads as cheaper than the invoice.
   */
  @Test
  void cachedTokensArePricedAsCachedTokens() {
    when(model.configured()).thenReturn(true);
    when(reports.findByGeneratedAtGreaterThanEqual(any(OffsetDateTime.class)))
        .thenReturn(List.of());
    // 1 MTok written at 1.25x $5 = $6.25; 2 MTok read at 0.1x $5 = $1.00. No uncached input,
    // no output.
    when(chatMessages.findByCreatedAtGreaterThanEqual(any(OffsetDateTime.class)))
        .thenReturn(
            List.of(new AssistantMessage(1L, "assistant", "hi", 0, 0, 1_000_000, 2_000_000)));

    WeeklyReviewService.Status status = service.status();

    assertThat(status.cacheWriteTokens()).isEqualTo(1_000_000);
    assertThat(status.cacheReadTokens()).isEqualTo(2_000_000);
    assertThat(status.costThisMonthUsd()).isEqualTo(7.25);
    // Those 2 MTok would have cost $10 uncached and cost $1, saving $9; the write cost $1.25 more
    // than the same tokens uncached. Net $7.75.
    assertThat(status.cacheSavingUsd()).isEqualTo(7.75);
  }

  /**
   * A prefix written and never read back is a surcharge, and the number has to be able to say so.
   */
  @Test
  void cacheSavingGoesNegativeWhenNothingIsEverRead() {
    when(model.configured()).thenReturn(true);
    when(reports.findByGeneratedAtGreaterThanEqual(any(OffsetDateTime.class)))
        .thenReturn(List.of());
    when(chatMessages.findByCreatedAtGreaterThanEqual(any(OffsetDateTime.class)))
        .thenReturn(List.of(new AssistantMessage(1L, "assistant", "hi", 0, 0, 1_000_000, 0)));

    // 1 MTok written at 1.25x $5 rather than $5: a quarter of $5 wasted.
    assertThat(service.status().cacheSavingUsd()).isEqualTo(-1.25);
  }

  private static LocalDate monday(int weeksAgo) {
    return LocalDate.now()
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        .plusWeeks(weeksAgo);
  }
}
