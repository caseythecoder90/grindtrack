package dev.grindtrack.relationship.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import dev.grindtrack.relationship.service.RelationshipSummary.Perspective;
import dev.grindtrack.relationship.service.RelationshipSummary.Upcoming;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * The relationship tab, and above all the one rule it must never break: it reassures, it does not
 * score. Several tests here exist purely to fail if somebody later adds a judgement to a branch
 * that should not have one.
 */
class RelationshipServiceTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 8, 28);

  private MomentRepository moments;
  private IdeaRepository ideas;
  private OccasionRepository occasions;
  private ReadingRepository reading;
  private RelationshipService service;

  @BeforeEach
  void setUp() {
    moments = mock(MomentRepository.class);
    ideas = mock(IdeaRepository.class);
    occasions = mock(OccasionRepository.class);
    reading = mock(ReadingRepository.class);
    service = new RelationshipService(moments, ideas, occasions, reading);

    when(moments.save(any(Moment.class))).thenAnswer(i -> i.getArgument(0));
    when(moments.findAllByOrderByOccurredOnDescIdDesc()).thenReturn(List.of());
    when(moments.findByKindOrderByOccurredOnDescIdDesc(any(), any())).thenReturn(List.of());
    when(moments.findFirstByKindOrderByOccurredOnDescIdDesc(any())).thenReturn(Optional.empty());
    when(ideas.save(any(Idea.class))).thenAnswer(i -> i.getArgument(0));
    when(ideas.findByStatusNotOrderByIdDesc(any())).thenReturn(List.of());
    when(ideas.findByStatusNotAndOccasionIgnoreCase(any(), anyString())).thenReturn(List.of());
    when(occasions.findAllByOrderByLabelAsc()).thenReturn(List.of());
    when(occasions.save(any(Occasion.class))).thenAnswer(i -> i.getArgument(0));
    when(reading.save(any(Reading.class))).thenAnswer(i -> i.getArgument(0));
  }

  // ---------- the sentence the whole feature exists to produce ----------

  @Test
  void aRecentGapIsStatedPlainlyAndCalmly() {
    Perspective p = service.perspectiveOn(2L, 5, 6);

    assertThat(p.headline()).isEqualTo("2 days ago.");
    assertThat(p.detail()).contains("That is recent");
    assertThat(p.tone()).isEqualTo("CALM");
  }

  @Test
  void todayAndYesterdayReadAsWordsNotAsNumbers() {
    assertThat(service.perspectiveOn(0L, 4, 5).headline()).isEqualTo("Today.");
    assertThat(service.perspectiveOn(1L, 4, 5).headline()).isEqualTo("Yesterday.");
  }

  @Test
  void aMiddlingGapInANormalMonthIsStillCalm() {
    // Ten days can feel like ages in a bad mood. Five times in the month says otherwise, and that
    // is the correction this feature is for.
    Perspective p = service.perspectiveOn(10L, 5, 6);

    assertThat(p.detail()).contains("5 times in the last 30 days");
    assertThat(p.detail()).contains("about normal");
    assertThat(p.tone()).isEqualTo("CALM");
  }

  @Test
  void aQuietMonthIsDescribedWithoutBeingCriticized() {
    Perspective p = service.perspectiveOn(12L, 1, 8);

    assertThat(p.detail()).contains("quieter");
    assertThat(p.tone()).isEqualTo("NEUTRAL");
    // The words that would turn this into a verdict.
    assertThat(p.detail()).doesNotContainIgnoringCase("should");
    assertThat(p.detail()).doesNotContainIgnoringCase("target");
    assertThat(p.detail()).doesNotContainIgnoringCase("behind");
    assertThat(p.detail()).doesNotContainIgnoringCase("only");
  }

  @Test
  void aLongGapOffersAPlanRatherThanAStatistic() {
    // Past a couple of weeks, another number does not help. Something to do does.
    Perspective p = service.perspectiveOn(30L, 0, 6);

    assertThat(p.headline()).isEqualTo("30 days ago.");
    assertThat(p.detail()).contains("plan something");
    assertThat(p.tone()).isEqualTo("SUGGEST");
  }

  @Test
  void anEmptyHistorySaysSoWithoutImplyingAnythingIsWrong() {
    Perspective p = service.perspectiveOn(null, 0, null);

    assertThat(p.tone()).isEqualTo("NEUTRAL");
    assertThat(p.headline()).isEqualTo("Nothing logged yet.");
  }

  @Test
  void noToneIsEverAWarning() {
    // The frontend has no red state for this card, and this is the test that keeps it that way.
    List<Perspective> all =
        List.of(
            service.perspectiveOn(null, 0, null),
            service.perspectiveOn(0L, 6, 6),
            service.perspectiveOn(5L, 4, 6),
            service.perspectiveOn(12L, 1, 8),
            service.perspectiveOn(40L, 0, 6));

    assertThat(all).allMatch(p -> List.of("CALM", "NEUTRAL", "SUGGEST").contains(p.tone()));
  }

  @Test
  void withNoBaselineTheSentenceMakesNoComparison() {
    // Before there is enough history, comparing against a made-up average would invent a verdict.
    Perspective p = service.perspectiveOn(4L, 2, null);

    assertThat(p.detail()).contains("2 times in the last 30 days");
    assertThat(p.detail()).doesNotContain("usual");
  }

  // ---------- the baseline is always your own, and honest about itself ----------

  @Test
  void thereIsNoBaselineUntilThereIsEnoughHistoryForOne() {
    when(moments.findByKindOrderByOccurredOnDescIdDesc(eq(MomentKind.INTIMACY), any()))
        .thenReturn(List.of(moment(TODAY.minusDays(3)), moment(TODAY.minusDays(20))));

    assertThat(service.typicalPerMonth(TODAY)).isNull();
  }

  @Test
  void theBaselineIsDrawnFromHowMuchHistoryActuallyExists() {
    when(moments.findByKindOrderByOccurredOnDescIdDesc(eq(MomentKind.INTIMACY), any()))
        .thenReturn(List.of(moment(TODAY.minusDays(2)), moment(TODAY.minusDays(120))));
    when(moments.countByKindAndOccurredOnBetween(eq(MomentKind.INTIMACY), any(), any()))
        .thenReturn(24L);

    // 24 over 120 days is 6 a month.
    assertThat(service.typicalPerMonth(TODAY)).isEqualTo(6);
  }

  // ---------- occasions ----------

  @Test
  void aRecurringOccasionRollsForwardToItsNextTime() {
    Occasion anniversary = new Occasion("Anniversary", LocalDate.of(2022, 10, 2));

    LocalDate next = anniversary.nextOccurrence(TODAY);

    assertThat(next).isEqualTo(LocalDate.of(2026, 10, 2));
    assertThat(anniversary.yearsAt(next)).isEqualTo(4);
  }

  @Test
  void anOccasionAlreadyPassedThisYearRollsToNextYear() {
    Occasion birthday = new Occasion("Her birthday", LocalDate.of(1992, 3, 14));
    assertThat(birthday.nextOccurrence(TODAY)).isEqualTo(LocalDate.of(2027, 3, 14));
  }

  @Test
  void februaryTwentyNinthFallsBackRatherThanBeingSkipped() {
    Occasion leapling = new Occasion("Something", LocalDate.of(2024, 2, 29));
    assertThat(leapling.nextOccurrence(LocalDate.of(2027, 1, 1)))
        .isEqualTo(LocalDate.of(2027, 2, 28));
  }

  @Test
  void onlyOccasionsInsideTheirLeadWindowAreSurfaced() {
    // An anniversary you cannot act on yet is noise; one you have missed is worse than noise.
    Occasion soon = new Occasion("Anniversary", LocalDate.of(2022, 9, 10));
    soon.update("Anniversary", LocalDate.of(2022, 9, 10), true, 21, "");
    Occasion distant = new Occasion("Her birthday", LocalDate.of(1992, 3, 14));
    distant.update("Her birthday", LocalDate.of(1992, 3, 14), true, 21, "");
    when(occasions.findAllByOrderByLabelAsc()).thenReturn(List.of(soon, distant));

    List<Upcoming> upcoming = service.upcoming(TODAY);

    assertThat(upcoming).hasSize(1);
    assertThat(upcoming.get(0).label()).isEqualTo("Anniversary");
    assertThat(upcoming.get(0).daysAway()).isEqualTo(13);
  }

  // ---------- ideas ----------

  @Test
  void theEasiestIdeasComeFirst() {
    // On an ordinary evening the deciding factor is effort, not how good the idea is.
    when(ideas.findByStatusNotOrderByIdDesc(IdeaStatus.DONE))
        .thenReturn(
            List.of(
                idea("Weekend away", Effort.BIG),
                idea("Note in her bag", Effort.SMALL),
                idea("Book the pottery class", Effort.MEDIUM)));

    assertThat(service.listIdeas(false))
        .extracting(Idea::getTitle)
        .containsExactly("Note in her bag", "Book the pottery class", "Weekend away");
  }

  @Test
  void actingOnAnIdeaTurnsItIntoAMomentAndTakesItOffTheList() {
    Idea gift = idea("Framed print from Denver", Effort.MEDIUM);
    gift.update(
        IdeaKind.GIFT, "Framed print from Denver", "", null, null, Effort.MEDIUM, IdeaStatus.IDEA);
    when(ideas.findById(3L)).thenReturn(Optional.of(gift));

    Moment created = service.completeIdea(3L, TODAY);

    assertThat(created.getKind()).isEqualTo(MomentKind.GIFT_GIVEN);
    assertThat(created.getNote()).isEqualTo("Framed print from Denver");
    assertThat(gift.getStatus()).isEqualTo(IdeaStatus.DONE);
  }

  @Test
  void anIdeaNeedsATitle() {
    assertThatThrownBy(() -> service.createIdea(IdeaKind.GIFT, "  ", "", null, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("needs a title");
  }

  // ---------- moments ----------

  @Test
  void aMomentCannotBeLoggedInTheFuture() {
    assertThatThrownBy(
            () -> service.log(LocalDate.now().plusDays(2), MomentKind.DATE_NIGHT, "", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("future");
  }

  @Test
  void loggingWithNoDateMeansToday() {
    Moment saved = service.log(null, MomentKind.NOTE_LEFT, "on the coffee machine", null);
    assertThat(saved.getOccurredOn()).isEqualTo(LocalDate.now());
  }

  @Test
  void feltCloseOutsideItsRangeIsDroppedRatherThanStored() {
    Moment saved = service.log(TODAY, MomentKind.CONVERSATION, "", (short) 9);
    assertThat(saved.getFeltClose()).isNull();
  }

  @Test
  void intimacyIsTheOnlyKindHeldBackByTheDiscreetToggle() {
    assertThat(MomentKind.INTIMACY.isPrivate()).isTrue();
    assertThat(
            List.of(
                MomentKind.DATE_NIGHT,
                MomentKind.NOTE_LEFT,
                MomentKind.GIFT_GIVEN,
                MomentKind.CONVERSATION,
                MomentKind.TRIP,
                MomentKind.GESTURE))
        .allMatch(k -> !k.isPrivate());
  }

  // ---------- reading ----------

  @Test
  void aTakeawayBecomesAGestureIdea() {
    // The reason the takeaway field exists: a thought that stays a thought changes nothing.
    Reading article = new Reading("Bids for connection", ReadingKind.ARTICLE);
    article.markRead("Answer the small bids instead of half-listening", TODAY);
    when(reading.findById(4L)).thenReturn(Optional.of(article));

    Idea promoted = service.promoteTakeaway(4L, null, Effort.SMALL);

    assertThat(promoted.getKind()).isEqualTo(IdeaKind.GESTURE);
    assertThat(promoted.getTitle()).isEqualTo("Answer the small bids instead of half-listening");
    assertThat(promoted.getDetail()).contains("Bids for connection");
  }

  @Test
  void promotingWithNothingWrittenDownAsksForTheTakeawayFirst() {
    Reading article = new Reading("Something", ReadingKind.ARTICLE);
    when(reading.findById(4L)).thenReturn(Optional.of(article));

    assertThatThrownBy(() -> service.promoteTakeaway(4L, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("takeaway");
  }

  // ---------- helpers ----------

  private static Moment moment(LocalDate on) {
    return new Moment(on, MomentKind.INTIMACY);
  }

  private static Idea idea(String title, Effort effort) {
    Idea i = new Idea(IdeaKind.DATE, title);
    i.update(IdeaKind.DATE, title, "", null, null, effort, IdeaStatus.IDEA);
    return i;
  }

  // ---------------------------------------------------------------- paging

  /**
   * The footer's "12 of 48" is the whole reason a page carries a total: an "older" button that
   * might do nothing is worse than no button.
   */
  @Test
  void aPageCarriesTheTotalAndNotJustTheRows() {
    when(moments.findAllByOrderByOccurredOnDescIdDesc(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(moment(1), moment(2)), PageRequest.of(0, 12), 48));

    RelationshipService.MomentPage page = service.page(12, 0);

    assertThat(page.items()).hasSize(2);
    assertThat(page.total()).isEqualTo(48);
    assertThat(page.limit()).isEqualTo(12);
  }

  /**
   * An offset that is not on a page boundary comes back SNAPPED, because the rows returned are the
   * page it fell inside. Reporting the asked-for offset would have the screen label rows 1-12 as
   * "5-16 of 48".
   */
  @Test
  void anOffsetOffTheBoundaryIsReportedWhereItActuallyLanded() {
    ArgumentCaptor<Pageable> asked = ArgumentCaptor.forClass(Pageable.class);
    when(moments.findAllByOrderByOccurredOnDescIdDesc(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 12), 48));

    assertThat(service.page(12, 5).offset()).isZero();

    verify(moments).findAllByOrderByOccurredOnDescIdDesc(asked.capture());
    assertThat(asked.getValue().getPageNumber()).isZero();
  }

  /** A stale tab asking past the end after a delete gets an empty page, never a 500. */
  @Test
  void anOffsetPastTheEndIsAnEmptyPageNotAnError() {
    when(moments.findAllByOrderByOccurredOnDescIdDesc(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(99, 12), 48));

    RelationshipService.MomentPage page = service.page(12, 1200);

    assertThat(page.items()).isEmpty();
    assertThat(page.total()).isEqualTo(48);
  }

  /** A page is for reading, not for scraping the whole table in one request. */
  @Test
  void anAbsurdLimitIsCapped() {
    ArgumentCaptor<Pageable> asked = ArgumentCaptor.forClass(Pageable.class);
    when(moments.findAllByOrderByOccurredOnDescIdDesc(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 48));

    assertThat(service.page(100_000, 0).limit()).isEqualTo(100);

    verify(moments).findAllByOrderByOccurredOnDescIdDesc(asked.capture());
    assertThat(asked.getValue().getPageSize()).isEqualTo(100);
  }

  /** Zero and negatives come from a client bug, and a page of nothing forever is a worse bug. */
  @Test
  void aZeroLimitStillAsksForAtLeastOneRow() {
    when(moments.findAllByOrderByOccurredOnDescIdDesc(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 1), 48));

    assertThat(service.page(0, -30).limit()).isEqualTo(1);
    assertThat(service.page(0, -30).offset()).isZero();
  }

  private static Moment moment(int daysAgo) {
    return new Moment(LocalDate.now().minusDays(daysAgo), MomentKind.DATE_NIGHT);
  }
}
