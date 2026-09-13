package dev.grindtrack.recovery.service;

import dev.grindtrack.config.AssistantProperties;
import dev.grindtrack.config.RecoveryProperties;
import dev.grindtrack.recovery.domain.JournalEntry;
import dev.grindtrack.recovery.domain.JournalEntryRepository;
import dev.grindtrack.recovery.domain.MeditationSession;
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
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The recovery tab: the number, the day's readings, the book's cursor, the timer's log and the
 * journal. The imports live here too, because replacing a book and keeping the cursor honest is one
 * transaction.
 */
@Service
public class RecoveryService {

  /** A replacement within this much of the old paragraph count keeps the cursor. */
  private static final double CURSOR_TOLERANCE = 0.05;

  private static final int SAMPLE_CHARS = 200;
  private static final int MISSING_SHOWN = 8;

  private final RecoveryTextRepository texts;
  private final RecoveryParagraphRepository paragraphs;
  private final RecoveryDailyEntryRepository entries;
  private final RecoverySettingsRepository settingsRows;
  private final JournalEntryRepository journal;
  private final MeditationSessionRepository sessions;
  private final RecoveryDayRepository days;
  private final BibleService bible;
  private final RecoveryProperties props;
  private final AssistantProperties zone;

  public RecoveryService(
      RecoveryTextRepository texts,
      RecoveryParagraphRepository paragraphs,
      RecoveryDailyEntryRepository entries,
      RecoverySettingsRepository settingsRows,
      JournalEntryRepository journal,
      MeditationSessionRepository sessions,
      RecoveryDayRepository days,
      BibleService bible,
      RecoveryProperties props,
      AssistantProperties zone) {
    this.texts = texts;
    this.paragraphs = paragraphs;
    this.entries = entries;
    this.settingsRows = settingsRows;
    this.journal = journal;
    this.sessions = sessions;
    this.days = days;
    this.bible = bible;
    this.props = props;
    this.zone = zone;
  }

  // ---- the shapes the page reads -------------------------------------------------------------

  /** Everything the today view needs. Cards the data cannot fill are null, not empty. */
  public record Today(
      String today,
      Number number,
      Daily reflection,
      Daily meditationEntry,
      BibleService.Passage passage,
      Reading reading,
      Settings settings,
      Meditation meditation) {}

  public record Number(
      String sobrietyDate,
      long days,
      String spelledOut,
      long nextMilestoneDays,
      String nextMilestoneLabel,
      long daysToMilestone) {}

  public record Daily(String bookTitle, String title, String body, int month, int day) {}

  public record Reading(
      String bookTitle,
      int chapterNo,
      String chapterTitle,
      List<Para> paragraphs,
      int words,
      int minutes,
      int percent,
      boolean doneToday,
      int dayNumber,
      int readThroughs,
      int paragraphCount,
      List<Chapter> chapters) {}

  public record Para(int seq, String body) {}

  /** {@code state} is done, now or later — where the cursor is against the table of contents. */
  public record Chapter(int no, String title, int firstSeq, long paragraphs, String state) {}

  public record Settings(int readMinutes, int meditationMinutes) {}

  public record Meditation(int streak, boolean doneToday) {}

  public record Library(List<Slot> slots, BibleService.Status bible, String biblePlanStart) {}

  public record Slot(
      String slot,
      String defaultTitle,
      boolean imported,
      String title,
      String importedAt,
      int paragraphs,
      int words,
      long entries) {}

  private LocalDate now() {
    return LocalDate.now(ZoneId.of(zone.zone()));
  }

  @Transactional
  public Today today() {
    LocalDate today = now();
    RecoverySettings settings = settings();
    RecoveryDay day = days.findById(today).orElse(null);
    return new Today(
        today.toString(),
        number(today),
        daily(TextSlot.REFLECTION, today),
        daily(TextSlot.MEDITATION, today),
        bible.passageFor(settings.getBiblePlanStart(), today),
        reading(settings, day),
        new Settings(settings.getReadMinutes(), settings.getMeditationMinutes()),
        new Meditation(streak(today), day != null && day.isMeditated()));
  }

