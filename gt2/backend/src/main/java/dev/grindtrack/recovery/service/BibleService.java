package dev.grindtrack.recovery.service;

import dev.grindtrack.config.RecoveryProperties;
import dev.grindtrack.recovery.domain.BibleNote;
import dev.grindtrack.recovery.domain.BibleNoteRepository;
import dev.grindtrack.recovery.domain.BibleVerse;
import dev.grindtrack.recovery.domain.BibleVerseRepository;
import dev.grindtrack.web.ServiceOffException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The built-in Bible: the plan of daily passages, the books and chapters to read from anywhere, the
 * search, and what a passage means.
 *
 * <p>The plan is a list of references, not text, so it is small and is built once from the verse
 * table; the chapter counts come from the same pass. The verses of a passage or a chapter are read
 * from the table when asked for.
 */
@Service
public class BibleService {

  private static final Logger log = LoggerFactory.getLogger(BibleService.class);

  /** "John 3", "1 john 4:7", "psalm 23", "ps 23". */
  private static final Pattern REFERENCE =
      Pattern.compile(
          "^\\s*([1-3]?\\s*[A-Za-z][A-Za-z ]*?)\\s+(\\d{1,3})(?:\\s*:\\s*(\\d{1,3}))?\\s*$");

  private final BibleVerseRepository verses;
  private final BibleNoteRepository notes;
  private final PassageModel model;
  private final RecoveryProperties props;
  private volatile Built built;

  public BibleService(
      BibleVerseRepository verses,
      BibleNoteRepository notes,
      PassageModel model,
      RecoveryProperties props) {
    this.verses = verses;
    this.notes = notes;
    this.model = model;
    this.props = props;
  }

  /** The plan and the chapter count of every book, from one read of the table. */
  private record Built(List<BiblePlan.Passage> plan, Map<String, Integer> chapters) {}

  /**
   * A passage of the plan. {@code position} is one-based; {@code explanation} is the note kept for
   * it, or null until one is asked for.
   */
  public record Passage(
      String reference,
      String book,
      int chapter,
      String translation,
      int position,
      int planSize,
      List<Verse> verses,
      String explanation) {}

  public record Verse(int verse, boolean para, String text) {}

  public record Status(String name, String abbrev, long verses, int passages) {}

  public record Book(String code, String name, int chapters, String testament) {}

  /** Somewhere in the Bible: a chapter, and a verse in it when one was named. */
  public record Place(String book, String name, int chapter, Integer verse) {}

  public record Chapter(
      String book,
      String name,
      int chapter,
      int chapters,
      List<Verse> verses,
      Place prev,
      Place next) {}

  public record Hit(
      String book, String name, int chapter, int verse, String reference, String text) {}

  /** What a search found: a place when the query was a reference, else the verses. */
  public record Search(Place place, List<Hit> hits) {}

  public record Explanation(String reference, String body) {}

  /** True once the table has been seeded. */
  public boolean available() {
    return !plan().isEmpty();
  }

  public Status status() {
    List<BiblePlan.Passage> p = plan();
    return new Status(props.bible().name(), props.bible().abbrev(), verses.count(), p.size());
  }

  public int planSize() {
    return plan().size();
  }

  /**
   * Where the old date-counted plan would be today: day n of the plan is passage n, wrapping. The
   * cursor starts here the first time, so nobody's place jumps.
   */
  public int dateIndex(LocalDate planStart, LocalDate today) {
    List<BiblePlan.Passage> p = plan();
    if (p.isEmpty()) {
      return 0;
    }
    long day = Math.max(0, ChronoUnit.DAYS.between(planStart, today));
    return (int) (day % p.size());
  }

  /** The reference alone, for the push line. Null when there is no Bible. */
  public String referenceAt(int index) {
    List<BiblePlan.Passage> p = plan();
    return p.isEmpty() ? null : p.get(Math.floorMod(index, p.size())).reference();
  }

  /** Passage {@code index} of the plan, with its verses and its note if there is one. */
  public Passage passageAt(int index) {
    List<BiblePlan.Passage> p = plan();
    if (p.isEmpty()) {
      return null;
    }
    int i = Math.floorMod(index, p.size());
    BiblePlan.Passage ref = p.get(i);
    List<Verse> text =
        verses(
            verses.findByBookAndChapterAndVerseBetweenOrderByVerseAsc(
                ref.book(), ref.chapter(), ref.from(), ref.to()));
    String note = notes.findById(key(ref)).map(BibleNote::getBody).orElse(null);
    return new Passage(
        ref.reference(),
        ref.book(),
        ref.chapter(),
        props.bible().abbrev(),
        i + 1,
        p.size(),
        text,
        note);
  }

  // ---- the whole book ----------------------------------------------------------------------

  /** The sixty-six books in canonical order, with their chapter counts. */
  public List<Book> books() {
    Map<String, Integer> chapters = built().chapters();
    List<Book> out = new ArrayList<>();
    for (String code : BibleBooks.codes()) {
      Integer n = chapters.get(code);
      if (n != null) {
        out.add(
            new Book(code, BibleBooks.name(code), n, BibleBooks.ord(code) <= 39 ? "old" : "new"));
      }
    }
    return out;
  }

  /** One chapter, with where the previous and the next are — across books. */
  public Optional<Chapter> chapter(String code, int chapter) {
    Map<String, Integer> chapters = built().chapters();
    Integer count = chapters.get(code);
    if (count == null || chapter < 1 || chapter > count) {
      return Optional.empty();
    }
    List<Verse> text = verses(verses.findByBookAndChapterOrderByVerseAsc(code, chapter));
    return Optional.of(
        new Chapter(
            code,
            BibleBooks.name(code),
            chapter,
            count,
            text,
            neighbour(code, chapter, -1),
            neighbour(code, chapter, +1)));
  }

