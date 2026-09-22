package dev.grindtrack.recovery.service;

import dev.grindtrack.config.AssistantProperties;
import dev.grindtrack.config.RecoveryProperties;
import dev.grindtrack.recovery.domain.Contact;
import dev.grindtrack.recovery.domain.ContactRepository;
import dev.grindtrack.recovery.domain.JournalEntry;
import dev.grindtrack.recovery.domain.JournalEntryRepository;
import dev.grindtrack.recovery.domain.MeditationSession;
import dev.grindtrack.recovery.domain.MeditationSessionRepository;
import dev.grindtrack.recovery.domain.Person;
import dev.grindtrack.recovery.domain.PersonRepository;
import dev.grindtrack.recovery.domain.PersonRole;
import dev.grindtrack.recovery.domain.RecoveryDailyEntry;
import dev.grindtrack.recovery.domain.RecoveryDailyEntryRepository;
import dev.grindtrack.recovery.domain.RecoveryDay;
import dev.grindtrack.recovery.domain.RecoveryDayRepository;
import dev.grindtrack.recovery.domain.RecoveryFile;
import dev.grindtrack.recovery.domain.RecoveryFileRepository;
import dev.grindtrack.recovery.domain.RecoveryMark;
import dev.grindtrack.recovery.domain.RecoveryMarkRepository;
import dev.grindtrack.recovery.domain.RecoveryParagraph;
import dev.grindtrack.recovery.domain.RecoveryParagraphRepository;
import dev.grindtrack.recovery.domain.RecoverySettings;
import dev.grindtrack.recovery.domain.RecoverySettingsRepository;
import dev.grindtrack.recovery.domain.RecoveryText;
import dev.grindtrack.recovery.domain.RecoveryTextRepository;
import dev.grindtrack.recovery.domain.TextSlot;
import dev.grindtrack.web.BadRequestException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The recovery tab: the number, the day's readings, the book's cursor and pages, the timer's log,
 * the journal, and the people to call. The imports live here too, because replacing a book and
 * keeping the cursor honest is one transaction.
 */
