package dev.grindtrack.recovery.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.grindtrack.config.AssistantProperties;
import dev.grindtrack.config.RecoveryProperties;
import dev.grindtrack.recovery.domain.Contact;
import dev.grindtrack.recovery.domain.ContactRepository;
import dev.grindtrack.recovery.domain.JournalEntryRepository;
import dev.grindtrack.recovery.domain.MeditationSessionRepository;
import dev.grindtrack.recovery.domain.Person;
import dev.grindtrack.recovery.domain.PersonRepository;
import dev.grindtrack.recovery.domain.PersonRole;
import dev.grindtrack.recovery.domain.RecoveryDailyEntry;
import dev.grindtrack.recovery.domain.RecoveryDailyEntryRepository;
import dev.grindtrack.recovery.domain.RecoveryDay;
import dev.grindtrack.recovery.domain.RecoveryDayRepository;
import dev.grindtrack.recovery.domain.RecoveryFileRepository;
import dev.grindtrack.recovery.domain.RecoveryParagraph;
import dev.grindtrack.recovery.domain.RecoveryParagraphRepository;
import dev.grindtrack.recovery.domain.RecoverySettings;
import dev.grindtrack.recovery.domain.RecoverySettingsRepository;
import dev.grindtrack.recovery.domain.RecoveryText;
import dev.grindtrack.recovery.domain.RecoveryTextRepository;
import dev.grindtrack.recovery.domain.TextSlot;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** The cursor, the day's marks, and what replacing a book does to them. */
class RecoveryServiceTest {

  private static final String ZONE = "America/New_York";

  private RecoveryTextRepository texts;
  private RecoveryParagraphRepository paragraphs;
  private RecoveryDailyEntryRepository entries;
  private RecoveryDayRepository days;
  private PersonRepository people;
  private ContactRepository contacts;
  private BibleService bible;
  private RecoverySettings settings;
  private final Map<LocalDate, RecoveryDay> dayRows = new HashMap<>();
  private RecoveryService service;

  @BeforeEach
  void setUp() {
    texts = mock(RecoveryTextRepository.class);
    paragraphs = mock(RecoveryParagraphRepository.class);
    entries = mock(RecoveryDailyEntryRepository.class);
    days = mock(RecoveryDayRepository.class);
    people = mock(PersonRepository.class);
    contacts = mock(ContactRepository.class);
    bible = mock(BibleService.class);
    RecoverySettingsRepository settingsRows = mock(RecoverySettingsRepository.class);
    settings = new RecoverySettings();
    when(settingsRows.findById(RecoverySettings.THE_ROW)).thenReturn(Optional.of(settings));
    when(days.findById(any()))
        .thenAnswer(inv -> Optional.ofNullable(dayRows.get(inv.<LocalDate>getArgument(0))));
    when(days.save(any()))
        .thenAnswer(
            inv -> {
              RecoveryDay d = inv.getArgument(0);
              dayRows.put(d.getDay(), d);
              return d;
            });
    when(texts.save(any()))
        .thenAnswer(
            inv -> {
              RecoveryText t = inv.getArgument(0);
              ReflectionTestUtils.setField(t, "id", 9L);
              return t;
            });
    service =
        new RecoveryService(
            texts,
            paragraphs,
            entries,
            settingsRows,
            mock(JournalEntryRepository.class),
            mock(MeditationSessionRepository.class),
            days,
            mock(RecoveryFileRepository.class),
            people,
            contacts,
            bible,
            new RecoveryProperties("2024-09-05", "0 55 7 * * *", null, null),
            new AssistantProperties(null, null, ZONE, null, null));
  }

  private static RecoveryText book(int paragraphCount) {
    RecoveryText t = new RecoveryText(TextSlot.BIG_BOOK, "Big Book", paragraphCount, 70_000);
    ReflectionTestUtils.setField(t, "id", 7L);
    return t;
  }

  /** A paragraph on a page: seq 0–1 on page 0, 2–3 on page 1, and so on. */
  private static RecoveryParagraph para(int seq, int chapter, int words) {
    return new RecoveryParagraph(
        7L,
        chapter,
        "Chapter " + chapter,
        seq,
        "p" + seq,
        words,
        String.valueOf(seq / 2 + 1),
        seq / 2);
  }

