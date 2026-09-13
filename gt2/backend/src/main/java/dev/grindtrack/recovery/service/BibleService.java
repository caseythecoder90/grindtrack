package dev.grindtrack.recovery.service;

import dev.grindtrack.config.RecoveryProperties;
import dev.grindtrack.recovery.domain.BibleVerse;
import dev.grindtrack.recovery.domain.BibleVerseRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * The built-in Bible: the plan, held in memory once built, and the day's passage from it.
 *
 * <p>The plan is a list of references, not text, so it is small; the verses of one passage are read
 * from the table when asked for.
 */
@Service
public class BibleService {

  private final BibleVerseRepository verses;
  private final RecoveryProperties props;
  private volatile List<BiblePlan.Passage> plan;

  public BibleService(BibleVerseRepository verses, RecoveryProperties props) {
    this.verses = verses;
    this.props = props;
  }

  public record Passage(
      String reference, String translation, int dayInPlan, int planSize, List<Verse> verses) {}

  public record Verse(int verse, boolean para, String text) {}

  public record Status(String name, String abbrev, long verses, int passages) {}

  /** True once the table has been seeded. */
  public boolean available() {
    return !plan().isEmpty();
  }

  public Status status() {
    List<BiblePlan.Passage> p = plan();
    return new Status(props.bible().name(), props.bible().abbrev(), verses.count(), p.size());
  }

  /** The reference alone, for the push line. Null when there is no Bible. */
  public String referenceFor(LocalDate planStart, LocalDate today) {
    List<BiblePlan.Passage> p = plan();
    return p.isEmpty() ? null : p.get(index(p, planStart, today)).reference();
  }

  /** Day n of the plan is passage n; past the end it starts again. Null when there is no Bible. */
  public Passage passageFor(LocalDate planStart, LocalDate today) {
    List<BiblePlan.Passage> p = plan();
    if (p.isEmpty()) {
      return null;
    }
    int i = index(p, planStart, today);
    BiblePlan.Passage ref = p.get(i);
    List<Verse> text =
        verses
            .findByBookAndChapterAndVerseBetweenOrderByVerseAsc(
                ref.book(), ref.chapter(), ref.from(), ref.to())
            .stream()
            .map(v -> new Verse(v.getVerse(), v.isPara(), v.getText()))
            .toList();
    return new Passage(ref.reference(), props.bible().abbrev(), i + 1, p.size(), text);
  }

  private static int index(List<BiblePlan.Passage> plan, LocalDate planStart, LocalDate today) {
    long day = ChronoUnit.DAYS.between(planStart, today);
    if (day < 0) {
      day = 0;
    }
    return (int) (day % plan.size());
  }

  /** Drops the built plan, so the next request rebuilds it — after the seed has run. */
  public void forget() {
    plan = null;
  }

  private List<BiblePlan.Passage> plan() {
    List<BiblePlan.Passage> built = plan;
    if (built == null) {
      List<BibleVerse> all = verses.findAllByOrderByBookOrdAscChapterAscVerseAsc();
      List<String> order =
          props.bible() == null || props.bible().books() == null
              ? List.of()
              : props.bible().books();
      built = BiblePlan.build(all, order);
      plan = built;
    }
    return built;
  }
}
