package dev.grindtrack.recovery.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.grindtrack.config.RecoveryProperties;
import dev.grindtrack.recovery.domain.BibleNote;
import dev.grindtrack.recovery.domain.BibleNoteRepository;
import dev.grindtrack.recovery.domain.BibleVerse;
import dev.grindtrack.recovery.domain.BibleVerseRepository;
import dev.grindtrack.web.ServiceOffException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Reading from anywhere: the books, a chapter and its neighbours, a reference typed, a note. */
class BibleServiceTest {

  private BibleVerseRepository verses;
  private BibleNoteRepository notes;
  private PassageModel model;
  private BibleService bible;

  /** A tiny Bible: Genesis 1–2, Psalms 1–3, John 1–2, six verses a chapter. */
  private static List<BibleVerse> tiny() {
    List<BibleVerse> out = new ArrayList<>();
    String[][] books = {{"GEN", "2"}, {"PSA", "3"}, {"JHN", "2"}};
    for (String[] b : books) {
      for (int c = 1; c <= Integer.parseInt(b[1]); c++) {
        for (int v = 1; v <= 6; v++) {
          out.add(new BibleVerse(b[0], BibleBooks.ord(b[0]), c, v, v == 1 || v == 4, "verse " + v));
        }
      }
    }
    return out;
  }

  @BeforeEach
  void setUp() {
    verses = mock(BibleVerseRepository.class);
    notes = mock(BibleNoteRepository.class);
    model = mock(PassageModel.class);
    List<BibleVerse> all = tiny();
    when(verses.findAllByOrderByBookOrdAscChapterAscVerseAsc()).thenReturn(all);
    when(verses.findByBookAndChapterOrderByVerseAsc(anyString(), anyInt()))
        .thenAnswer(
            inv ->
                all.stream()
                    .filter(v -> v.getBook().equals(inv.getArgument(0)))
                    .filter(v -> v.getChapter() == inv.<Integer>getArgument(1))
                    .toList());
    when(verses.findByBookAndChapterAndVerseBetweenOrderByVerseAsc(
            anyString(), anyInt(), anyInt(), anyInt()))
        .thenAnswer(
            inv ->
                all.stream()
                    .filter(v -> v.getBook().equals(inv.getArgument(0)))
                    .filter(v -> v.getChapter() == inv.<Integer>getArgument(1))
                    .filter(
                        v ->
                            v.getVerse() >= inv.<Integer>getArgument(2)
                                && v.getVerse() <= inv.<Integer>getArgument(3))
                    .toList());
    when(notes.findById(anyString())).thenReturn(Optional.empty());
    bible =
        new BibleService(
            verses,
            notes,
            model,
            new RecoveryProperties(
                null,
                null,
                null,
                new RecoveryProperties.Bible("x", "World English Bible", "WEB", List.of("JHN"))));
  }

  @Test
  void theBooksComeInCanonicalOrderWithTheirChapterCounts() {
    assertThat(bible.books())
        .extracting(
            BibleService.Book::code, BibleService.Book::chapters, BibleService.Book::testament)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("GEN", 2, "old"),
            org.assertj.core.groups.Tuple.tuple("PSA", 3, "old"),
            org.assertj.core.groups.Tuple.tuple("JHN", 2, "new"));
  }

  @Test
  void aChapterKnowsItsNeighboursAcrossBooks() {
    BibleService.Chapter psalm3 = bible.chapter("PSA", 3).orElseThrow();
    assertThat(psalm3.verses()).hasSize(6);
    assertThat(psalm3.prev().chapter()).isEqualTo(2);
    assertThat(psalm3.next()).isEqualTo(new BibleService.Place("JHN", "John", 1, null));
    assertThat(bible.chapter("GEN", 1).orElseThrow().prev()).isNull();
    assertThat(bible.chapter("JHN", 2).orElseThrow().next()).isNull();
    assertThat(bible.chapter("JHN", 3)).isEmpty();
    assertThat(bible.chapter("TOB", 1)).isEmpty();
  }

  @Test
  void aReferenceIsAPlaceAndAnythingElseIsSearched() {
    assertThat(bible.search("John 2").place())
        .isEqualTo(new BibleService.Place("JHN", "John", 2, null));
    assertThat(bible.search("john 1:4").place().verse()).isEqualTo(4);
    assertThat(bible.search("psalm 3").place().book()).isEqualTo("PSA");
    assertThat(bible.search("Ps 2").place().book()).isEqualTo("PSA");
    assertThat(bible.search("gen 2").place().book()).isEqualTo("GEN");
    // A chapter the book does not have, or a book that is not there, is a search.
    assertThat(bible.search("John 9").place()).isNull();
    assertThat(bible.search("Mark 1").place()).isNull();

    BibleVerse hit = new BibleVerse("JHN", 43, 1, 4, false, "In him was life");
    when(verses.search("life")).thenReturn(List.of(hit));
    BibleService.Search found = bible.search("life");
    assertThat(found.place()).isNull();
    assertThat(found.hits()).hasSize(1);
    assertThat(found.hits().get(0).reference()).isEqualTo("John 1:4");
    assertThat(found.hits().get(0).text()).isEqualTo("In him was life");
  }

  @Test
  void thePlanStartsWithTheConfiguredBookAndThePassageCarriesItsPlace() {
    BibleService.Passage first = bible.passageAt(0);
    assertThat(first.book()).isEqualTo("JHN");
    assertThat(first.position()).isEqualTo(1);
    assertThat(first.explanation()).isNull();
    assertThat(bible.passageAt(bible.planSize()).position()).isEqualTo(1);
  }

  @Test
  void anExplanationIsWrittenOnceAndKept() {
    when(model.configured()).thenReturn(true);
    when(model.explain(anyString(), anyString(), anyString()))
        .thenReturn(new PassageModel.Explained("John opens with…", "m", 300, 120));

    BibleService.Explanation e = bible.explain(0);
    assertThat(e.reference()).isEqualTo(bible.referenceAt(0));
    assertThat(e.body()).isEqualTo("John opens with…");
    verify(model).explain(eq(bible.referenceAt(0)), eq("World English Bible"), any());
    verify(notes).save(any(BibleNote.class));

    when(notes.findById(anyString()))
        .thenReturn(Optional.of(new BibleNote("k", "John opens with…", "m")));
    bible.explain(0);
    verify(model, org.mockito.Mockito.times(1)).explain(anyString(), anyString(), anyString());
    assertThat(bible.passageAt(0).explanation()).isEqualTo("John opens with…");
  }

  @Test
  void noModelMeansAnExplanationIsOffNotAnError() {
    when(model.configured()).thenReturn(false);
    assertThatThrownBy(() -> bible.explain(0)).isInstanceOf(ServiceOffException.class);
    verify(notes, never()).save(any());
  }
}