  /** The number, or null when no sobriety date is configured. */
  Number number(LocalDate today) {
    Optional<LocalDate> date = props.sobrietyDay();
    if (date.isEmpty()) {
      return null;
    }
    long days = Milestones.daysSober(date.get(), today);
    Milestones.Milestone next = Milestones.next(days);
    return new Number(
        date.get().toString(),
        days,
        Milestones.spelledOut(date.get(), today),
        next.days(),
        next.label(),
        next.days() - days);
  }

  private Daily daily(TextSlot slot, LocalDate today) {
    return texts
        .findBySlot(slot)
        .flatMap(
            t ->
                entries
                    .findByTextIdAndMonthAndDay(
                        t.getId(), today.getMonthValue(), today.getDayOfMonth())
                    .map(
                        e ->
                            new Daily(
                                t.getTitle(), e.getTitle(), e.getBody(), e.getMonth(), e.getDay())))
        .orElse(null);
  }

  /** Today's part: what was read if today is marked, otherwise what the cursor points at. */
  private Reading reading(RecoverySettings settings, RecoveryDay day) {
    RecoveryText book = texts.findBySlot(TextSlot.BIG_BOOK).orElse(null);
    if (book == null || book.getParagraphCount() == 0) {
      return null;
    }
    boolean done = day != null && day.isReadDone() && day.getReadFrom() != null;
    List<RecoveryParagraph> part;
    if (done) {
      part =
          paragraphs.findByTextIdAndSeqBetweenOrderBySeqAsc(
              book.getId(), day.getReadFrom(), day.getReadTo());
    } else {
      part =
          ReadingPlanner.part(
              paragraphs.findTop80ByTextIdAndSeqGreaterThanEqualOrderBySeqAsc(
                  book.getId(), settings.getReadCursor()),
              settings.getReadMinutes());
    }
    if (part.isEmpty()) {
      return null;
    }
    int cursor = done ? day.getReadTo() + 1 : settings.getReadCursor();
    List<Chapter> chapters = new ArrayList<>();
    int nowChapter = part.get(0).getChapterNo();
    for (RecoveryParagraphRepository.ChapterSummary c : paragraphs.chapters(book.getId())) {
      String state =
          c.getChapterNo() == nowChapter ? "now" : c.getChapterNo() < nowChapter ? "done" : "later";
      chapters.add(
          new Chapter(
              c.getChapterNo(), c.getChapterTitle(), c.getFirstSeq(), c.getParagraphs(), state));
    }
    int words = part.stream().mapToInt(RecoveryParagraph::getWords).sum();
    int percent =
        (int)
            Math.round(
                100.0 * Math.min(cursor, book.getParagraphCount()) / book.getParagraphCount());
    return new Reading(
        book.getTitle(),
        nowChapter,
        part.get(0).getChapterTitle(),
        part.stream().map(p -> new Para(p.getSeq(), p.getBody())).toList(),
        words,
        settings.getReadMinutes(),
        percent,
        done,
        (int) days.countByReadDoneTrue() + (done ? 0 : 1),
        settings.getReadThroughs(),
        book.getParagraphCount(),
        chapters);
  }

  /** Consecutive days meditated, ending today or yesterday. */
  private int streak(LocalDate today) {
    List<RecoveryDay> marked = days.findTop120ByMeditatedTrueOrderByDayDesc();
    if (marked.isEmpty()) {
      return 0;
    }
    LocalDate expect = marked.get(0).getDay().equals(today) ? today : today.minusDays(1);
    int streak = 0;
    for (RecoveryDay d : marked) {
      if (!d.getDay().equals(expect)) {
        break;
      }
      streak++;
      expect = expect.minusDays(1);
    }
    return streak;
  }

  // ---- the writes ----------------------------------------------------------------------------

