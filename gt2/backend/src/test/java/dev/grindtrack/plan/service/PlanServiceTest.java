package dev.grindtrack.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.grindtrack.plan.domain.PlanItem;
import dev.grindtrack.plan.domain.PlanItemRepository;
import dev.grindtrack.plan.domain.PlanQuarter;
import dev.grindtrack.plan.domain.PlanQuarterRepository;
import dev.grindtrack.plan.domain.PlanReference;
import dev.grindtrack.plan.domain.PlanReferenceRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlanServiceTest {

  private PlanItemRepository items;
  private PlanQuarterRepository quarters;
  private PlanReferenceRepository references;
  private PlanService service;

  @BeforeEach
  void setUp() {
    items = mock(PlanItemRepository.class);
    quarters = mock(PlanQuarterRepository.class);
    references = mock(PlanReferenceRepository.class);
    service = new PlanService(items, quarters, references);
  }

  private static PlanItem item(String type, String title, String status, String notes) {
    return new PlanItem(type, title, null, null, null, null, null, null, status, notes, 0);
  }

  @Test
  void importCarriesProgressOntoItemsMatchingByTypeAndTitle() {
    PlanItem existing = item("cert", "AWS SAA", "in_progress", "chapter 3");
    existing.setStatus("done");
    when(items.findAll()).thenReturn(List.of(existing));
    PlanItem incoming = item("cert", "AWS SAA", "not_started", "");

    int imported = service.importPlan(List.of(incoming), List.of(), List.of());

    assertThat(imported).isEqualTo(1);
    assertThat(incoming.getStatus()).isEqualTo("done");
    assertThat(incoming.getCompletedAt()).isEqualTo(existing.getCompletedAt());
    assertThat(incoming.getNotes()).isEqualTo("chapter 3");
  }

  @Test
  void importKeepsIncomingNotesWhenPreviousNotesWereBlank() {
    when(items.findAll()).thenReturn(List.of(item("book", "DDIA", "in_progress", "")));
    PlanItem incoming = item("book", "DDIA", "not_started", "from workbook");

    service.importPlan(List.of(incoming), List.of(), List.of());

    assertThat(incoming.getStatus()).isEqualTo("in_progress");
    assertThat(incoming.getNotes()).isEqualTo("from workbook");
  }

  @Test
  void importLeavesNonMatchingItemsWithTheirIncomingState() {
    when(items.findAll()).thenReturn(List.of(item("cert", "AWS SAA", "done", "old notes")));
    PlanItem differentTitle = item("cert", "CKA", "in_progress", "");
    PlanItem differentType = item("book", "AWS SAA", "not_started", "");

    service.importPlan(List.of(differentTitle, differentType), List.of(), List.of());

    assertThat(differentTitle.getStatus()).isEqualTo("in_progress");
    assertThat(differentTitle.getCompletedAt()).isNull();
    assertThat(differentType.getStatus()).isEqualTo("not_started");
    assertThat(differentType.getNotes()).isEmpty();
  }

  @Test
  void importReplacesAllThreeCollections() {
    when(items.findAll()).thenReturn(List.of());
    List<PlanItem> newItems = List.of(item("cert", "CKA", "not_started", ""));
    List<PlanQuarter> newQuarters = List.of(new PlanQuarter(1, "Q1", 1, null, null, null, null));
    List<PlanReference> newReferences = List.of(new PlanReference("overview", "Overview", "{}", 0));

    int imported = service.importPlan(newItems, newQuarters, newReferences);

    assertThat(imported).isEqualTo(1);
    verify(items).deleteAll();
    verify(quarters).deleteAll();
    verify(references).deleteAll();
    verify(items).saveAll(newItems);
    verify(quarters).saveAll(newQuarters);
    verify(references).saveAll(newReferences);
  }

  @Test
  void updateStampsCompletedAtOnTransitionToDoneAndClearsItOnLeavingDone() {
    PlanItem stored = item("cert", "AWS SAA", "in_progress", "");
    when(items.findById(1L)).thenReturn(Optional.of(stored));
    when(items.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Optional<PlanItem> done = service.update(1L, "done", null);
    assertThat(done).isPresent();
    assertThat(done.get().getCompletedAt()).isNotNull();

    Optional<PlanItem> reopened = service.update(1L, "in_progress", null);
    assertThat(reopened.get().getCompletedAt()).isNull();
  }

  @Test
  void updateOnlyTouchesTheFieldsThatWereSent() {
    PlanItem stored = item("cert", "AWS SAA", "in_progress", "keep me");
    when(items.findById(1L)).thenReturn(Optional.of(stored));
    when(items.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Optional<PlanItem> updated = service.update(1L, null, "new notes");

    assertThat(updated.get().getStatus()).isEqualTo("in_progress");
    assertThat(updated.get().getNotes()).isEqualTo("new notes");
  }

  @Test
  void updateReturnsEmptyForAnUnknownId() {
    when(items.findById(99L)).thenReturn(Optional.empty());
    assertThat(service.update(99L, "done", null)).isEmpty();
  }
}
