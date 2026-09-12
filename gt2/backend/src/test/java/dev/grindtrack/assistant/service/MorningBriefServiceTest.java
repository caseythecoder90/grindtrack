package dev.grindtrack.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.grindtrack.assistant.domain.AssistantReport;
import dev.grindtrack.assistant.domain.AssistantReportRepository;
import dev.grindtrack.web.ServiceOffException;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** One row per morning, priced, and off is a state rather than an error. */
class MorningBriefServiceTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 9, 12);
  private static final BriefDraft DRAFT =
      new BriefDraft(
          "Two study blocks booked, both against CKA.",
          "CKA is 20 days out. Last night you wrote \"etcd restore still shaky\".",
          "Take the 6am block for the etcd restore lab, because that is the note you left.");

  private ContextService context;
  private BriefModel model;
  private AssistantReportRepository reports;
  private MorningBriefService service;

  @BeforeEach
  void setUp() {
    context = mock(ContextService.class);
    model = mock(BriefModel.class);
    reports = mock(AssistantReportRepository.class);
    when(reports.save(any())).thenAnswer(inv -> inv.getArgument(0));
    service = new MorningBriefService(context, model, reports, new ObjectMapper());
  }

  @Test
  void generatingStoresOneRowForTheDayWithWhatItCost() {
    when(model.configured()).thenReturn(true);
    when(model.draft(anyString()))
        .thenReturn(new BriefModel.DraftedBrief(DRAFT, "claude-opus-5", 2000, 300));
    when(reports.findByKindAndWeekStart(AssistantReport.KIND_MORNING_BRIEF, TODAY))
        .thenReturn(Optional.empty());

    MorningBriefService.Brief brief = service.generate(TODAY);

    ArgumentCaptor<AssistantReport> saved = ArgumentCaptor.forClass(AssistantReport.class);
    verify(reports).save(saved.capture());
    assertThat(saved.getValue().getWeekStart()).isEqualTo(TODAY);
    assertThat(saved.getValue().getInputTokens()).isEqualTo(2000);
    // 2000 in at $5/MTok + 300 out at $25/MTok = $0.01 + $0.0075
    assertThat(brief.costUsd()).isEqualTo(0.0175);
    assertThat(brief.draft().suggestion()).contains("etcd restore lab");
  }

  /** A redraft replaces the morning's row rather than adding a second one. */
  @Test
  void redraftingReplacesRatherThanAccumulates() {
    when(model.configured()).thenReturn(true);
    when(model.draft(anyString()))
        .thenReturn(new BriefModel.DraftedBrief(DRAFT, "claude-opus-5", 1000, 100));
    AssistantReport existing = new AssistantReport(AssistantReport.KIND_MORNING_BRIEF, TODAY);
    existing.replaceDraft("claude-opus-5", 5, 5, "{}");
    when(reports.findByKindAndWeekStart(AssistantReport.KIND_MORNING_BRIEF, TODAY))
        .thenReturn(Optional.of(existing));

    service.generate(TODAY);

    ArgumentCaptor<AssistantReport> saved = ArgumentCaptor.forClass(AssistantReport.class);
    verify(reports).save(saved.capture());
    assertThat(saved.getValue()).isSameAs(existing);
    assertThat(saved.getValue().getInputTokens()).isEqualTo(1000);
  }

  @Test
  void findReadsTheStoredDraftBack() {
    AssistantReport stored = new AssistantReport(AssistantReport.KIND_MORNING_BRIEF, TODAY);
    stored.replaceDraft(
        "claude-opus-5", 10, 10, "{\"headline\":\"h\",\"today\":\"t\",\"suggestion\":\"s\"}");
    when(reports.findByKindAndWeekStart(AssistantReport.KIND_MORNING_BRIEF, TODAY))
        .thenReturn(Optional.of(stored));

    assertThat(service.find(TODAY)).isPresent();
    assertThat(service.find(TODAY).get().draft().headline()).isEqualTo("h");
  }

  @Test
  void noBriefYetIsEmptyNotAnError() {
    when(reports.findByKindAndWeekStart(AssistantReport.KIND_MORNING_BRIEF, TODAY))
        .thenReturn(Optional.empty());

    assertThat(service.find(TODAY)).isEmpty();
  }

  /** With no key the assistant is off, and the answer is a 503 with a sentence, not a call. */
  @Test
  void generatingWhileOffRefusesBeforeSpendingAnything() {
    when(model.configured()).thenReturn(false);

    assertThatThrownBy(() -> service.generate(TODAY))
        .isInstanceOf(ServiceOffException.class)
        .hasMessageContaining("ANTHROPIC_API_KEY");
    verify(model, never()).draft(anyString());
    verify(reports, never()).save(any());
  }
}