  /** Marks today read and moves the cursor past the part; a second press changes nothing. */
  @Transactional
  public Today finishReading() {
    LocalDate today = now();
    RecoverySettings settings = settings();
    RecoveryDay day = days.findById(today).orElseGet(() -> new RecoveryDay(today));
    RecoveryText book = texts.findBySlot(TextSlot.BIG_BOOK).orElse(null);
    if (book != null && !day.isReadDone()) {
      List<RecoveryParagraph> part =
          ReadingPlanner.part(
              paragraphs.findTop80ByTextIdAndSeqGreaterThanEqualOrderBySeqAsc(
                  book.getId(), settings.getReadCursor()),
              settings.getReadMinutes());
      if (!part.isEmpty()) {
        int last = part.get(part.size() - 1).getSeq();
        day.markRead(part.get(0).getSeq(), last);
        days.save(day);
        settings.advanceReading(last + 1, book.getParagraphCount());
      }
    }
    return today();
  }

  @Transactional
  public Settings updateSettings(Integer readMinutes, Integer meditationMinutes) {
    RecoverySettings settings = settings();
    if (readMinutes != null) {
      settings.setReadMinutes(readMinutes);
    }
    if (meditationMinutes != null) {
      settings.setMeditationMinutes(meditationMinutes);
    }
    return new Settings(settings.getReadMinutes(), settings.getMeditationMinutes());
  }

  /** A sitting, whether or not the bell was reached; only a finished one marks the day. */
  @Transactional
  public Meditation logSession(int minutes, boolean completed) {
    LocalDate today = now();
    sessions.save(new MeditationSession(OffsetDateTime.now(), minutes, completed));
    settings().setMeditationMinutes(minutes);
    if (completed) {
      RecoveryDay day = days.findById(today).orElseGet(() -> new RecoveryDay(today));
      day.markMeditated();
      days.save(day);
    }
    RecoveryDay day = days.findById(today).orElse(null);
    return new Meditation(streak(today), day != null && day.isMeditated());
  }

  @Transactional
  public void restartReading() {
    settings().restartReading();
  }

  @Transactional
  public void restartBiblePlan() {
    settings().restartBiblePlan(now());
  }

  // ---- the journal ---------------------------------------------------------------------------

  public List<JournalEntry> journal(Long before) {
    return before == null
        ? journal.findTop50ByOrderByIdDesc()
        : journal.findTop50ByIdLessThanOrderByIdDesc(before);
  }

  @Transactional
  public JournalEntry addJournal(String body, boolean spoken) {
    return journal.save(new JournalEntry(body, spoken));
  }

  /** False for an id that was never there, so the controller can answer 404. */
  @Transactional
  public boolean deleteJournal(Long id) {
    if (!journal.existsById(id)) {
      return false;
    }
    journal.deleteById(id);
    return true;
  }

  // ---- the books -----------------------------------------------------------------------------

  public Library library() {
    List<Slot> slots = new ArrayList<>();
    for (TextSlot slot : TextSlot.values()) {
      RecoveryText t = texts.findBySlot(slot).orElse(null);
      slots.add(
          t == null
              ? new Slot(slot.wireValue(), slot.defaultTitle(), false, null, null, 0, 0, 0)
              : new Slot(
                  slot.wireValue(),
                  slot.defaultTitle(),
                  true,
                  t.getTitle(),
                  t.getImportedAt().toString(),
                  t.getParagraphCount(),
                  t.getWordCount(),
                  slot.isDaily() ? entries.countByTextId(t.getId()) : 0));
    }
    return new Library(slots, bible.status(), settings().getBiblePlanStart().toString());
  }

  /**
   * Reads the file, and writes it when this is not a dry run. Writing replaces whatever the slot
   * held; the reading cursor survives a replacement of about the same length, otherwise it goes
   * back to the start and the report says so.
   */
  @Transactional
  public ImportReport importText(TextSlot slot, String title, String content, boolean dryRun) {
    String name = title == null || title.isBlank() ? slot.defaultTitle() : title.trim();
    return slot.isDaily()
        ? importDaily(slot, name, content, dryRun)
        : importBook(name, content, dryRun);
  }