@Service
public class RecoveryService {

  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(RecoveryService.class);

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
  private final RecoveryFileRepository files;
  private final RecoveryMarkRepository marks;
  private final PersonRepository people;
  private final ContactRepository contacts;
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
      RecoveryFileRepository files,
      RecoveryMarkRepository marks,
      PersonRepository people,
      ContactRepository contacts,
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
    this.files = files;
    this.marks = marks;
    this.people = people;
    this.contacts = contacts;
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
      Meditation meditation,
      PersonView nextCall) {}

  public record Number(
      String sobrietyDate,
      long days,
      String spelledOut,
      long nextMilestoneDays,
      String nextMilestoneLabel,
      long daysToMilestone) {}

  public record Daily(String bookTitle, String title, String body, int month, int day) {}

  /**
   * Today's part of the book. {@code pagesDue} is what is owed today; {@code pagesCarried} is how
   * much of that is from days missed. {@code pageFrom}/{@code pageTo} are the printed labels.
   */
  public record Reading(
      String bookTitle,
      int chapterNo,
      String chapterTitle,
      List<Para> paragraphs,
      int words,
      int pagesPerDay,
      int pagesDue,
      int pagesCarried,
      String pageFrom,
      String pageTo,
      int percent,
      boolean doneToday,
      int dayNumber,
      int readThroughs,
      int paragraphCount,
      int pageCount,
      Integer place,
      List<Chapter> chapters) {}

  public record Para(int seq, String body, String pageLabel, int pageSeq) {}

  /** {@code state} is done, now or later — where the cursor is against the table of contents. */
  public record Chapter(
      int no,
      String title,
      int firstSeq,
      long paragraphs,
      String firstPage,
      String lastPage,
      String state) {}

  /** A chapter opened to read: its paragraphs, and its neighbours. */
  public record ChapterText(
      int no,
      String title,
      List<Para> paragraphs,
      Integer prevNo,
      Integer nextNo,
      int cursor,
      List<MarkView> marks) {}

  /**
   * A highlight or a note on a paragraph. {@code start}/{@code end} are offsets into the paragraph,
   * null for the whole of it; {@code color} null is a note alone.
   */
  public record MarkView(
      Long id,
      int seq,
      int chapterNo,
      String chapterTitle,
      String pageLabel,
      Integer start,
      Integer end,
      String quote,
      String color,
      String note,
      String createdAt) {}

  public record Settings(int pagesPerDay, int meditationMinutes) {}

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
      int pages,
      long entries,
      List<String> files) {}

  /**
   * Someone to keep in touch with, as the list shows them. {@code state} is ask (a prospect),
   * overdue, due (today) or ok; {@code nextDue} is the date the cadence points at.
   */
  public record PersonView(
      Long id,
      String name,
      String role,
      int cadenceDays,
      String note,
      String lastContact,
      String lastNote,
      String nextDue,
      long overdueDays,
      String state,
      long contacts) {}

  public record ContactView(Long id, String at, String note) {}

  /** A file as uploaded: its name and its bytes. */
  public record Upload(String name, byte[] bytes) {}

  private LocalDate now() {
    return LocalDate.now(ZoneId.of(zone.zone()));
  }

  @Transactional
  public Today today() {
    LocalDate today = now();
    RecoverySettings settings = settings();
    RecoveryDay day = days.findById(today).orElse(null);
    List<PersonView> due = people(today);
    return new Today(
        today.toString(),
        number(today),
        daily(TextSlot.REFLECTION, today),
        daily(TextSlot.MEDITATION, today),
        passageToday(settings, today),
        reading(settings, day, today),
        new Settings(settings.getPagesPerDay(), settings.getMeditationMinutes()),
        new Meditation(streak(today), day != null && day.isMeditated()),
        due.isEmpty() || due.get(0).state().equals("ok") ? null : due.get(0));
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

  /** Today's part: what was read if today is marked, otherwise what the cursor owes. */
  private Reading reading(RecoverySettings settings, RecoveryDay day, LocalDate today) {
    RecoveryText book = texts.findBySlot(TextSlot.BIG_BOOK).orElse(null);
    if (book == null || book.getParagraphCount() == 0) {
      return null;
    }
    boolean done = day != null && day.isReadDone() && day.getReadFrom() != null;
    int due = done ? 0 : settings.pagesDue(today, today.minusDays(1));
    List<RecoveryParagraph> part =
        done
            ? paragraphs.findByTextIdAndSeqBetweenOrderBySeqAsc(
                book.getId(), day.getReadFrom(), day.getReadTo())
            : partFromCursor(book, settings.getReadCursor(), due);
    if (part.isEmpty()) {
      return null;
    }
    int cursor = done ? day.getReadTo() + 1 : settings.getReadCursor();
    int pageCount = pageCount(book);
    int cursorPage =
        paragraphs
            .findByTextIdAndSeq(book.getId(), Math.min(cursor, book.getParagraphCount() - 1))
            .map(RecoveryParagraph::getPageSeq)
            .orElse(0);
    int nowChapter = part.get(0).getChapterNo();
    List<Chapter> chapters = new ArrayList<>();
    for (RecoveryParagraphRepository.ChapterSummary c : paragraphs.chapters(book.getId())) {
      String state =
          c.getChapterNo() == nowChapter ? "now" : c.getChapterNo() < nowChapter ? "done" : "later";
      chapters.add(
          new Chapter(
              c.getChapterNo(),
              c.getChapterTitle(),
              c.getFirstSeq(),
              c.getParagraphs(),
              label(book, c.getFirstPageSeq()),
              label(book, c.getLastPageSeq()),
              state));
    }
    int words = part.stream().mapToInt(RecoveryParagraph::getWords).sum();
    int percent =
        pageCount == 0 ? 0 : (int) Math.round(100.0 * Math.min(cursorPage, pageCount) / pageCount);
    return new Reading(
        book.getTitle(),
        nowChapter,
        part.get(0).getChapterTitle(),
        part.stream().map(RecoveryService::para).toList(),
        words,
        settings.getPagesPerDay(),
        due,
        Math.max(0, due - settings.getPagesPerDay()),
        part.get(0).getPageLabel(),
        part.get(part.size() - 1).getPageLabel(),
        percent,
        done,
        (int) days.countByReadDoneTrue() + (done ? 0 : 1),
        settings.getReadThroughs(),
        book.getParagraphCount(),
        pageCount,
        settings.getReadingPlace(),
        chapters);
  }

  private List<RecoveryParagraph> partFromCursor(RecoveryText book, int cursor, int pages) {
    int start =
        paragraphs
            .findByTextIdAndSeq(book.getId(), cursor)
            .map(RecoveryParagraph::getPageSeq)
            .orElse(0);
    return ReadingPlanner.part(
        paragraphs.findByTextIdAndSeqGreaterThanEqualAndPageSeqLessThanOrderBySeqAsc(
            book.getId(), cursor, start + Math.max(1, pages)),
        pages);
  }

  private int pageCount(RecoveryText book) {
    return paragraphs
        .findByTextIdAndSeq(book.getId(), book.getParagraphCount() - 1)
        .map(p -> p.getPageSeq() + 1)
        .orElse(0);
  }

  private String label(RecoveryText book, int pageSeq) {
    return paragraphs
        .findFirstByTextIdAndPageSeqOrderBySeqAsc(book.getId(), pageSeq)
        .map(RecoveryParagraph::getPageLabel)
        .orElse(null);
  }

  private static Para para(RecoveryParagraph p) {
    return new Para(p.getSeq(), p.getBody(), p.getPageLabel(), p.getPageSeq());
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

  // ---- the reading -----------------------------------------------------------------------------

  /** Marks today read and moves the cursor past the part; a second press changes nothing. */
  @Transactional
  public Today finishReading() {
    LocalDate today = now();
    RecoverySettings settings = settings();
    RecoveryDay day = days.findById(today).orElseGet(() -> new RecoveryDay(today));
    RecoveryText book = texts.findBySlot(TextSlot.BIG_BOOK).orElse(null);
    if (book != null && !day.isReadDone()) {
      List<RecoveryParagraph> part =
          partFromCursor(
              book, settings.getReadCursor(), settings.pagesDue(today, today.minusDays(1)));
      if (!part.isEmpty()) {
        int last = part.get(part.size() - 1).getSeq();
        day.markRead(part.get(0).getSeq(), last);
        days.save(day);
        settings.advanceReading(last + 1, book.getParagraphCount(), today);
        settings.setReadingPlace(last + 1);
      }
    }
    return today();
  }

  /** Read ahead and say so: everything up to this paragraph counts as read, today. */
  @Transactional
  public Today markReadTo(int seq) {
    LocalDate today = now();
    RecoverySettings settings = settings();
    RecoveryText book =
        texts
            .findBySlot(TextSlot.BIG_BOOK)
            .orElseThrow(() -> new NoSuchElementException("the book"));
    paragraphs
        .findByTextIdAndSeq(book.getId(), seq)
        .orElseThrow(() -> new NoSuchElementException("paragraph " + seq));
    RecoveryDay day = days.findById(today).orElseGet(() -> new RecoveryDay(today));
    int from =
        day.isReadDone() && day.getReadFrom() != null
            ? day.getReadFrom()
            : settings.getReadCursor();
    day.markRead(Math.min(from, seq), seq);
    days.save(day);
    settings.advanceReading(seq + 1, book.getParagraphCount(), today);
    settings.setReadingPlace(seq + 1);
    return today();
  }

  /** Forgives the backlog: tomorrow owes the daily count again. */
  @Transactional
  public Today catchUp() {
    settings().catchUp(now());
    return today();
  }

  @Transactional
  public void savePlace(int seq) {
    settings().setReadingPlace(seq);
  }

  /** The table of contents, for reading anywhere. */
  public Reading book() {
    RecoverySettings settings = settings();
    return reading(settings, days.findById(now()).orElse(null), now());
  }

  public ChapterText chapter(int no) {
    RecoveryText book =
        texts
            .findBySlot(TextSlot.BIG_BOOK)
            .orElseThrow(() -> new NoSuchElementException("the book"));
    List<RecoveryParagraph> ps = paragraphs.findByTextIdAndChapterNoOrderBySeqAsc(book.getId(), no);
    if (ps.isEmpty()) {
      throw new NoSuchElementException("chapter " + no);
    }
    List<RecoveryParagraphRepository.ChapterSummary> all = paragraphs.chapters(book.getId());
    Integer prev = null;
    Integer next = null;
    for (int i = 0; i < all.size(); i++) {
      if (all.get(i).getChapterNo() == no) {
        prev = i > 0 ? all.get(i - 1).getChapterNo() : null;
        next = i + 1 < all.size() ? all.get(i + 1).getChapterNo() : null;
      }
    }
    List<MarkView> inChapter =
        marks
            .findBySlotAndSeqBetweenOrderBySeqAscStartOffAscIdAsc(
                TextSlot.BIG_BOOK, ps.get(0).getSeq(), ps.get(ps.size() - 1).getSeq())
            .stream()
            .map(m -> markView(m, no, ps.get(0).getChapterTitle()))
            .toList();
    return new ChapterText(
        no,
        ps.get(0).getChapterTitle(),
        ps.stream().map(RecoveryService::para).toList(),
        prev,
        next,
        settings().getReadCursor(),
        inChapter);
  }

  // ---- highlights and notes ---------------------------------------------------------------

  static final java.util.Set<String> COLORS = java.util.Set.of("yellow", "green", "blue", "pink");
  static final int MAX_NOTE_CHARS = 4000;

  /** Every mark in the book, in reading order, each with its chapter — the notes view. */
  @Transactional(readOnly = true)
  public List<MarkView> marks() {
    RecoveryText book = texts.findBySlot(TextSlot.BIG_BOOK).orElse(null);
    if (book == null) {
      return List.of();
    }
    List<MarkView> out = new ArrayList<>();
    for (RecoveryMark m : marks.findBySlotOrderBySeqAscStartOffAscIdAsc(TextSlot.BIG_BOOK)) {
      RecoveryParagraph p = paragraphs.findByTextIdAndSeq(book.getId(), m.getSeq()).orElse(null);
      out.add(p == null ? markView(m, 0, "") : markView(m, p.getChapterNo(), p.getChapterTitle()));
    }
    return out;
  }

  /**
   * A highlight, a note, or both, on a paragraph — on the whole of it, or on the run of words
   * between {@code start} and {@code end}.
   *
   * @throws BadRequestException for a paragraph that is not there, offsets outside it, a colour
   *     that is not one of the four, or neither a colour nor a note
   * @throws NoSuchElementException when there is no book
   */
  @Transactional
  public MarkView addMark(int seq, Integer start, Integer end, String color, String note) {
    RecoveryText book =
        texts
            .findBySlot(TextSlot.BIG_BOOK)
            .orElseThrow(() -> new NoSuchElementException("the book"));
    RecoveryParagraph p =
        paragraphs
            .findByTextIdAndSeq(book.getId(), seq)
            .orElseThrow(() -> new BadRequestException("no such paragraph"));
    String tone =
        color == null || color.isBlank() ? null : color.trim().toLowerCase(java.util.Locale.ROOT);
    if (tone != null && !COLORS.contains(tone)) {
      throw new BadRequestException("the colour is yellow, green, blue or pink");
    }
    String words = note == null || note.isBlank() ? null : note.trim();
    if (words != null && words.length() > MAX_NOTE_CHARS) {
      throw new BadRequestException("a note is at most " + MAX_NOTE_CHARS + " characters");
    }
    if (tone == null && words == null) {
      throw new BadRequestException("a mark is a highlight, a note, or both");
    }
    String body = p.getBody();
    Integer from = null;
    Integer to = null;
    if (start != null || end != null) {
      if (start == null || end == null || start < 0 || end > body.length() || start >= end) {
        throw new BadRequestException("the words marked must lie inside the paragraph");
      }
      from = start;
      to = end;
    }
    String quote = from == null ? body : body.substring(from, to);
    RecoveryMark saved =
        marks.save(
            new RecoveryMark(
                TextSlot.BIG_BOOK, seq, p.getPageLabel(), from, to, quote, tone, words));
    return markView(saved, p.getChapterNo(), p.getChapterTitle());
  }

  /**
   * Change a mark's colour or note. Clearing both removes the mark.
   *
   * @throws NoSuchElementException for no such mark — a 404
   */
  @Transactional
  public Optional<MarkView> updateMark(
      long id, String color, boolean clearColor, String note, boolean clearNote) {
    RecoveryMark m = marks.findById(id).orElseThrow(() -> new NoSuchElementException("mark " + id));
    if (clearColor) {
      m.setColor(null);
    } else if (color != null && !color.isBlank()) {
      String tone = color.trim().toLowerCase(java.util.Locale.ROOT);
      if (!COLORS.contains(tone)) {
        throw new BadRequestException("the colour is yellow, green, blue or pink");
      }
      m.setColor(tone);
    }
    if (clearNote) {
      m.setNote(null);
    } else if (note != null) {
      String words = note.trim();
      if (words.length() > MAX_NOTE_CHARS) {
        throw new BadRequestException("a note is at most " + MAX_NOTE_CHARS + " characters");
      }
      m.setNote(words.isEmpty() ? null : words);
    }
    if (m.getColor() == null && m.getNote() == null) {
      marks.delete(m);
      return Optional.empty();
    }
    RecoveryText book = texts.findBySlot(TextSlot.BIG_BOOK).orElse(null);
    RecoveryParagraph p =
        book == null ? null : paragraphs.findByTextIdAndSeq(book.getId(), m.getSeq()).orElse(null);
    return Optional.of(
        p == null ? markView(m, 0, "") : markView(m, p.getChapterNo(), p.getChapterTitle()));
  }

  /**
   * @throws NoSuchElementException for no such mark — a 404
   */
  @Transactional
  public void deleteMark(long id) {
    RecoveryMark m = marks.findById(id).orElseThrow(() -> new NoSuchElementException("mark " + id));
    marks.delete(m);
  }

  /**
   * After the book is imported afresh, every mark finds its paragraph again by its words: on the
   * same printed page first, then anywhere. One that is nowhere keeps its old place and is counted,
   * so the import report can say so.
   *
   * @return how many marks could not be placed
   */
  int reanchor(List<RecoveryParagraph> rows) {
    List<RecoveryMark> all = marks.findBySlotOrderBySeqAscStartOffAscIdAsc(TextSlot.BIG_BOOK);
    int lost = 0;
    for (RecoveryMark m : all) {
      RecoveryParagraph home = null;
      for (RecoveryParagraph p : rows) {
        if (m.getPageLabel() != null
            && m.getPageLabel().equals(p.getPageLabel())
            && p.getBody().contains(m.getQuote())) {
          home = p;
          break;
        }
      }
      if (home == null) {
        for (RecoveryParagraph p : rows) {
          if (p.getBody().contains(m.getQuote())) {
            home = p;
            break;
          }
        }
      }
      if (home == null) {
        lost++;
        continue;
      }
      if (m.isWholeParagraph()) {
        m.moveTo(home.getSeq(), home.getPageLabel(), null, null);
      } else {
        int at = home.getBody().indexOf(m.getQuote());
        m.moveTo(home.getSeq(), home.getPageLabel(), at, at + m.getQuote().length());
      }
      marks.save(m);
    }
    if (lost > 0) {
      log.warn(
          "{} of {} marks in the book could not find their paragraph after the import",
          lost,
          all.size());
    }
    return lost;
  }

  private static MarkView markView(RecoveryMark m, int chapterNo, String chapterTitle) {
    return new MarkView(
        m.getId(),
        m.getSeq(),
        chapterNo,
        chapterTitle,
        m.getPageLabel(),
        m.getStartOff(),
        m.getEndOff(),
        m.getQuote(),
        m.getColor(),
        m.getNote(),
        m.getCreatedAt().toString());
  }

  /** A hit in the book: where it is, and a piece of the paragraph around the words. */
  public record Hit(
      int seq, int chapterNo, String chapterTitle, String pageLabel, String snippet) {}

  /** Where a page begins. */
  public record PagePlace(int chapterNo, String chapterTitle, int seq, String pageLabel) {}

  public List<Hit> search(String q) {
    RecoveryText book = texts.findBySlot(TextSlot.BIG_BOOK).orElse(null);
    if (book == null) {
      return List.of();
    }
    List<String> words = List.of(q.toLowerCase(java.util.Locale.ROOT).trim().split("\\s+"));
    return paragraphs.search(book.getId(), q).stream()
        .map(
            p ->
                new Hit(
                    p.getSeq(),
                    p.getChapterNo(),
                    p.getChapterTitle(),
                    p.getPageLabel(),
                    snippet(p.getBody(), words)))
        .toList();
  }

  /**
   * About a hundred and sixty characters around the first of the words, on word boundaries, with an
   * ellipsis at whichever ends were cut. The whole paragraph is one tap away.
   */
  static String snippet(String body, List<String> words) {
    String lower = body.toLowerCase(java.util.Locale.ROOT);
    int at = -1;
    for (String w : words) {
      if (w.isEmpty()) {
        continue;
      }
      int i = lower.indexOf(w);
      if (i >= 0 && (at < 0 || i < at)) {
        at = i;
      }
      // A stemmed match ("surrendered" for "surrender") is usually the word's first letters.
      if (i < 0 && w.length() > 4) {
        int j = lower.indexOf(w.substring(0, w.length() - 2));
        if (j >= 0 && (at < 0 || j < at)) {
          at = j;
        }
      }
    }
    if (at < 0) {
      at = 0;
    }
    int start = Math.max(0, at - 60);
    int end = Math.min(body.length(), at + 100);
    if (start > 0) {
      int space = body.lastIndexOf(' ', start);
      start = space > 0 ? space + 1 : start;
    }
    if (end < body.length()) {
      int space = body.indexOf(' ', end);
      end = space > 0 ? space : end;
    }
    return (start > 0 ? "…" : "") + body.substring(start, end) + (end < body.length() ? "…" : "");
  }

  public Optional<PagePlace> page(String label) {
    RecoveryText book = texts.findBySlot(TextSlot.BIG_BOOK).orElse(null);
    if (book == null) {
      return Optional.empty();
    }
    return paragraphs
        .findFirstByTextIdAndPageLabelOrderBySeqAsc(
            book.getId(), label.trim().toLowerCase(java.util.Locale.ROOT))
        .map(
            p ->
                new PagePlace(p.getChapterNo(), p.getChapterTitle(), p.getSeq(), p.getPageLabel()));
  }

  @Transactional
  public Settings updateSettings(Integer pagesPerDay, Integer meditationMinutes) {
    RecoverySettings settings = settings();
    if (pagesPerDay != null) {
      settings.setPagesPerDay(pagesPerDay);
    }
    if (meditationMinutes != null) {
      settings.setMeditationMinutes(meditationMinutes);
    }
    return new Settings(settings.getPagesPerDay(), settings.getMeditationMinutes());
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

  // ---- the people ----------------------------------------------------------------------------

  /** Everyone not archived: prospects first, then whoever is due soonest. */
  public List<PersonView> people() {
    return people(now());
  }

  private List<PersonView> people(LocalDate today) {
    List<PersonView> out = new ArrayList<>();
    for (Person p : people.findByArchivedFalseOrderByCreatedAtAsc()) {
      out.add(view(p, today));
    }
    out.sort(
        Comparator.comparing((PersonView v) -> !v.state().equals("ask"))
            .thenComparing(v -> v.nextDue() == null ? "" : v.nextDue()));
    return out;
  }

  private PersonView view(Person p, LocalDate today) {
    Optional<Contact> last = contacts.findFirstByPersonIdOrderByAtDesc(p.getId());
    LocalDate lastDay =
        last.map(c -> c.getAt().atZoneSameInstant(ZoneId.of(zone.zone())).toLocalDate())
            .orElse(null);
    LocalDate since =
        lastDay != null
            ? lastDay
            : p.getCreatedAt().atZoneSameInstant(ZoneId.of(zone.zone())).toLocalDate();
    LocalDate nextDue = since.plusDays(p.getCadenceDays());
    long overdue = ChronoUnit.DAYS.between(nextDue, today);
    String state;
    if (p.getRole() == PersonRole.PROSPECT) {
      state = "ask";
    } else if (overdue > 0) {
      state = "overdue";
    } else if (overdue == 0) {
      state = "due";
    } else {
      state = "ok";
    }
    return new PersonView(
        p.getId(),
        p.getName(),
        p.getRole().wireValue(),
        p.getCadenceDays(),
        p.getNote(),
        last.map(c -> c.getAt().toString()).orElse(null),
        last.map(Contact::getNote).orElse(null),
        p.getRole() == PersonRole.PROSPECT ? null : nextDue.toString(),
        Math.max(0, overdue),
        state,
        contacts.countByPersonId(p.getId()));
  }

  /** The ones to nudge about: still to ask, or past their cadence. */
  public List<PersonView> peopleToCall() {
    return people().stream()
        .filter(v -> v.state().equals("ask") || v.state().equals("overdue"))
        .toList();
  }

  @Transactional
  public PersonView addPerson(String name, PersonRole role, int cadenceDays, String note) {
    return view(people.save(new Person(name, role, cadenceDays, note)), now());
  }

  @Transactional
  public Optional<PersonView> updatePerson(
      Long id, String name, PersonRole role, Integer cadenceDays, String note, boolean clearNote) {
    return people
        .findById(id)
        .map(
            p -> {
              if (name != null) {
                p.rename(name);
              }
              if (role != null) {
                p.setRole(role);
              }
              if (cadenceDays != null) {
                p.setCadenceDays(cadenceDays);
              }
              if (clearNote) {
                p.setNote(null);
              } else if (note != null) {
                p.setNote(note);
              }
              return view(p, now());
            });
  }

  @Transactional
  public boolean archivePerson(Long id) {
    Optional<Person> p = people.findById(id);
    p.ifPresent(Person::archive);
    return p.isPresent();
  }

  /** A call logged. For a prospect this is also the asking, and the answer was yes. */
  @Transactional
  public Optional<PersonView> logContact(Long id, String note) {
    return people
        .findById(id)
        .map(
            p -> {
              contacts.save(new Contact(p.getId(), note));
              p.asked();
              return view(p, now());
            });
  }

  public List<ContactView> contacts(Long personId) {
    if (!people.existsById(personId)) {
      throw new NoSuchElementException("person " + personId);
    }
    return contacts.findTop30ByPersonIdOrderByAtDesc(personId).stream()
        .map(c -> new ContactView(c.getId(), c.getAt().toString(), c.getNote()))
        .toList();
  }

  // ---- the books -----------------------------------------------------------------------------

  public Library library() {
    List<Slot> slots = new ArrayList<>();
    for (TextSlot slot : TextSlot.values()) {
      RecoveryText t = texts.findBySlot(slot).orElse(null);
      List<String> names =
          files.findBySlotOrderByOrdinalAsc(slot).stream().map(RecoveryFile::getFilename).toList();
      slots.add(
          t == null
              ? new Slot(
                  slot.wireValue(), slot.defaultTitle(), false, null, null, 0, 0, 0, 0, names)
              : new Slot(
                  slot.wireValue(),
                  slot.defaultTitle(),
                  true,
                  t.getTitle(),
                  t.getImportedAt().toString(),
                  t.getParagraphCount(),
                  t.getWordCount(),
                  slot.isDaily() ? 0 : pageCount(t),
                  slot.isDaily() ? entries.countByTextId(t.getId()) : 0,
                  names));
    }
    return new Library(slots, bible.status(), settings().getBiblePlanStart().toString());
  }

  /**
   * Reads the files, and writes when this is not a dry run. Writing replaces whatever the slot held
   * and keeps the files, so the book can be read again by a better parser later; the reading cursor
   * survives a replacement of about the same length, otherwise it goes back to the start and the
   * report says so.
   */
  @Transactional
  public ImportReport importFiles(
      TextSlot slot, String title, List<Upload> uploads, boolean dryRun) {
    if (uploads.isEmpty()) {
      throw new BadRequestException("Choose at least one file.");
    }
    String name = title == null || title.isBlank() ? slot.defaultTitle() : title.trim();
    ImportReport report =
        slot.isDaily()
            ? importDaily(slot, name, uploads, dryRun)
            : importBook(name, uploads, dryRun);
    if (!dryRun) {
      files.deleteBySlot(slot);
      int i = 0;
      for (Upload u : uploads) {
        files.save(new RecoveryFile(slot, i++, u.name(), u.bytes()));
      }
    }
    return report;
  }

  /** The stored files through the parser again — after the parser has improved. */
  @Transactional
  public ImportReport reparse(TextSlot slot) {
    List<Upload> uploads =
        files.findBySlotOrderByOrdinalAsc(slot).stream()
            .map(f -> new Upload(f.getFilename(), f.getBytes()))
            .toList();
    if (uploads.isEmpty()) {
      throw new BadRequestException("No files are stored for " + slot.defaultTitle() + ".");
    }
    String title = texts.findBySlot(slot).map(RecoveryText::getTitle).orElse(null);
    return slot.isDaily()
        ? importDaily(slot, title == null ? slot.defaultTitle() : title, uploads, false)
        : importBook(title == null ? slot.defaultTitle() : title, uploads, false);
  }

  private ImportReport importBook(String title, List<Upload> uploads, boolean dryRun) {
    List<PdfBookParser.Paragraph> parsed;
    List<ImportReport.Chapter> chapters;
    List<String> warnings;
    if (uploads.stream().allMatch(u -> PdfText.isPdf(u.bytes()))) {
      List<PdfBookParser.Source> sources = new ArrayList<>();
      for (Upload u : uploads) {
        sources.add(new PdfBookParser.Source(u.name(), PdfText.pages(u.bytes())));
      }
      PdfBookParser.Parsed p = PdfBookParser.parse(sources);
      parsed = p.paragraphs();
      warnings = p.warnings();
      chapters =
          p.chapters().stream()
              .map(
                  c ->
                      new ImportReport.Chapter(
                          c.no(),
                          c.title(),
                          c.paragraphs(),
                          c.words(),
                          c.firstPage(),
                          c.lastPage()))
              .toList();
    } else if (uploads.size() == 1 && !PdfText.isPdf(uploads.get(0).bytes())) {
      BookParser.Parsed p =
          BookParser.parse(new String(uploads.get(0).bytes(), StandardCharsets.UTF_8));
      parsed = withSyntheticPages(p.paragraphs());
      warnings = p.warnings();
      chapters = new ArrayList<>();
      for (BookParser.Chapter c : p.chapters()) {
        String first = null;
        String last = null;
        for (PdfBookParser.Paragraph q : parsed) {
          if (q.chapterNo() == c.no()) {
            first = first == null ? q.pageLabel() : first;
            last = q.pageLabel();
          }
        }
        chapters.add(
            new ImportReport.Chapter(c.no(), c.title(), c.paragraphs(), c.words(), first, last));
      }
    } else {
      throw new BadRequestException("Upload the book's PDFs together, or one plain-text file.");
    }
    int words = parsed.stream().mapToInt(PdfBookParser.Paragraph::words).sum();
    int pages = parsed.isEmpty() ? 0 : parsed.get(parsed.size() - 1).pageSeq() + 1;
    String sample = sample(parsed.get(0).body());
    boolean cursorReset = false;
    if (!dryRun) {
      RecoverySettings settings = settings();
      Optional<RecoveryText> old = texts.findBySlot(TextSlot.BIG_BOOK);
      if (old.isPresent()) {
        int was = old.get().getParagraphCount();
        int now = parsed.size();
        if (Math.abs(was - now) > was * CURSOR_TOLERANCE || settings.getReadCursor() >= now) {
          settings.restartReading();
          cursorReset = was > 0;
          // Today's mark pointed into the old book; without it today owes the daily count again.
          days.findById(now()).ifPresent(RecoveryDay::clearRead);
        }
        texts.delete(old.get());
        texts.flush();
      }
      RecoveryText text =
          texts.save(new RecoveryText(TextSlot.BIG_BOOK, title, parsed.size(), words));
      List<RecoveryParagraph> rows = new ArrayList<>(parsed.size());
      for (PdfBookParser.Paragraph p : parsed) {
        rows.add(
            new RecoveryParagraph(
                text.getId(),
                p.chapterNo(),
                p.chapterTitle(),
                p.seq(),
                p.body(),
                p.words(),
                p.pageLabel(),
                p.pageSeq()));
      }
      paragraphs.saveAll(rows);
      int lost = reanchor(rows);
      if (lost > 0) {
        warnings = new ArrayList<>(warnings);
        warnings.add(
            lost
                + (lost == 1 ? " mark" : " marks")
                + " could not find their paragraph in the new text and kept their old place");
      }
    }
    return new ImportReport(
        dryRun,
        TextSlot.BIG_BOOK.wireValue(),
        title,
        parsed.size(),
        words,
        pages,
        chapters,
        0,
        0,
        List.of(),
        sample,
        warnings,
        cursorReset);
  }

  /** A plain-text book has no pages of its own, so it gets one every three hundred words. */
  static List<PdfBookParser.Paragraph> withSyntheticPages(List<BookParser.Paragraph> in) {
    List<PdfBookParser.Paragraph> out = new ArrayList<>(in.size());
    int words = 0;
    int page = 0;
    for (BookParser.Paragraph p : in) {
      if (words >= ReadingPlanner.WORDS_PER_PAGE) {
        page++;
        words = 0;
      }
      out.add(
          new PdfBookParser.Paragraph(
              p.chapterNo(),
              p.chapterTitle(),
              p.seq(),
              p.body(),
              p.words(),
              String.valueOf(page + 1),
              page));
      words += p.words();
    }
    return out;
  }

  private ImportReport importDaily(
      TextSlot slot, String title, List<Upload> uploads, boolean dryRun) {
    StringBuilder text = new StringBuilder();
    for (Upload u : uploads) {
      if (PdfText.isPdf(u.bytes())) {
        for (List<String> page : PdfText.pages(u.bytes())) {
          for (String line : page) {
            String t = PdfBookParser.clean(line);
            if (line.stripLeading().startsWith(String.valueOf(PdfText.PARAGRAPH))) {
              text.append('\n');
            }
            text.append(t).append('\n');
          }
        }
      } else {
        text.append(new String(u.bytes(), StandardCharsets.UTF_8)).append('\n');
      }
    }
    DailyParser.Parsed parsed = DailyParser.parse(text.toString());
    DailyParser.Entry first = parsed.entries().get(0);
    String sample = sample((first.title().isEmpty() ? "" : first.title() + " — ") + first.body());
    int words = parsed.entries().stream().mapToInt(e -> Blocks.words(e.body())).sum();
    if (!dryRun) {
      texts.findBySlot(slot).ifPresent(texts::delete);
      texts.flush();
      RecoveryText row = texts.save(new RecoveryText(slot, title, 0, words));
      List<RecoveryDailyEntry> rows = new ArrayList<>(parsed.entries().size());
      for (DailyParser.Entry e : parsed.entries()) {
        rows.add(new RecoveryDailyEntry(row.getId(), e.month(), e.day(), e.title(), e.body()));
      }
      entries.saveAll(rows);
    }
    return new ImportReport(
        dryRun,
        slot.wireValue(),
        title,
        0,
        words,
        0,
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

  // ---- the Bible ------------------------------------------------------------------------------

  /** Today's passage, moving the cursor on if the day has changed. Null when there is no Bible. */
  private BibleService.Passage passageToday(RecoverySettings settings, LocalDate today) {
    if (!bible.available()) {
      return null;
    }
    return bible.passageAt(passageIndex(settings, today));
  }

  private int passageIndex(RecoverySettings settings, LocalDate today) {
    return settings.biblePassage(
        today, bible.dateIndex(settings.getBiblePlanStart(), today), bible.planSize());
  }

  /** Another passage today: the next one becomes today's, and tomorrow's follows it. */
  @Transactional
  public Today nextPassage() {
    LocalDate today = now();
    RecoverySettings settings = settings();
    if (bible.available()) {
      settings.nextBiblePassage(
          today, bible.dateIndex(settings.getBiblePlanStart(), today), bible.planSize());
    }
    return today();
  }

  /** What today's passage means, written now if it has not been. */
  @Transactional
  public BibleService.Explanation explainPassage() {
    LocalDate today = now();
    return bible.explain(passageIndex(settings(), today));
  }

  /**
   * Writes today's explanation ahead of time, so it is waiting when the passage is read. Quiet: no
   * model, or a failed call, is a note that is not there yet, and the card's button is the retry.
   */
  @Transactional
  public void prepareExplanation() {
    if (!bible.available() || !bible.canExplain()) {
      return;
    }
    try {
      bible.explain(passageIndex(settings(), now()));
    } catch (Exception e) {
      log.warn("Could not write today's passage note ahead of time: {}", e.getMessage());
    }
  }

  // ---- the push line -------------------------------------------------------------------------

  /** The pieces of the day's readings, for the phone; empty when there is nothing to read. */
  @Transactional
  public List<String> readingsToday() {
    LocalDate today = now();
    List<String> pieces = new ArrayList<>();
    Daily reflection = daily(TextSlot.REFLECTION, today);
    if (reflection != null) {
      pieces.add(reflection.title().isEmpty() ? reflection.bookTitle() : reflection.title());
    }
    if (bible.available()) {
      pieces.add(bible.referenceAt(passageIndex(settings(), today)));
    }
    Reading reading = reading(settings(), days.findById(today).orElse(null), today);
    if (reading != null && !reading.doneToday()) {
      String pages =
          reading.pageFrom() == null
              ? reading.chapterTitle()
              : reading.pageFrom().equals(reading.pageTo())
                  ? "p. " + reading.pageFrom()
                  : "pp. " + reading.pageFrom() + "–" + reading.pageTo();
      pieces.add(reading.bookTitle() + " · " + pages);
    }
    return pieces;
  }

  /** The settings row, created with the defaults the first time anything asks for it. */
  private RecoverySettings settings() {
    return settingsRows
        .findById(RecoverySettings.THE_ROW)
        .orElseGet(() -> settingsRows.save(new RecoverySettings()));
  }
}