  /**
   * A reference opens the chapter; anything else searches the text. "John 3:16" is a place;
   * "shepherd" is verses.
   */
  public Search search(String q) {
    Place place = parse(q);
    if (place != null) {
      return new Search(place, List.of());
    }
    List<Hit> hits =
        verses.search(q).stream()
            .map(
                v ->
                    new Hit(
                        v.getBook(),
                        BibleBooks.name(v.getBook()),
                        v.getChapter(),
                        v.getVerse(),
                        BibleBooks.reference(
                            v.getBook(), v.getChapter(), v.getVerse(), v.getVerse(), false),
                        v.getText()))
            .toList();
    return new Search(null, hits);
  }

  // ---- what it means -----------------------------------------------------------------------

  /** Whether an explanation can be written: the assistant's key is the model's. */
  public boolean canExplain() {
    return model.configured();
  }

  /**
   * The note for passage {@code index}, written now if there is none.
   *
   * @throws ServiceOffException when there is no model
   */
  public Explanation explain(int index) {
    List<BiblePlan.Passage> p = plan();
    if (p.isEmpty()) {
      throw new java.util.NoSuchElementException("the Bible is not seeded");
    }
    if (!model.configured()) {
      throw new ServiceOffException(
          "the assistant is off — set ANTHROPIC_API_KEY on the deployment");
    }
    BiblePlan.Passage ref = p.get(Math.floorMod(index, p.size()));
    String key = key(ref);
    Optional<BibleNote> kept = notes.findById(key);
    if (kept.isPresent()) {
      return new Explanation(ref.reference(), kept.get().getBody());
    }
    String text =
        verses
            .findByBookAndChapterAndVerseBetweenOrderByVerseAsc(
                ref.book(), ref.chapter(), ref.from(), ref.to())
            .stream()
            .map(v -> v.getVerse() + " " + v.getText().replace('\n', ' '))
            .reduce((a, b) -> a + "\n" + b)
            .orElse("");
    PassageModel.Explained answer = model.explain(ref.reference(), props.bible().name(), text);
    notes.save(new BibleNote(key, answer.body(), answer.model()));
    log.info(
        "Explained {} ({} in, {} out)",
        ref.reference(),
        answer.inputTokens(),
        answer.outputTokens());
    return new Explanation(ref.reference(), answer.body());
  }

  /** Drops the built plan, so the next request rebuilds it — after the seed has run. */
  public void forget() {
    built = null;
  }

  // ---- internals ---------------------------------------------------------------------------

  static String key(BiblePlan.Passage ref) {
    return ref.book() + " " + ref.chapter() + ":" + ref.from() + "-" + ref.to();
  }

  private static List<Verse> verses(List<BibleVerse> rows) {
    return rows.stream().map(v -> new Verse(v.getVerse(), v.isPara(), v.getText())).toList();
  }

  private Place neighbour(String code, int chapter, int step) {
    Map<String, Integer> chapters = built().chapters();
    int next = chapter + step;
    if (next >= 1 && next <= chapters.get(code)) {
      return new Place(code, BibleBooks.name(code), next, null);
    }
    List<String> codes = BibleBooks.codes().stream().filter(chapters::containsKey).toList();
    int at = codes.indexOf(code) + step;
    if (at < 0 || at >= codes.size()) {
      return null;
    }
    String other = codes.get(at);
    return new Place(other, BibleBooks.name(other), step < 0 ? chapters.get(other) : 1, null);
  }

  /** "John 3:16" → a place; anything that is not a reference to a chapter that exists → null. */
  Place parse(String q) {
    Matcher m = REFERENCE.matcher(q == null ? "" : q);
    if (!m.matches()) {
      return null;
    }
    String name = m.group(1).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    String code = bookCode(name);
    if (code == null) {
      return null;
    }
    int chapter = Integer.parseInt(m.group(2));
    Integer count = built().chapters().get(code);
    if (count == null || chapter < 1 || chapter > count) {
      return null;
    }
    Integer verse = m.group(3) == null ? null : Integer.parseInt(m.group(3));
    return new Place(code, BibleBooks.name(code), chapter, verse);
  }

  /** A book by its name, or by "psalm", or by the first three or more letters of its name. */
  private static String bookCode(String typed) {
    if (typed.equals("psalm") || typed.equals("psalms") || typed.equals("ps")) {
      return "PSA";
    }
    for (String code : BibleBooks.codes()) {
      if (BibleBooks.name(code).toLowerCase(Locale.ROOT).equals(typed)) {
        return code;
      }
    }
    String letters = typed.replaceAll("[^a-z0-9]", "");
    if (letters.replaceAll("[0-9]", "").length() < 3) {
      return null;
    }
    for (String code : BibleBooks.codes()) {
      if (BibleBooks.name(code)
          .toLowerCase(Locale.ROOT)
          .replaceAll("[^a-z0-9]", "")
          .startsWith(letters)) {
        return code;
      }
    }
    return null;
  }

  private List<BiblePlan.Passage> plan() {
    return built().plan();
  }

  private Built built() {
    Built b = built;
    if (b == null) {
      List<BibleVerse> all = verses.findAllByOrderByBookOrdAscChapterAscVerseAsc();
      List<String> order =
          props.bible() == null || props.bible().books() == null
              ? List.of()
              : props.bible().books();
      Map<String, Integer> chapters = new LinkedHashMap<>();
      for (BibleVerse v : all) {
        chapters.merge(v.getBook(), v.getChapter(), Math::max);
      }
      b = new Built(BiblePlan.build(all, order), Map.copyOf(chapters));
      built = b;
    }
    return b;
  }
}