  private static RecoveryService.Upload txt(String text) {
    return new RecoveryService.Upload("book.txt", text.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void theNumberCountsTheDateItselfAsDayOne() {
    RecoveryService.Number n = service.number(LocalDate.of(2026, 9, 13));

    assertThat(n.days()).isEqualTo(739);
    assertThat(n.spelledOut()).isEqualTo("2 years, 8 days");
    assertThat(n.nextMilestoneLabel()).isEqualTo("750 days");
    assertThat(n.daysToMilestone()).isEqualTo(11);
  }

  @Test
  void withNothingImportedTheCardsAreNullNotEmpty() {
    when(texts.findBySlot(any())).thenReturn(Optional.empty());
    when(days.findTop120ByMeditatedTrueOrderByDayDesc()).thenReturn(List.of());

    RecoveryService.Today today = service.today();

    assertThat(today.number()).isNotNull();
    assertThat(today.reflection()).isNull();
    assertThat(today.reading()).isNull();
    assertThat(today.settings().pagesPerDay()).isEqualTo(2);
    assertThat(today.nextCall()).isNull();
    assertThat(today.meditation().streak()).isZero();
  }

  @Test
  void doneForTodayReadsTwoPagesMovesTheCursorAndASecondPressDoesNothing() {
    when(texts.findBySlot(TextSlot.BIG_BOOK)).thenReturn(Optional.of(book(100)));
    when(paragraphs.findByTextIdAndSeq(eq(7L), anyInt()))
        .thenAnswer(inv -> Optional.of(para(inv.getArgument(1), 1, 100)));
    // Two pages from page 0: paragraphs 0–3 start before page 2.
    when(paragraphs.findByTextIdAndSeqGreaterThanEqualAndPageSeqLessThanOrderBySeqAsc(7L, 0, 2))
        .thenReturn(List.of(para(0, 1, 100), para(1, 1, 100), para(2, 1, 100), para(3, 1, 100)));
    when(paragraphs.findByTextIdAndSeqBetweenOrderBySeqAsc(7L, 0, 3))
        .thenReturn(List.of(para(0, 1, 100), para(1, 1, 100), para(2, 1, 100), para(3, 1, 100)));
    when(paragraphs.chapters(7L)).thenReturn(List.of());
    when(days.findTop120ByMeditatedTrueOrderByDayDesc()).thenReturn(List.of());
    when(days.countByReadDoneTrue()).thenReturn(1L);

    RecoveryService.Today after = service.finishReading();

    assertThat(settings.getReadCursor()).isEqualTo(4);
    LocalDate today = LocalDate.now(ZoneId.of(ZONE));
    assertThat(settings.getReadLastDone()).isEqualTo(today);
    assertThat(dayRows.get(today).isReadDone()).isTrue();
    assertThat(dayRows.get(today).getReadFrom()).isZero();
    assertThat(dayRows.get(today).getReadTo()).isEqualTo(3);
    // The part read stays on screen, and the day number counts today.
    assertThat(after.reading().doneToday()).isTrue();
    assertThat(after.reading().pagesDue()).isZero();
    assertThat(after.reading().pageFrom()).isEqualTo("1");
    assertThat(after.reading().pageTo()).isEqualTo("2");
    assertThat(after.reading().paragraphs())
        .extracting(RecoveryService.Para::seq)
        .containsExactly(0, 1, 2, 3);
    assertThat(after.reading().dayNumber()).isEqualTo(1);

    service.finishReading();
    assertThat(settings.getReadCursor()).isEqualTo(4);
  }

  @Test
  void aMissedDayCarriesOverAndCatchingUpForgivesIt() {
    LocalDate today = LocalDate.now(ZoneId.of(ZONE));
    when(texts.findBySlot(TextSlot.BIG_BOOK)).thenReturn(Optional.of(book(100)));
    when(paragraphs.findByTextIdAndSeq(eq(7L), anyInt()))
        .thenAnswer(inv -> Optional.of(para(inv.getArgument(1), 1, 100)));
    when(paragraphs.findByTextIdAndSeqGreaterThanEqualAndPageSeqLessThanOrderBySeqAsc(
            eq(7L), eq(0), anyInt()))
        .thenAnswer(
            inv -> {
              int end = inv.getArgument(2);
              List<RecoveryParagraph> out = new java.util.ArrayList<>();
              for (int seq = 0; seq / 2 < end; seq++) {
                out.add(para(seq, 1, 100));
              }
              return out;
            });
    when(paragraphs.chapters(7L)).thenReturn(List.of());
    when(days.findTop120ByMeditatedTrueOrderByDayDesc()).thenReturn(List.of());
    // Last done three days ago: today owes six pages, four of them carried.
    settings.advanceReading(0, 100, today.minusDays(3));

    RecoveryService.Reading owed = service.today().reading();
    assertThat(owed.pagesDue()).isEqualTo(6);
    assertThat(owed.pagesCarried()).isEqualTo(4);
    assertThat(owed.paragraphs()).hasSize(12);

    RecoveryService.Reading forgiven = service.catchUp().reading();
    assertThat(forgiven.pagesDue()).isEqualTo(2);
    assertThat(forgiven.pagesCarried()).isZero();
  }

  @Test
  void markingReadToAParagraphCountsEverythingUpToItAsToday() {
    LocalDate today = LocalDate.now(ZoneId.of(ZONE));
    when(texts.findBySlot(TextSlot.BIG_BOOK)).thenReturn(Optional.of(book(100)));
    when(paragraphs.findByTextIdAndSeq(eq(7L), anyInt()))
        .thenAnswer(inv -> Optional.of(para(inv.getArgument(1), 1, 100)));
    when(paragraphs.findByTextIdAndSeqBetweenOrderBySeqAsc(eq(7L), anyInt(), anyInt()))
        .thenReturn(List.of(para(0, 1, 100)));
    when(paragraphs.chapters(7L)).thenReturn(List.of());
    when(days.findTop120ByMeditatedTrueOrderByDayDesc()).thenReturn(List.of());

    service.markReadTo(9);

    assertThat(settings.getReadCursor()).isEqualTo(10);
    assertThat(settings.getReadLastDone()).isEqualTo(today);
    assertThat(settings.getReadingPlace()).isEqualTo(10);
    assertThat(dayRows.get(today).getReadFrom()).isZero();
    assertThat(dayRows.get(today).getReadTo()).isEqualTo(9);
  }

  @Test
  void theCursorWrapsAtTheEndOfTheBookAndCountsAReadThrough() {
    settings.advanceReading(100, 100, LocalDate.of(2026, 9, 13));

    assertThat(settings.getReadCursor()).isZero();
    assertThat(settings.getReadThroughs()).isEqualTo(1);
  }

  @Test
  void replacingTheBookKeepsTheCursorWhenTheLengthIsCloseAndResetsItWhenItIsNot() {
    String twelve = "CHAPTER ONE\n\n" + "Some words in a paragraph here.\n\n".repeat(12);
    when(texts.findBySlot(TextSlot.BIG_BOOK)).thenReturn(Optional.of(book(12)));
    settings.advanceReading(6, 12, LocalDate.of(2026, 9, 13));

    ImportReport kept = service.importFiles(TextSlot.BIG_BOOK, null, List.of(txt(twelve)), false);
    assertThat(kept.cursorReset()).isFalse();
    assertThat(settings.getReadCursor()).isEqualTo(6);
    assertThat(kept.paragraphs()).isEqualTo(12);
    assertThat(kept.chapters()).hasSize(1);
    // Plain text gets a page every three hundred words: twelve short paragraphs is one page.
    assertThat(kept.pages()).isEqualTo(1);

    String five = "CHAPTER ONE\n\n" + "Some words in a paragraph here.\n\n".repeat(5);
    ImportReport reset =
        service.importFiles(TextSlot.BIG_BOOK, "My copy", List.of(txt(five)), false);
    assertThat(reset.cursorReset()).isTrue();
    assertThat(reset.title()).isEqualTo("My copy");
    assertThat(settings.getReadCursor()).isZero();
  }

  @Test
  void aDryRunWritesNothing() {
    when(texts.findBySlot(any())).thenReturn(Optional.empty());

    ImportReport report =
        service.importFiles(
            TextSlot.REFLECTION, null, List.of(txt("JANUARY 1\nTitle\nBody.")), true);

    assertThat(report.dryRun()).isTrue();
    assertThat(report.entries()).isEqualTo(1);
    assertThat(report.missingCount()).isEqualTo(365);
    assertThat(report.sample()).isEqualTo("Title — Body.");
    org.mockito.Mockito.verify(texts, org.mockito.Mockito.never()).save(any());
  }

  @Test
  void theReadingsLineNamesWhatThereIsToRead() {
    RecoveryText reflections = new RecoveryText(TextSlot.REFLECTION, "Daily Reflections", 0, 0);
    ReflectionTestUtils.setField(reflections, "id", 3L);
    when(texts.findBySlot(TextSlot.REFLECTION)).thenReturn(Optional.of(reflections));
    when(texts.findBySlot(TextSlot.BIG_BOOK)).thenReturn(Optional.of(book(100)));
    when(entries.findByTextIdAndMonthAndDay(eq(3L), anyInt(), anyInt()))
        .thenReturn(Optional.of(new RecoveryDailyEntry(3L, 1, 1, "I Am a Miracle", "…")));
    when(bible.referenceFor(any(), any())).thenReturn("Psalm 23");
    when(paragraphs.findByTextIdAndSeq(eq(7L), anyInt()))
        .thenAnswer(inv -> Optional.of(para(inv.getArgument(1), 3, 100)));
    when(paragraphs.findByTextIdAndSeqGreaterThanEqualAndPageSeqLessThanOrderBySeqAsc(
            eq(7L), anyInt(), anyInt()))
        .thenReturn(List.of(para(0, 3, 100), para(1, 3, 100), para(2, 3, 100)));
    when(paragraphs.chapters(7L)).thenReturn(List.of());

    assertThat(service.readingsToday())
        .containsExactly("I Am a Miracle", "Psalm 23", "Big Book · pp. 1–2");
  }

  @Test
  void theStreakEndsTodayOrYesterday() {
    LocalDate today = LocalDate.now(ZoneId.of(ZONE));
    RecoveryDay yesterday = new RecoveryDay(today.minusDays(1));
    yesterday.markMeditated();
    RecoveryDay before = new RecoveryDay(today.minusDays(2));
    before.markMeditated();
    RecoveryDay gap = new RecoveryDay(today.minusDays(4));
    gap.markMeditated();
    when(texts.findBySlot(any())).thenReturn(Optional.empty());
    when(days.findTop120ByMeditatedTrueOrderByDayDesc())
        .thenReturn(List.of(yesterday, before, gap));

    RecoveryService.Today view = service.today();

    assertThat(view.meditation().streak()).isEqualTo(2);
    assertThat(view.meditation().doneToday()).isFalse();
  }

  @Test
  void peopleAreSortedProspectsFirstThenBySoonestDueAndACallResetsTheClock() {
    LocalDate today = LocalDate.now(ZoneId.of(ZONE));
    Person sponsor = new Person("Mike", PersonRole.SPONSOR, 7, "Georgia");
    ReflectionTestUtils.setField(sponsor, "id", 1L);
    ReflectionTestUtils.setField(sponsor, "createdAt", OffsetDateTime.now().minusDays(30));
    Person prospect = new Person("Dan", PersonRole.PROSPECT, 7, null);
    ReflectionTestUtils.setField(prospect, "id", 2L);
    Person friend = new Person("Jake", PersonRole.FRIEND, 14, null);
    ReflectionTestUtils.setField(friend, "id", 3L);
    when(people.findByArchivedFalseOrderByCreatedAtAsc())
        .thenReturn(List.of(sponsor, prospect, friend));
    when(people.findById(2L)).thenReturn(Optional.of(prospect));
    Contact call = new Contact(1L, "good talk");
    ReflectionTestUtils.setField(call, "at", OffsetDateTime.now().minusDays(10));
    when(contacts.findFirstByPersonIdOrderByAtDesc(1L)).thenReturn(Optional.of(call));
    when(contacts.findFirstByPersonIdOrderByAtDesc(2L)).thenReturn(Optional.empty());
    when(contacts.findFirstByPersonIdOrderByAtDesc(3L)).thenReturn(Optional.empty());

    List<RecoveryService.PersonView> list = service.people();

    assertThat(list)
        .extracting(RecoveryService.PersonView::name)
        .containsExactly("Dan", "Mike", "Jake");
    assertThat(list.get(0).state()).isEqualTo("ask");
    assertThat(list.get(1).state()).isEqualTo("overdue");
    assertThat(list.get(1).overdueDays()).isEqualTo(3);
    assertThat(list.get(1).nextDue()).isEqualTo(today.minusDays(3).toString());
    assertThat(list.get(2).state()).isEqualTo("ok");
    assertThat(service.peopleToCall())
        .extracting(RecoveryService.PersonView::name)
        .containsExactly("Dan", "Mike");

    // Asking is logging the first call: the prospect becomes a sponsor.
    RecoveryService.PersonView asked = service.logContact(2L, "said yes").orElseThrow();
    assertThat(asked.role()).isEqualTo("sponsor");
  }

  @Test
  void aSnippetIsTheWordsInTheirSurroundingsOnWordBoundaries() {
    String body =
        "Half measures availed us nothing. We stood at the turning point. We asked His"
            + " protection and care with complete abandon, and it was a long paragraph after that,"
            + " going on for a while so the end is cut, and cut on a word rather than in one.";

    String hit = RecoveryService.snippet(body, List.of("turning", "point"));

    assertThat(hit).startsWith("Half measures availed").contains("turning point").endsWith("…");
    assertThat(RecoveryService.snippet(body, List.of("abandon"))).startsWith("…");
    // A word the paragraph carries only in a stemmed form still lands somewhere near it.
    assertThat(RecoveryService.snippet(body, List.of("protections"))).contains("protection");
    assertThat(RecoveryService.snippet("short", List.of("zzz"))).isEqualTo("short");
  }
}
