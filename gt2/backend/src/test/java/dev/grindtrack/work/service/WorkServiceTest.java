package dev.grindtrack.work.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.grindtrack.work.domain.WorkLog;
import dev.grindtrack.work.domain.WorkLogRepository;
import dev.grindtrack.work.domain.WorkSkill;
import dev.grindtrack.work.domain.WorkSkillRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The rules that moved out of {@code WorkController} when this service was extracted.
 *
 * <p>They were previously reachable only by sending an HTTP request, which is the practical cost of
 * putting domain rules in a controller: you can still test them, but every test has to boot MockMvc
 * and serialize JSON to assert a range check.
 */
class WorkServiceTest {

  private static final LocalDate DAY = LocalDate.of(2026, 8, 27);

  private WorkLogRepository workLogs;
  private WorkSkillRepository workSkills;
  private WorkService service;

  @BeforeEach
  void setUp() {
    workLogs = mock(WorkLogRepository.class);
    workSkills = mock(WorkSkillRepository.class);
    service = new WorkService(workLogs, workSkills);

    when(workLogs.findById(any())).thenReturn(Optional.empty());
    when(workLogs.save(any(WorkLog.class))).thenAnswer(i -> i.getArgument(0));
    when(workSkills.save(any(WorkSkill.class))).thenAnswer(i -> i.getArgument(0));
  }

  private WorkLog save(BigDecimal hours, List<String> categories) {
    return service.saveDay(DAY, hours, categories, null, null, null, null, null);
  }

  @Test
  void aDayCannotHoldMoreHoursThanADayHas() {
    assertThatThrownBy(() -> save(new BigDecimal("25"), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("hours must be 0-24");
  }

  @Test
  void negativeHoursAreRejected() {
    assertThatThrownBy(() -> save(new BigDecimal("-1"), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("hours must be 0-24");
  }

  @Test
  void missingHoursMeanZeroRatherThanAFailure() {
    // The form can legitimately save a journal entry with no hours yet.
    assertThat(save(null, null).getHours()).isEqualByComparingTo("0");
  }

  @Test
  void anImplausibleNumberOfCategoriesIsRejectedAsAPasteAccident() {
    assertThatThrownBy(() -> save(BigDecimal.ONE, Collections.nCopies(51, "x")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("too many categories");
  }

  @Test
  void anOverlongCategoryNameIsRejected() {
    assertThatThrownBy(() -> save(BigDecimal.ONE, List.of("x".repeat(101))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("category name over 100 chars");
  }

  @Test
  void savingADayThatAlreadyExistsUpdatesItRatherThanAddingASecond() {
    // The date is the primary key, so this has to be an upsert or a second save would fail.
    WorkLog existing = new WorkLog(DAY);
    when(workLogs.findById(DAY)).thenReturn(Optional.of(existing));

    assertThat(save(new BigDecimal("7.5"), null)).isSameAs(existing);
    assertThat(existing.getHours()).isEqualByComparingTo("7.5");
  }

  // ---------- skills ----------

  @Test
  void aNewSkillGoesToTheEndOfTheList() {
    when(workSkills.count()).thenReturn(4L);
    assertThat(service.createSkill("Kafka", "platform", "").getSortOrder()).isEqualTo(4);
  }

  @Test
  void anUnknownSkillStatusIsRejected() {
    assertThatThrownBy(() -> service.updateSkill(1L, null, null, null, "nearly", null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("status must be one of");
    verify(workSkills, never()).save(any());
  }

  @Test
  void aPartialUpdateLeavesUnsuppliedFieldsAlone() {
    WorkSkill skill = new WorkSkill("Kafka", "platform", "detail", 0);
    skill.setNotes("existing notes");
    when(workSkills.findById(1L)).thenReturn(Optional.of(skill));

    service.updateSkill(1L, null, null, null, "proficient", null, null);

    assertThat(skill.getStatus()).isEqualTo("proficient");
    assertThat(skill.getName()).isEqualTo("Kafka");
    assertThat(skill.getNotes()).isEqualTo("existing notes");
  }

  @Test
  void updatingAnUnknownSkillReportsAbsenceRatherThanThrowing() {
    // The controller turns this into a 404. Absence is a transport question, not a domain error.
    when(workSkills.findById(99L)).thenReturn(Optional.empty());
    assertThat(service.updateSkill(99L, "x", null, null, null, null, null)).isEmpty();
  }
}