  private ImportReport importBook(String title, String content, boolean dryRun) {
    BookParser.Parsed parsed = BookParser.parse(content);
    List<ImportReport.Chapter> chapters =
        parsed.chapters().stream()
            .map(c -> new ImportReport.Chapter(c.no(), c.title(), c.paragraphs(), c.words()))
            .toList();
    String sample = sample(parsed.paragraphs().get(0).body());
    boolean cursorReset = false;
    if (!dryRun) {
      RecoverySettings settings = settings();
      Optional<RecoveryText> old = texts.findBySlot(TextSlot.BIG_BOOK);
      if (old.isPresent()) {
        int was = old.get().getParagraphCount();
        int now = parsed.paragraphs().size();
        if (Math.abs(was - now) > was * CURSOR_TOLERANCE || settings.getReadCursor() >= now) {
          settings.restartReading();
          cursorReset = settings.getReadCursor() == 0 && was > 0;
        }
        texts.delete(old.get());
        texts.flush();
      }
      RecoveryText text =
          texts.save(
              new RecoveryText(
                  TextSlot.BIG_BOOK, title, parsed.paragraphs().size(), parsed.words()));
      List<RecoveryParagraph> rows = new ArrayList<>(parsed.paragraphs().size());
      for (BookParser.Paragraph p : parsed.paragraphs()) {
        rows.add(
            new RecoveryParagraph(
                text.getId(), p.chapterNo(), p.chapterTitle(), p.seq(), p.body(), p.words()));
      }
      paragraphs.saveAll(rows);
    }
    return new ImportReport(
        dryRun,
        TextSlot.BIG_BOOK.wireValue(),
        title,
        parsed.paragraphs().size(),
        parsed.words(),
        chapters,
        0,
        0,
        List.of(),
        sample,
        parsed.warnings(),
        cursorReset);
  }

  private ImportReport importDaily(TextSlot slot, String title, String content, boolean dryRun) {
    DailyParser.Parsed parsed = DailyParser.parse(content);
    DailyParser.Entry first = parsed.entries().get(0);
    String sample = sample((first.title().isEmpty() ? "" : first.title() + " — ") + first.body());
    int words = parsed.entries().stream().mapToInt(e -> Blocks.words(e.body())).sum();
    if (!dryRun) {
      texts.findBySlot(slot).ifPresent(texts::delete);
      texts.flush();
      RecoveryText text = texts.save(new RecoveryText(slot, title, 0, words));
      List<RecoveryDailyEntry> rows = new ArrayList<>(parsed.entries().size());
      for (DailyParser.Entry e : parsed.entries()) {
        rows.add(new RecoveryDailyEntry(text.getId(), e.month(), e.day(), e.title(), e.body()));
      }
      entries.saveAll(rows);
    }
    return new ImportReport(
        dryRun,
        slot.wireValue(),
        title,
        0,
        words,
        List.of(),
        parsed.entries().size(),
        parsed.missing().size(),
        parsed.missing().subList(0, Math.min(MISSING_SHOWN, parsed.missing().size())),
        sample,
        parsed.warnings(),
        false);
  }

  private static String sample(String text) {
    return text.length() <= SAMPLE_CHARS ? text : text.substring(0, SAMPLE_CHARS).trim() + "…";
  }

  // ---- the push line -------------------------------------------------------------------------

  /** The pieces of the day's readings, for the phone; empty when there is nothing to read. */
  public List<String> readingsToday() {
    LocalDate today = now();
    List<String> pieces = new ArrayList<>();
    Daily reflection = daily(TextSlot.REFLECTION, today);
    if (reflection != null) {
      pieces.add(reflection.title().isEmpty() ? reflection.bookTitle() : reflection.title());
    }
    String reference = bible.referenceFor(settings().getBiblePlanStart(), today);
    if (reference != null) {
      pieces.add(reference);
    }
    RecoverySettings settings = settings();
    texts
        .findBySlot(TextSlot.BIG_BOOK)
        .ifPresent(
            book -> {
              List<RecoveryParagraph> part =
                  paragraphs.findTop80ByTextIdAndSeqGreaterThanEqualOrderBySeqAsc(
                      book.getId(), settings.getReadCursor());
              if (!part.isEmpty()) {
                pieces.add(book.getTitle() + ": " + part.get(0).getChapterTitle());
              }
            });
    return pieces;
  }

  /** The settings row, created with the defaults the first time anything asks for it. */
  private RecoverySettings settings() {
    return settingsRows
        .findById(RecoverySettings.THE_ROW)
        .orElseGet(() -> settingsRows.save(new RecoverySettings()));
  }
}
