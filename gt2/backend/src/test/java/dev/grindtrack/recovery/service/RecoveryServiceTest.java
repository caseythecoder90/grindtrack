package dev.grindtrack.recovery.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.grindtrack.config.AssistantProperties;
import dev.grindtrack.config.RecoveryProperties;
import dev.grindtrack.recovery.domain.JournalEntryRepository;
import dev.grindtrack.recovery.domain.MeditationSessionRepository;
import dev.grindtrack.recovery.domain.RecoveryDailyEntry;
import dev.grindtrack.recovery.domain.RecoveryDailyEntryRepository;
import dev.grindtrack.recovery.domain.RecoveryDay;
import dev.grindtrack.recovery.domain.RecoveryDayRepository;
import dev.grindtrack.recovery.domain.RecoveryParagraph;
import dev.grindtrack.recovery.domain.RecoveryParagraphRepository;
import dev.grindtrack.recovery.domain.RecoverySettings;
import dev.grindtrack.recovery.domain.RecoverySettingsRepository;
import dev.grindtrack.recovery.domain.RecoveryText;
import dev.grindtrack.recovery.domain.RecoveryTextRepository;
import dev.grindtrack.recovery.domain.TextSlot;
import java.time.LocalDate;
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
            bible,
            new RecoveryProperties("2024-09-05", "0 55 7 * * *", null),
            new AssistantProperties(null, null, ZONE, null, null));
  }

  private static RecoveryText book(int paragraphCount) {
    RecoveryText t = new RecoveryText(TextSlot.BIG_BOOK, "Big Book", paragraphCount, 70_000);
    ReflectionTestUtils.setField(t, "id", 7L);
    return t;
  }

  private static RecoveryParagraph para(int seq, int chapter, int words) {
    return new RecoveryParagraph(7L, chapter, "Chapter " + chapter, seq, "p" + seq, words);
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
    assertThat(today.settings().readMinutes()).isEqualTo(5);
    assertThat(today.meditation().streak()).isZero();
  }

  @Test
  void doneForTodayMovesTheCursorPastThePartAndASecondPressDoesNothing() {
    when(texts.findBySlot(TextSlot.BIG_BOOK)).thenReturn(Optional.of(book(100)));
    when(paragraphs.findTop80ByTextIdAndSeqGreaterThanEqualOrderBySeqAsc(7L, 0))
        .thenReturn(List.of(para(0, 1, 500), para(1, 1, 500), para(2, 1, 500)));
    when(paragraphs.findByTextIdAndSeqBetweenOrderBySeqAsc(7L, 0, 1))
        .thenReturn(List.of(para(0, 1, 500), para(1, 1, 500)));
    when(paragraphs.chapters(7L)).thenReturn(List.of());
    when(days.findTop120ByMeditatedTrueOrderByDayDesc()).thenReturn(List.of());
    when(days.countByReadDoneTrue()).thenReturn(1L);

    RecoveryService.Today after = service.finishReading();

    assertThat(settings.getReadCursor()).isEqualTo(2);
    LocalDate today = LocalDate.now(ZoneId.of(ZONE));
    assertThat(dayRows.get(today).isReadDone()).isTrue();
    assertThat(dayRows.get(today).getReadFrom()).isZero();
    assertThat(dayRows.get(today).getReadTo()).isEqualTo(1);
    // The part read stays on screen, and the day number counts today.
    assertThat(after.reading().doneToday()).isTrue();
    assertThat(after.reading().paragraphs())
        .extracting(RecoveryService.Para::seq)
        .containsExactly(0, 1);
    assertThat(after.reading().dayNumber()).isEqualTo(1);

    service.finishReading();
    assertThat(settings.getReadCursor()).isEqualTo(2);
  }

  @Test
  void theCursorWrapsAtTheEndOfTheBookAndCountsAReadThrough() {
    settings.advanceReading(100, 100);

    assertThat(settings.getReadCursor()).isZero();
    assertThat(settings.getReadThroughs()).isEqualTo(1);
  }

  @Test
  void replacingTheBookKeepsTheCursorWhenTheLengthIsCloseAndResetsItWhenItIsNot() {
    String twelve = "CHAPTER ONE\n\n" + "Some words in a paragraph here.\n\n".repeat(12);
    when(texts.findBySlot(TextSlot.BIG_BOOK)).thenReturn(Optional.of(book(12)));
    settings.advanceReading(6, 12);

    ImportReport kept = service.importText(TextSlot.BIG_BOOK, null, twelve, false);
    assertThat(kept.cursorReset()).isFalse();
    assertThat(settings.getReadCursor()).isEqualTo(6);
    assertThat(kept.paragraphs()).isEqualTo(12);
    assertThat(kept.chapters()).hasSize(1);

    String five = "CHAPTER ONE\n\n" + "Some words in a paragraph here.\n\n".repeat(5);
    ImportReport reset = service.importText(TextSlot.BIG_BOOK, "My copy", five, false);
    assertThat(reset.cursorReset()).isTrue();
    assertThat(reset.title()).isEqualTo("My copy");
    assertThat(settings.getReadCursor()).isZero();
  }

  @Test
  void aDryRunWritesNothing() {
    when(texts.findBySlot(any())).thenReturn(Optional.empty());

    ImportReport report =
        service.importText(TextSlot.REFLECTION, null, "JANUARY 1\nTitle\nBody.", true);

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
    when(paragraphs.findTop80ByTextIdAndSeqGreaterThanEqualOrderBySeqAsc(anyLong(), anyInt()))
        .thenReturn(List.of(para(0, 3, 100)));

    assertThat(service.readingsToday())
        .containsExactly("I Am a Miracle", "Psalm 23", "Big Book: Chapter 3");
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
}
