package dev.grindtrack.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.grindtrack.assistant.domain.AssistantReport;
import dev.grindtrack.assistant.domain.AssistantReportRepository;
import dev.grindtrack.calendar.domain.EventKind;
import dev.grindtrack.calendar.service.CalendarService;
import dev.grindtrack.plan.domain.PlanItem;
import dev.grindtrack.plan.service.PlanService;
import dev.grindtrack.web.BadRequestException;
import dev.grindtrack.web.ServiceOffException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeekPlanServiceTest {

  private static final LocalDate MONDAY = LocalDate.of(2026, 9, 14);

  @Mock private ContextService contextService;
  @Mock private WeekPlanModel model;
  @Mock private AssistantReportRepository reports;
  @Mock private CalendarService calendar;
  @Mock private PlanService plan;

  private WeekPlanService service;
  private final ObjectMapper mapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    service = new WeekPlanService(contextService, model, reports, calendar, plan, mapper);
  }

  private static WeekPlanDraft draftWith(WeekPlanDraft.Block... blocks) {
    return new WeekPlanDraft("because", List.of(blocks));
  }

  private static WeekPlanDraft.Block block(String date, String from, String to, Long itemId) {
    return new WeekPlanDraft.Block(date, from, to, "CKA labs", itemId);
  }

  private void storedDraft(WeekPlanDraft draft) {
    AssistantReport report = new AssistantReport(AssistantReport.KIND_WEEK_PLAN, MONDAY);
    try {
      report.replaceDraft("claude-opus-5", 1, 1, mapper.writeValueAsString(draft));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    when(reports.findByKindAndWeekStart(AssistantReport.KIND_WEEK_PLAN, MONDAY))
        .thenReturn(Optional.of(report));
  }

  private void planHasItem(long id) {
    PlanItem item = org.mockito.Mockito.mock(PlanItem.class);
    when(item.getId()).thenReturn(id);
    when(plan.allItems()).thenReturn(List.of(item));
  }

  @Test
  void proposeRefusesWhenOff() {
    when(model.configured()).thenReturn(false);

    assertThatThrownBy(() -> service.propose(MONDAY)).isInstanceOf(ServiceOffException.class);
    verify(model, never()).plan(anyString(), anyString());
  }

  @Test
  void proposeWritesAReportAndNothingToTheCalendar() {
    when(model.configured()).thenReturn(true);
    when(model.plan(anyString(), eq("2026-09-14")))
        .thenReturn(
            new WeekPlanModel.PlannedWeek(
                draftWith(block("2026-09-15", "06:00", "08:00", 3L)), "claude-opus-5", 900, 300));
    when(reports.findByKindAndWeekStart(any(), any())).thenReturn(Optional.empty());

    WeekPlanService.Draft draft = service.propose(MONDAY);

    assertThat(draft.blocks()).hasSize(1);
    assertThat(draft.rationale()).isEqualTo("because");
    verify(reports).save(any());
    // The whole point: proposing books nothing.
    verify(calendar, never()).create(anyString(), any(), any(), any(), any(), any(), anyString());
  }

  @Test
  void acceptBooksTheStoredBlocksAsStudyBlocks() {
    storedDraft(draftWith(block("2026-09-15", "06:00", "08:00", 3L)));
    planHasItem(3L);

    WeekPlanService.Accepted accepted = service.accept(MONDAY);

    assertThat(accepted.blocksBooked()).isEqualTo(1);
    verify(calendar)
        .create(
            "CKA labs",
            EventKind.STUDY_BLOCK,
            LocalDate.of(2026, 9, 15),
            LocalTime.of(6, 0),
            LocalTime.of(8, 0),
            3L,
            "");
  }

  @Test
  void acceptRefusesABlockNamingAPlanItemThatDoesNotExist() {
    // The model is told to use real ids. This is where that stops being a matter of trust.
    storedDraft(draftWith(block("2026-09-15", "06:00", "08:00", 999L)));
    planHasItem(3L);

    assertThatThrownBy(() -> service.accept(MONDAY))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("999");
    verify(calendar, never()).create(anyString(), any(), any(), any(), any(), any(), anyString());
  }

  @Test
  void acceptRefusesABlockOutsideTheWeekItPlanned() {
    storedDraft(draftWith(block("2026-09-28", "06:00", "08:00", null)));

    assertThatThrownBy(() -> service.accept(MONDAY))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("outside that week");
  }

  @Test
  void acceptRefusesABlockThatEndsBeforeItStarts() {
    storedDraft(draftWith(block("2026-09-15", "08:00", "06:00", null)));

    assertThatThrownBy(() -> service.accept(MONDAY))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("ends before it starts");
  }

  @Test
  void acceptWithNothingDraftedIsA400NotAnEmptyBooking() {
    when(reports.findByKindAndWeekStart(AssistantReport.KIND_WEEK_PLAN, MONDAY))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.accept(MONDAY))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("no drafted plan");
  }

  @Test
  void aNonMondayIsRefusedBeforeAnythingElseHappens() {
    assertThatThrownBy(() -> service.accept(MONDAY.plusDays(1)))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("Monday");
    verify(reports, never()).findByKindAndWeekStart(any(), any());
  }
}
