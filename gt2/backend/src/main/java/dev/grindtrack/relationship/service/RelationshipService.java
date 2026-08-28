package dev.grindtrack.relationship.service;

import dev.grindtrack.relationship.domain.Effort;
import dev.grindtrack.relationship.domain.Idea;
import dev.grindtrack.relationship.domain.IdeaKind;
import dev.grindtrack.relationship.domain.IdeaRepository;
import dev.grindtrack.relationship.domain.IdeaStatus;
import dev.grindtrack.relationship.domain.Moment;
import dev.grindtrack.relationship.domain.MomentKind;
import dev.grindtrack.relationship.domain.MomentRepository;
import dev.grindtrack.relationship.domain.Occasion;
import dev.grindtrack.relationship.domain.OccasionRepository;
import dev.grindtrack.relationship.domain.Reading;
import dev.grindtrack.relationship.domain.ReadingKind;
import dev.grindtrack.relationship.domain.ReadingRepository;
import dev.grindtrack.relationship.domain.ReadingStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The relationship tab.
 *
 * <p>One rule governs everything here: <strong>this reassures, it does not score.</strong> There
 * are no streaks, no targets, no goals and no relationship health number anywhere in this class,
 * and there should never be. The recency figures exist so that on an evening when it feels like
 * nothing has happened lately, you can check — and the common case is that something did.
 *
 * <p>The one place that principle turns into code is {@link #perspectiveOn}. Its whole job is to
 * turn a gap in days into a sentence a person reads before they have finished doing the arithmetic
 * themselves, because the arithmetic done in a bad mood is what this is correcting for.
 */
@Service
public class RelationshipService {

  /**
   * How far back a personal baseline is drawn from.
   *
   * <p>Six months smooths out a busy fortnight and a quiet one. Crucially the baseline is always
   * <em>your own</em> history — there is no external norm in this file, because a comparison
   * against anyone else's average is a verdict rather than context.
   */
  private static final int BASELINE_DAYS = 180;

  /** A month, for the "how many times lately" figure. */
  private static final int RECENT_WINDOW_DAYS = 30;

  /** Within this many days, the answer is simply "recently" and no further framing is needed. */
  private static final int RECENT_DAYS = 3;

  /** Beyond this, the useful response is a suggestion rather than a number. */
  private static final int SUGGEST_AFTER_DAYS = 14;

  /** How close to your own baseline still counts as a normal stretch rather than a quiet one. */
  private static final double NORMAL_BAND = 0.75;

  private final MomentRepository moments;
  private final IdeaRepository ideas;
  private final OccasionRepository occasions;
  private final ReadingRepository reading;

  public RelationshipService(
      MomentRepository moments,
      IdeaRepository ideas,
      OccasionRepository occasions,
      ReadingRepository reading) {
    this.moments = moments;
    this.ideas = ideas;
    this.occasions = occasions;
    this.reading = reading;
  }

  // ------------------------------------------------------------------ views

  /**
   * @param daysSince null when nothing of this kind has ever been logged, which is a blank rather
   *     than a zero — "never" and "today" must not look alike
   */
  public record Recency(String kind, String lastOn, Long daysSince, String note) {}

  /**
   * @param tone CALM, NEUTRAL or SUGGEST. Never a warning, and never a failure — the frontend has
   *     no red state for this and must not acquire one
   */
  public record Perspective(String headline, String detail, String tone) {}

  /**
   * What the intimacy card shows: the literal recent dates, a count against your own baseline, and
   * a sentence.
   *
   * @param recentDates the last few, as dates rather than as a rate. This is the thing that
   *     actually settles the question, so it comes first
   * @param typicalPerMonth your own trailing average, the only baseline used anywhere
   */
  public record Closeness(
      List<String> recentDates,
      Long daysSince,
      long lastThirtyDays,
      Integer typicalPerMonth,
      Perspective perspective) {}

  public record Upcoming(
      Long id, String label, String on, long daysAway, Integer years, int ideaCount, String note) {}

  public record Summary(
      List<Recency> recency,
      Closeness closeness,
      List<Upcoming> upcoming,
      List<IdeaView> readyIdeas,
      List<MomentView> lately) {}

  public record MomentView(
      Long id, String occurredOn, String kind, String note, Short feltClose, boolean isPrivate) {}

  public record IdeaView(
      Long id,
      String kind,
      String title,
      String detail,
      String occasion,
      BigDecimal estCost,
      String effort,
      String status) {}

  public record ReadingView(
      Long id,
      String title,
      String url,
      String source,
      String kind,
      String status,
      String takeaway,
      String readOn) {}

  public Summary summary() {
    LocalDate today = LocalDate.now();

    List<Recency> recency = new ArrayList<>();
    for (MomentKind kind : MomentKind.recencyOrder()) {
      Optional<Moment> last = moments.findFirstByKindOrderByOccurredOnDescIdDesc(kind);
      recency.add(
          new Recency(
              kind.name(),
              last.map(m -> m.getOccurredOn().toString()).orElse(null),
              last.map(m -> ChronoUnit.DAYS.between(m.getOccurredOn(), today)).orElse(null),
              last.map(Moment::getNote).orElse(null)));
    }

    List<IdeaView> ready =
        ideas.findByStatusNotOrderByIdDesc(IdeaStatus.DONE).stream()
            // Least effort first: on an ordinary evening the deciding factor is how much
            // something takes, not how good the idea is.
            .sorted(Comparator.comparingInt(RelationshipService::effortRank))
            .limit(8)
            .map(RelationshipService::toIdeaView)
            .toList();

    return new Summary(
        recency,
        closeness(today),
        upcoming(today),
        ready,
        moments.findAllByOrderByOccurredOnDescIdDesc().stream()
            .limit(12)
            .map(RelationshipService::toMomentView)
            .toList());
  }

  /**
   * The card this feature was really asked for.
   *
   * <p>Three things and nothing else: when it last happened as plain dates, how many times in the
   * last month against your own baseline, and a sentence. No chart, no rate per week, no target.
   */
  public Closeness closeness(LocalDate today) {
    List<Moment> recent =
        moments.findByKindOrderByOccurredOnDescIdDesc(MomentKind.INTIMACY, Limit.of(5));
    List<String> dates = recent.stream().map(m -> m.getOccurredOn().toString()).toList();

    Long daysSince =
        recent.isEmpty() ? null : ChronoUnit.DAYS.between(recent.get(0).getOccurredOn(), today);

    long lastThirty =
        moments.countByKindAndOccurredOnBetween(
            MomentKind.INTIMACY, today.minusDays(RECENT_WINDOW_DAYS), today);

    return new Closeness(
        dates,
        daysSince,
        lastThirty,
        typicalPerMonth(today),
        perspectiveOn(daysSince, lastThirty, typicalPerMonth(today)));
  }

  /**
   * Your own trailing average, per month.
   *
   * <p>Measured over however much history actually exists, capped at six months, so a tab that is
   * two weeks old does not report a baseline of nearly zero and then describe every subsequent week
   * as busy. Returns null until there is enough history to say anything honest.
   */
  Integer typicalPerMonth(LocalDate today) {
    Optional<Moment> earliest =
        moments.findByKindOrderByOccurredOnDescIdDesc(MomentKind.INTIMACY, Limit.of(500)).stream()
            .min(Comparator.comparing(Moment::getOccurredOn));
    if (earliest.isEmpty()) {
      return null;
    }
    long span = ChronoUnit.DAYS.between(earliest.get().getOccurredOn(), today);
    if (span < 45) {
      // Not enough history for an average to mean anything. Better to show no baseline than a
      // made-up one that later makes a normal month look like a decline.
      return null;
    }
    long window = Math.min(span, BASELINE_DAYS);
    long count =
        moments.countByKindAndOccurredOnBetween(
            MomentKind.INTIMACY, today.minusDays(window), today);
    return (int) Math.round(count * 30.0 / window);
  }

  /**
   * Turns a gap in days into something worth reading.
   *
   * <p>This is the feature. The same number reassures or winds you up depending entirely on how it
   * is put, and the number arrives at exactly the moment you are least inclined to be fair about
   * it. So: recent is stated plainly and early, a middling gap is given the context of the month
   * around it, and a genuinely long one gets a suggestion rather than a statistic — because at that
   * point what helps is a plan, not a measurement.
   *
   * <p>There is no branch here that produces a criticism, and none should ever be added.
   */
  Perspective perspectiveOn(Long daysSince, long lastThirty, Integer typicalPerMonth) {
    if (daysSince == null) {
      return new Perspective(
          "Nothing logged yet.",
          "Once there are a few entries, this will tell you when the last time was without you"
              + " having to work it out.",
          "NEUTRAL");
    }

    String when = phrase(daysSince);
    String monthly = timesInLastMonth(lastThirty);

    if (daysSince <= RECENT_DAYS) {
      return new Perspective(
          capitalize(when) + ".",
          "That is recent. " + monthly + comparison(lastThirty, typicalPerMonth),
          "CALM");
    }

    if (daysSince <= 7) {
      return new Perspective(
          "Within the last week — " + when + ".",
          monthly + comparison(lastThirty, typicalPerMonth),
          "CALM");
    }

    if (daysSince <= SUGGEST_AFTER_DAYS) {
      boolean normalMonth =
          typicalPerMonth != null && lastThirty >= Math.floor(typicalPerMonth * NORMAL_BAND);
      return new Perspective(
          capitalize(when) + ".",
          monthly + comparison(lastThirty, typicalPerMonth),
          normalMonth ? "CALM" : "NEUTRAL");
    }

    return new Perspective(
        capitalize(when) + ".",
        "Might be a good week to plan something. There are ideas below.",
        "SUGGEST");
  }

  /** Compares you against yourself, and only ever against yourself. */
  private static String comparison(long lastThirty, Integer typicalPerMonth) {
    if (typicalPerMonth == null || typicalPerMonth == 0) {
      return "";
    }
    if (lastThirty >= typicalPerMonth) {
      return " Your usual is about " + typicalPerMonth + " — so, a normal month or better.";
    }
    if (lastThirty >= Math.floor(typicalPerMonth * NORMAL_BAND)) {
      return " Your usual is about " + typicalPerMonth + " — so, about normal.";
    }
    return " Your usual is about " + typicalPerMonth + ", so this month has been quieter.";
  }

  private static String timesInLastMonth(long count) {
    if (count == 0) {
      return "Nothing in the last 30 days.";
    }
    return count + (count == 1 ? " time" : " times") + " in the last 30 days.";
  }

  private static String phrase(long days) {
    if (days <= 0) {
      return "today";
    }
    if (days == 1) {
      return "yesterday";
    }
    return days + " days ago";
  }

  private static String capitalize(String value) {
    return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
  }

  /** Occasions inside their lead window, soonest first, each with how many ideas are waiting. */
  public List<Upcoming> upcoming(LocalDate today) {
    List<Upcoming> out = new ArrayList<>();
    for (Occasion occasion : occasions.findAllByOrderByLabelAsc()) {
      LocalDate next = occasion.nextOccurrence(today);
      long away = ChronoUnit.DAYS.between(today, next);
      if (away < 0 || away > occasion.getLeadDays()) {
        continue;
      }
      int ideaCount =
          ideas.findByStatusNotAndOccasionIgnoreCase(IdeaStatus.DONE, occasion.getLabel()).size();
      out.add(
          new Upcoming(
              occasion.getId(),
              occasion.getLabel(),
              next.toString(),
              away,
              occasion.yearsAt(next),
              ideaCount,
              occasion.getNote()));
    }
    out.sort(Comparator.comparingLong(Upcoming::daysAway));
    return out;
  }

  /** Every occasion with its next date, for the management list rather than the summary. */
  public List<Upcoming> allOccasions(LocalDate today) {
    return occasions.findAllByOrderByLabelAsc().stream()
        .map(
            o -> {
              LocalDate next = o.nextOccurrence(today);
              return new Upcoming(
                  o.getId(),
                  o.getLabel(),
                  next.toString(),
                  ChronoUnit.DAYS.between(today, next),
                  o.yearsAt(next),
                  ideas.findByStatusNotAndOccasionIgnoreCase(IdeaStatus.DONE, o.getLabel()).size(),
                  o.getNote());
            })
        .sorted(Comparator.comparingLong(Upcoming::daysAway))
        .toList();
  }

  // --------------------------------------------------------------- moments

  public List<MomentView> timeline(int limit) {
    return moments.findAllByOrderByOccurredOnDescIdDesc().stream()
        .limit(Math.max(1, limit))
        .map(RelationshipService::toMomentView)
        .toList();
  }

  @Transactional
  public Moment log(LocalDate occurredOn, MomentKind kind, String note, Short feltClose) {
    if (kind == null) {
      throw new IllegalArgumentException("a kind is required");
    }
    LocalDate when = occurredOn == null ? LocalDate.now() : occurredOn;
    if (when.isAfter(LocalDate.now())) {
      throw new IllegalArgumentException("that date is in the future");
    }
    Moment moment = new Moment(when, kind);
    moment.update(when, kind, note, feltClose);
    return moments.save(moment);
  }

  @Transactional
  public Moment updateMoment(
      Long id, LocalDate occurredOn, MomentKind kind, String note, Short feltClose) {
    Moment moment =
        moments.findById(id).orElseThrow(() -> new NoSuchElementException("moment " + id));
    moment.update(occurredOn == null ? moment.getOccurredOn() : occurredOn, kind, note, feltClose);
    return moments.save(moment);
  }

  @Transactional
  public void deleteMoment(Long id) {
    moments.deleteById(id);
  }

  // ----------------------------------------------------------------- ideas

  public List<IdeaView> listIdeas(boolean includeDone) {
    List<Idea> found =
        includeDone
            ? ideas.findAllByOrderByIdDesc()
            : ideas.findByStatusNotOrderByIdDesc(IdeaStatus.DONE);
    return found.stream()
        .sorted(Comparator.comparingInt(RelationshipService::effortRank))
        .map(RelationshipService::toIdeaView)
        .toList();
  }

  @Transactional
  public Idea createIdea(
      IdeaKind kind,
      String title,
      String detail,
      String occasion,
      BigDecimal estCost,
      Effort effort) {
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("an idea needs a title");
    }
    Idea idea = new Idea(kind, title);
    idea.update(kind, title, detail, occasion, estCost, effort, IdeaStatus.IDEA);
    return ideas.save(idea);
  }

  @Transactional
  public Idea updateIdea(
      Long id,
      IdeaKind kind,
      String title,
      String detail,
      String occasion,
      BigDecimal estCost,
      Effort effort,
      IdeaStatus status) {
    Idea idea = ideas.findById(id).orElseThrow(() -> new NoSuchElementException("idea " + id));
    idea.update(kind, title, detail, occasion, estCost, effort, status);
    return ideas.save(idea);
  }

  /**
   * Marks an idea done by turning it into a moment.
   *
   * <p>The loop that keeps the list alive: an idea you acted on leaves the list and joins the
   * timeline, so what is left is genuinely still pending rather than a graveyard of things already
   * given.
   */
  @Transactional
  public Moment completeIdea(Long id, LocalDate on) {
    Idea idea = ideas.findById(id).orElseThrow(() -> new NoSuchElementException("idea " + id));
    Moment moment = log(on, momentKindFor(idea.getKind()), idea.getTitle(), null);
    idea.completedAs(moment.getId());
    ideas.save(idea);
    return moment;
  }

  @Transactional
  public void deleteIdea(Long id) {
    ideas.deleteById(id);
  }

  private static MomentKind momentKindFor(IdeaKind kind) {
    return switch (kind) {
      case GIFT -> MomentKind.GIFT_GIVEN;
      case DATE -> MomentKind.DATE_NIGHT;
      case GESTURE -> MomentKind.GESTURE;
    };
  }

  /** Least effort first. SMALL ideas are the ones that actually get done on a Tuesday. */
  private static int effortRank(Idea idea) {
    if (idea.getEffort() == null) {
      return 2;
    }
    return switch (idea.getEffort()) {
      case SMALL -> 0;
      case MEDIUM -> 2;
      case BIG -> 3;
    };
  }

  // ------------------------------------------------------------- occasions

  @Transactional
  public Occasion createOccasion(
      String label, LocalDate date, boolean recurring, int leadDays, String note) {
    if (label == null || label.isBlank()) {
      throw new IllegalArgumentException("an occasion needs a name");
    }
    if (date == null) {
      throw new IllegalArgumentException("an occasion needs a date");
    }
    Occasion occasion = new Occasion(label, date);
    occasion.update(label, date, recurring, leadDays, note);
    return occasions.save(occasion);
  }

  @Transactional
  public Occasion updateOccasion(
      Long id, String label, LocalDate date, boolean recurring, int leadDays, String note) {
    Occasion occasion =
        occasions.findById(id).orElseThrow(() -> new NoSuchElementException("occasion " + id));
    occasion.update(label, date, recurring, leadDays, note);
    return occasions.save(occasion);
  }

  @Transactional
  public void deleteOccasion(Long id) {
    occasions.deleteById(id);
  }

  // --------------------------------------------------------------- reading

  public List<ReadingView> listReading() {
    return reading.findAllByOrderByStatusAscIdDesc().stream()
        .map(RelationshipService::toReadingView)
        .toList();
  }

  @Transactional
  public Reading addReading(String title, String url, String source, ReadingKind kind) {
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("a title is required");
    }
    Reading item = new Reading(title, kind);
    item.update(title, url, source, kind);
    return reading.save(item);
  }

  @Transactional
  public Reading markRead(Long id, String takeaway, LocalDate readOn) {
    Reading item =
        reading.findById(id).orElseThrow(() -> new NoSuchElementException("reading " + id));
    item.markRead(takeaway, readOn);
    return reading.save(item);
  }

  /**
   * Turns a takeaway into something you will actually do.
   *
   * <p>The reason the reading list has a takeaway field at all: a thought that stays a thought
   * changes nothing, and a gesture idea gets surfaced on an ordinary evening.
   */
  @Transactional
  public Idea promoteTakeaway(Long id, String title, Effort effort) {
    Reading item =
        reading.findById(id).orElseThrow(() -> new NoSuchElementException("reading " + id));
    String ideaTitle = title == null || title.isBlank() ? item.getTakeaway() : title;
    if (ideaTitle == null || ideaTitle.isBlank()) {
      throw new IllegalArgumentException(
          "write the takeaway first — the idea is what you would do differently");
    }
    return createIdea(IdeaKind.GESTURE, ideaTitle, "From: " + item.getTitle(), null, null, effort);
  }

  @Transactional
  public void deleteReading(Long id) {
    reading.deleteById(id);
  }

  // ---------------------------------------------------------------- mapping

  private static MomentView toMomentView(Moment m) {
    return new MomentView(
        m.getId(),
        m.getOccurredOn().toString(),
        m.getKind().name(),
        m.getNote(),
        m.getFeltClose(),
        m.getKind().isPrivate());
  }

  private static IdeaView toIdeaView(Idea i) {
    return new IdeaView(
        i.getId(),
        i.getKind().name(),
        i.getTitle(),
        i.getDetail(),
        i.getOccasion(),
        i.getEstCost(),
        i.getEffort() == null ? null : i.getEffort().name(),
        i.getStatus().name());
  }

  private static ReadingView toReadingView(Reading r) {
    return new ReadingView(
        r.getId(),
        r.getTitle(),
        r.getUrl(),
        r.getSource(),
        r.getKind().name(),
        r.getStatus().name(),
        r.getTakeaway(),
        r.getReadOn() == null ? null : r.getReadOn().toString());
  }

  /** Exposed for the controller so a blank status is a filter rather than an error. */
  public List<ReadingView> listReading(ReadingStatus status) {
    return status == null
        ? listReading()
        : reading.findByStatusOrderByIdDesc(status).stream()
            .map(RelationshipService::toReadingView)
            .toList();
  }
}
