package dev.grindtrack.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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
import org.mockito.ArgumentCaptor;

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

  /**
   * The rows actually written.
   *
   * <p>These tests used to assert on the incoming objects, which worked only because import was
   * delete-all + insert and therefore saved exactly what it was handed. It now updates matched rows
   * in place so their ids survive, and the incoming object is discarded — so what is saved is the
   * only thing worth asserting on, and always was.
   */
  @SuppressWarnings("unchecked")
  private List<PlanItem> saved() {
    ArgumentCaptor<List<PlanItem>> captor = ArgumentCaptor.forClass(List.class);
    verify(items).saveAll(captor.capture());
    return captor.getValue();
  }

  /** The rows dropped because the workbook no longer lists them. */
  @SuppressWarnings("unchecked")
  private List<PlanItem> deleted() {
    ArgumentCaptor<List<PlanItem>> captor = ArgumentCaptor.forClass(List.class);
    verify(items).deleteAll(captor.capture());
    return captor.getValue();
  }

  @Test
  void importCarriesProgressOntoItemsMatchingByTypeAndTitle() {
    PlanItem existing = item("cert", "AWS SAA", "in_progress", "chapter 3");
    existing.setStatus("done");
    when(items.findAll()).thenReturn(List.of(existing));
    PlanItem incoming = item("cert", "AWS SAA", "not_started", "");

    int imported = service.importPlan(List.of(incoming), List.of(), List.of());

    assertThat(imported).isEqualTo(1);
    // The existing row is what survives — same object, so anything referencing its id still does.
    assertThat(saved()).containsExactly(existing);
    assertThat(existing.getStatus()).isEqualTo("done");
    assertThat(existing.getNotes()).isEqualTo("chapter 3");
  }

  @Test
  void importUpdatesMatchedRowsInPlaceSoTheirIdsSurvive() {
    // A focus session records which plan item an hour went into. If re-import replaced the row,
    // every lunch session ever logged against this book would be orphaned on the next import.
    PlanItem existing = item("book", "DDIA", "in_progress", "ch. 3");
    when(items.findAll()).thenReturn(List.of(existing));
    PlanItem incoming =
        new PlanItem(
            "book", "DDIA", "revised details", null, null, null, null, null, "not_started", "", 42);

    service.importPlan(List.of(incoming), List.of(), List.of());

    assertThat(saved()).containsExactly(existing);
    assertThat(saved().get(0)).isSameAs(existing);
    // Workbook content still lands on it...
    assertThat(existing.getSortOrder()).isEqualTo(42);
    assertThat(existing.getDetails()).isEqualTo("revised details");
    // ...and the matched row is not deleted on the way past.
    assertThat(deleted()).isEmpty();
  }

  @Test
  void importRemovesItemsTheWorkbookNoLongerContains() {
    PlanItem dropped = item("cert", "RHCSA", "not_started", "");
    when(items.findAll()).thenReturn(List.of(dropped));
    PlanItem kept = item("cert", "CKA", "not_started", "");

    service.importPlan(List.of(kept), List.of(), List.of());

    assertThat(saved()).containsExactly(kept);
    assertThat(deleted()).containsExactly(dropped);
  }

  @Test
  void importKeepsIncomingNotesWhenPreviousNotesWereBlank() {
    when(items.findAll()).thenReturn(List.of(item("book", "DDIA", "in_progress", "")));
    PlanItem incoming = item("book", "DDIA", "not_started", "from workbook");

    service.importPlan(List.of(incoming), List.of(), List.of());

    // The workbook may seed a note onto an item that has none; it may never overwrite one.
    assertThat(saved())
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.getStatus()).isEqualTo("in_progress");
              assertThat(row.getNotes()).isEqualTo("from workbook");
            });
  }

  @Test
  void importLeavesNonMatchingItemsWithTheirIncomingState() {
    when(items.findAll()).thenReturn(List.of(item("cert", "AWS SAA", "done", "old notes")));
    PlanItem differentTitle = item("cert", "CKA", "in_progress", "");
    PlanItem differentType = item("book", "AWS SAA", "not_started", "");

    service.importPlan(List.of(differentTitle, differentType), List.of(), List.of());

    assertThat(saved()).containsExactly(differentTitle, differentType);
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
    verify(items).deleteAll(anyList());
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
