package dev.grindtrack.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The one property that makes a conversational log safe: a draft is a set of changes, and
 * everything it did not mention survives. "Logged two hours on etcd" must never blank the wins
 * written that morning.
 */
class DayLogServiceTest {

  private static final LocalDate DAY = LocalDate.now().minusDays(1);

  private TrackingService tracking;
  private AssistantReportRepository reports;
  private DayLogService service;

  @BeforeEach
  void setUp() {
    tracking = mock(TrackingService.class);
    reports = mock(AssistantReportRepository.class);
    when(reports.save(any())).thenAnswer(inv -> inv.getArgument(0));
    service =
        new DayLogService(
            tracking,
            reports,
            new AssistantProperties("key", "claude-opus-5", "America/New_York", "0 0 17 * * FRI"),
            new ObjectMapper());
  }

  /** A day that already has notes, hours and an energy score — the base the draft lands on. */
  private DailyLog existingDay() {
    DailyLog log = new DailyLog(DAY);
    log.setHours(new BigDecimal("1.5"));
    log.update(
        List.of("kubernetes"), "CKA labs", "etcd backup and restore", "passed the mock", "none", 4);
    return log;
  }

  private static DayLogDraft onlyDid(String did) {
    return new DayLogDraft(null, null, null, did, null, null, null);
  }

  @Test
  void proposingWritesADraftRowAndNothingToTheDay() {
    when(tracking.day(DAY)).thenReturn(Optional.empty());

    service.propose(DAY, onlyDid("read the etcd chapter"));

    verify(reports).save(any(AssistantReport.class));
    verify(tracking, never()).saveDay(any(), any(), any(), any(), any(), any(), any(), any());
  }

  /** The heart of it: a draft that only says what happened leaves everything else alone. */
  @Test
  void acceptingMergesOverTheDayAsItStandsRatherThanReplacingIt() {
    AssistantReport stored = new AssistantReport(AssistantReport.KIND_DAY_LOG, DAY);
    stored.replaceDraft("claude-opus-5", 0, 0, "{\"did\":\"read the etcd chapter\"}");
    when(reports.findByKindAndWeekStart(AssistantReport.KIND_DAY_LOG, DAY))
        .thenReturn(Optional.of(stored));
    when(tracking.day(DAY)).thenReturn(Optional.of(existingDay()));

    service.accept(DAY);

    verify(tracking)
        .saveDay(
            eq(DAY),
            isNull(), // hours untouched: null means leave alone, exactly as the form does
            eq(List.of("kubernetes")),
            eq("CKA labs"),
            eq("read the etcd chapter"),
            eq("passed the mock"),
            eq("none"),
            eq(4));
  }

  /** The preview a card renders is that same merge, so what you read is what saving writes. */
  @Test
  void thePreviewIsTheMergedDayWithTheChangesMarked() {
    AssistantReport stored = new AssistantReport(AssistantReport.KIND_DAY_LOG, DAY);
    stored.replaceDraft("claude-opus-5", 0, 0, "{\"hours\":2,\"wins\":\"finished the module\"}");
    when(reports.findByKindAndWeekStart(AssistantReport.KIND_DAY_LOG, DAY))
        .thenReturn(Optional.of(stored));
    when(tracking.day(DAY)).thenReturn(Optional.of(existingDay()));

    DayLogService.Draft draft = service.find(DAY).orElseThrow();

    assertThat(draft.result().hours()).isEqualByComparingTo("2");
    assertThat(draft.result().wins()).isEqualTo("finished the module");
    assertThat(draft.result().did()).isEqualTo("etcd backup and restore");
    assertThat(draft.result().energy()).isEqualTo(4);
    assertThat(draft.result().existed()).isTrue();
    assertThat(draft.changes().did()).isNull();
  }

  @Test
  void aDayWithNoEntryPreviewsFromBlank() {
    when(tracking.day(DAY)).thenReturn(Optional.empty());

    DayLogService.Draft draft = service.propose(DAY, onlyDid("started Kafka"));

    assertThat(draft.result().existed()).isFalse();
    assertThat(draft.result().hours()).isEqualByComparingTo("0");
    assertThat(draft.result().categories()).isEmpty();
    assertThat(draft.result().did()).isEqualTo("started Kafka");
  }

  @Test
  void theFutureCannotBeLogged() {
    assertThatThrownBy(() -> service.propose(LocalDate.now().plusDays(1), onlyDid("x")))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("has not happened");
  }

  @Test
  void aDraftThatChangesNothingIsRefusedRatherThanStored() {
    assertThatThrownBy(
            () -> service.propose(DAY, new DayLogDraft(null, null, null, null, null, null, null)))
        .isInstanceOf(BadRequestException.class);
    verify(reports, never()).save(any());
  }

  @Test
  void theLogsOwnLimitsAreCheckedEarlyAsSentences() {
    assertThatThrownBy(
            () ->
                service.propose(
                    DAY, new DayLogDraft(new BigDecimal("30"), null, null, null, null, null, null)))
        .hasMessageContaining("0-24");
    assertThatThrownBy(
            () -> service.propose(DAY, new DayLogDraft(null, null, null, null, null, null, 9)))
        .hasMessageContaining("1-5");
  }

  @Test
  void acceptingWithNoDraftIsA400NotA500() {
    when(reports.findByKindAndWeekStart(AssistantReport.KIND_DAY_LOG, DAY))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.accept(DAY)).isInstanceOf(BadRequestException.class);
  }
}
