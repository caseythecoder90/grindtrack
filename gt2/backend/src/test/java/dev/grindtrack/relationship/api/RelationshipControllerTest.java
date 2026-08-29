package dev.grindtrack.relationship.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.grindtrack.relationship.domain.Effort;
import dev.grindtrack.relationship.domain.Idea;
import dev.grindtrack.relationship.domain.IdeaKind;
import dev.grindtrack.relationship.domain.Moment;
import dev.grindtrack.relationship.domain.MomentKind;
import dev.grindtrack.relationship.service.RelationshipService;
import dev.grindtrack.relationship.service.RelationshipService.Closeness;
import dev.grindtrack.relationship.service.RelationshipService.Perspective;
import dev.grindtrack.relationship.service.RelationshipService.Summary;
import dev.grindtrack.web.ApiExceptionHandler;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * The us tab over HTTP.
 *
 * <p>One test here is not really about HTTP: {@link #theSummaryNeverCarriesAWarningTone()} guards
 * the rule the whole feature rests on. The service test asserts the tones it produces; this asserts
 * that nothing on the way out to a screen invents a fourth one.
 */
class RelationshipControllerTest {

  private RelationshipService relationship;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    relationship = mock(RelationshipService.class);
    mvc =
        MockMvcBuilders.standaloneSetup(new RelationshipController(relationship))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
    when(relationship.timeline(anyInt())).thenReturn(List.of());
    when(relationship.listIdeas(anyBoolean())).thenReturn(List.of());
    when(relationship.allOccasions(any())).thenReturn(List.of());
    when(relationship.listReading(any())).thenReturn(List.of());
  }

  private static Moment moment(MomentKind kind) {
    Moment m = new Moment(LocalDate.of(2026, 8, 26), kind);
    m.update(LocalDate.of(2026, 8, 26), kind, "note", null);
    return m;
  }

  private static Summary summary(String tone) {
    return new Summary(
        List.of(),
        new Closeness(
            List.of("2026-08-26"),
            2L,
            5,
            6,
            new Perspective("2 days ago.", "That is recent.", tone)),
        List.of(),
        List.of(),
        List.of());
  }

  // ---------- moments ----------

  @Test
  void anUnknownKindIsRejectedWithTheListOfRealOnes() throws Exception {
    mvc.perform(
            post("/api/relationship/moments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"occurredOn\":\"2026-08-26\",\"kind\":\"ARGUMENT\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("DATE_NIGHT")));

    verify(relationship, never()).log(any(), any(), any(), any());
  }

  @Test
  void aMalformedDateIsRejected() throws Exception {
    mvc.perform(
            post("/api/relationship/moments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"occurredOn\":\"26/08/2026\",\"kind\":\"DATE_NIGHT\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void aFutureDateIsRefusedByTheServiceAndSurfacedAsA400() throws Exception {
    when(relationship.log(any(), any(), any(), any()))
        .thenThrow(new IllegalArgumentException("that date is in the future"));

    mvc.perform(
            post("/api/relationship/moments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"occurredOn\":\"2027-01-01\",\"kind\":\"DATE_NIGHT\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("future")));
  }

  @Test
  void aLoggedMomentReportsWhetherTheDiscreetToggleShouldHideIt() throws Exception {
    // The frontend filters on this flag rather than string-matching the kind.
    when(relationship.log(any(), eq(MomentKind.INTIMACY), any(), any()))
        .thenReturn(moment(MomentKind.INTIMACY));

    mvc.perform(
            post("/api/relationship/moments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"occurredOn\":\"2026-08-26\",\"kind\":\"INTIMACY\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isPrivate").value(true));
  }

  @Test
  void everyOtherKindIsNotPrivate() throws Exception {
    when(relationship.log(any(), eq(MomentKind.DATE_NIGHT), any(), any()))
        .thenReturn(moment(MomentKind.DATE_NIGHT));

    mvc.perform(
            post("/api/relationship/moments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"occurredOn\":\"2026-08-26\",\"kind\":\"date_night\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isPrivate").value(false));
  }

  // ---------- the rule the feature rests on ----------

  @Test
  void theSummaryNeverCarriesAWarningTone() throws Exception {
    for (String tone : List.of("CALM", "NEUTRAL", "SUGGEST")) {
      when(relationship.summary()).thenReturn(summary(tone));
      mvc.perform(get("/api/relationship/summary"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.closeness.perspective.tone").value(tone));
    }
  }

  @Test
  void theSummaryLeadsWithDatesRatherThanARate() throws Exception {
    when(relationship.summary()).thenReturn(summary("CALM"));

    mvc.perform(get("/api/relationship/summary"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.closeness.recentDates[0]").value("2026-08-26"))
        .andExpect(jsonPath("$.closeness.typicalPerMonth").value(6));
  }

  // ---------- ideas ----------

  @Test
  void anIdeaWithoutATitleIsRefused() throws Exception {
    when(relationship.createIdea(any(), any(), any(), any(), any(), any()))
        .thenThrow(new IllegalArgumentException("an idea needs a title"));

    mvc.perform(
            post("/api/relationship/ideas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"GESTURE\",\"title\":\"  \"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void anUnknownEffortIsRejected() throws Exception {
    mvc.perform(
            post("/api/relationship/ideas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"GIFT\",\"title\":\"x\",\"effort\":\"ENORMOUS\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("effort")));
  }

  @Test
  void actingOnAnIdeaComesBackAsTheMomentItBecame() throws Exception {
    when(relationship.completeIdea(eq(3L), any())).thenReturn(moment(MomentKind.GIFT_GIVEN));

    mvc.perform(
            post("/api/relationship/ideas/3/done")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.kind").value("GIFT_GIVEN"));
  }

  @Test
  void actingOnAnIdeaWorksWithNoBodyAtAll() throws Exception {
    when(relationship.completeIdea(eq(3L), any())).thenReturn(moment(MomentKind.GESTURE));

    mvc.perform(post("/api/relationship/ideas/3/done")).andExpect(status().isOk());
  }

  // ---------- occasions and reading ----------

  @Test
  void anOccasionNeedsARealDate() throws Exception {
    mvc.perform(
            post("/api/relationship/occasions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"label\":\"Anniversary\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("date")));
  }

  @Test
  void promotingATakeawayWithNothingWrittenDownIsRefused() throws Exception {
    when(relationship.promoteTakeaway(any(), any(), any()))
        .thenThrow(new IllegalArgumentException("write the takeaway first"));

    mvc.perform(
            post("/api/relationship/reading/4/promote")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("takeaway")));
  }

  @Test
  void aPromotedTakeawayComesBackAsAGestureIdea() throws Exception {
    Idea idea = new Idea(IdeaKind.GESTURE, "Answer the small bids");
    idea.update(IdeaKind.GESTURE, "Answer the small bids", "", null, null, Effort.SMALL, null);
    when(relationship.promoteTakeaway(eq(4L), any(), eq(Effort.SMALL))).thenReturn(idea);

    mvc.perform(
            post("/api/relationship/reading/4/promote")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"effort\":\"SMALL\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.kind").value("GESTURE"))
        .andExpect(jsonPath("$.title").value("Answer the small bids"));
  }

  @Test
  void anUnknownReadingStatusFilterIsRejected() throws Exception {
    mvc.perform(get("/api/relationship/reading").param("status", "SKIMMED"))
        .andExpect(status().isBadRequest());
  }
}
