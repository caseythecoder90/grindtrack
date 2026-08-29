package dev.grindtrack.relationship.api;

import dev.grindtrack.relationship.api.RelationshipDtos.DoneRequest;
import dev.grindtrack.relationship.api.RelationshipDtos.IdeaRequest;
import dev.grindtrack.relationship.api.RelationshipDtos.IdeaResponse;
import dev.grindtrack.relationship.api.RelationshipDtos.MarkReadRequest;
import dev.grindtrack.relationship.api.RelationshipDtos.MomentRequest;
import dev.grindtrack.relationship.api.RelationshipDtos.MomentResponse;
import dev.grindtrack.relationship.api.RelationshipDtos.OccasionRequest;
import dev.grindtrack.relationship.api.RelationshipDtos.PromoteRequest;
import dev.grindtrack.relationship.api.RelationshipDtos.ReadingRequest;
import dev.grindtrack.relationship.api.RelationshipDtos.ReadingResponse;
import dev.grindtrack.relationship.api.RelationshipDtos.SummaryResponse;
import dev.grindtrack.relationship.domain.Effort;
import dev.grindtrack.relationship.domain.IdeaKind;
import dev.grindtrack.relationship.domain.IdeaStatus;
import dev.grindtrack.relationship.domain.MomentKind;
import dev.grindtrack.relationship.domain.ReadingKind;
import dev.grindtrack.relationship.domain.ReadingStatus;
import dev.grindtrack.relationship.service.RelationshipService;
import dev.grindtrack.relationship.service.RelationshipSummary.Upcoming;
import dev.grindtrack.web.Requests;
import dev.grindtrack.web.Responses.Deleted;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The relationship tab.
 *
 * <p>Behind the same authentication as everything else, and deliberately absent from {@code
 * /api/public/**} — none of this has a public shape and none of it ever should.
 *
 * <p>Everything validated here is shape: a parseable date, a known enum constant. The rules about
 * what may be recorded — a moment cannot be dated in the future, an idea needs a title — are {@link
 * RelationshipService}'s, because they hold however the call arrives.
 */
@RestController
@RequestMapping("/api/relationship")
public class RelationshipController {

  /** The recurring-occasion default: three weeks is enough notice to order something. */
  private static final int DEFAULT_LEAD_DAYS = 21;

  private static final int DEFAULT_TIMELINE_LIMIT = 60;

  private final RelationshipService relationship;

  public RelationshipController(RelationshipService relationship) {
    this.relationship = relationship;
  }

  // --------------------------------------------------------------- summary

  @GetMapping("/summary")
  public SummaryResponse summary() {
    return SummaryResponse.from(relationship.summary());
  }

  // --------------------------------------------------------------- moments

  @GetMapping("/moments")
  public List<MomentResponse> moments(
      @RequestParam(defaultValue = "" + DEFAULT_TIMELINE_LIMIT) int limit) {
    return relationship.timeline(limit).stream().map(MomentResponse::from).toList();
  }

  @PostMapping("/moments")
  public MomentResponse log(@RequestBody MomentRequest body) {
    return MomentResponse.from(
        relationship.log(
            Requests.optionalDate(body.occurredOn()),
            Requests.enumValue(MomentKind.class, body.kind(), "kind"),
            body.note(),
            body.feltClose()));
  }

  @PutMapping("/moments/{id}")
  public MomentResponse updateMoment(@PathVariable Long id, @RequestBody MomentRequest body) {
    return MomentResponse.from(
        relationship.updateMoment(
            id,
            Requests.optionalDate(body.occurredOn()),
            Requests.enumValue(MomentKind.class, body.kind(), "kind"),
            body.note(),
            body.feltClose()));
  }

  @DeleteMapping("/moments/{id}")
  public Deleted deleteMoment(@PathVariable Long id) {
    relationship.deleteMoment(id);
    return Deleted.of(id);
  }

  // ----------------------------------------------------------------- ideas

  @GetMapping("/ideas")
  public List<IdeaResponse> ideas(@RequestParam(defaultValue = "false") boolean includeDone) {
    return relationship.listIdeas(includeDone).stream().map(IdeaResponse::from).toList();
  }

  @PostMapping("/ideas")
  public IdeaResponse createIdea(@RequestBody IdeaRequest body) {
    return IdeaResponse.from(
        relationship.createIdea(
            Requests.enumValue(IdeaKind.class, body.kind(), "kind", IdeaKind.GIFT),
            body.title(),
            body.detail(),
            body.occasion(),
            body.estCost(),
            Requests.optionalEnum(Effort.class, body.effort(), "effort")));
  }

  @PutMapping("/ideas/{id}")
  public IdeaResponse updateIdea(@PathVariable Long id, @RequestBody IdeaRequest body) {
    return IdeaResponse.from(
        relationship.updateIdea(
            id,
            Requests.enumValue(IdeaKind.class, body.kind(), "kind", IdeaKind.GIFT),
            body.title(),
            body.detail(),
            body.occasion(),
            body.estCost(),
            Requests.optionalEnum(Effort.class, body.effort(), "effort"),
            Requests.enumValue(IdeaStatus.class, body.status(), "status", IdeaStatus.IDEA)));
  }

  /** Acting on an idea logs it as a moment and takes it off the list. */
  @PostMapping("/ideas/{id}/done")
  public MomentResponse completeIdea(
      @PathVariable Long id, @RequestBody(required = false) DoneRequest body) {
    return MomentResponse.from(
        relationship.completeIdea(id, body == null ? null : Requests.optionalDate(body.on())));
  }

  @DeleteMapping("/ideas/{id}")
  public Deleted deleteIdea(@PathVariable Long id) {
    relationship.deleteIdea(id);
    return Deleted.of(id);
  }

  // ------------------------------------------------------------- occasions

  @GetMapping("/occasions")
  public List<Upcoming> occasions() {
    return relationship.allOccasions(LocalDate.now());
  }

  /** Returns the whole list rather than the one row, because the next dates all shift with it. */
  @PostMapping("/occasions")
  public List<Upcoming> createOccasion(@RequestBody OccasionRequest body) {
    relationship.createOccasion(
        body.label(),
        Requests.requireDate(body.date(), "an occasion needs a date as YYYY-MM-DD"),
        body.recurring() == null || body.recurring(),
        body.leadDays() == null ? DEFAULT_LEAD_DAYS : body.leadDays(),
        body.note());
    return relationship.allOccasions(LocalDate.now());
  }

  @PutMapping("/occasions/{id}")
  public List<Upcoming> updateOccasion(@PathVariable Long id, @RequestBody OccasionRequest body) {
    relationship.updateOccasion(
        id,
        body.label(),
        Requests.requireDate(body.date(), "an occasion needs a date as YYYY-MM-DD"),
        body.recurring() == null || body.recurring(),
        body.leadDays() == null ? DEFAULT_LEAD_DAYS : body.leadDays(),
        body.note());
    return relationship.allOccasions(LocalDate.now());
  }

  @DeleteMapping("/occasions/{id}")
  public Deleted deleteOccasion(@PathVariable Long id) {
    relationship.deleteOccasion(id);
    return Deleted.of(id);
  }

  // --------------------------------------------------------------- reading

  @GetMapping("/reading")
  public List<ReadingResponse> reading(@RequestParam(required = false) String status) {
    return relationship
        .listReading(Requests.optionalEnum(ReadingStatus.class, status, "status"))
        .stream()
        .map(ReadingResponse::from)
        .toList();
  }

  @PostMapping("/reading")
  public List<ReadingResponse> addReading(@RequestBody ReadingRequest body) {
    relationship.addReading(
        body.title(),
        body.url(),
        body.source(),
        Requests.enumValue(ReadingKind.class, body.kind(), "kind", ReadingKind.ARTICLE));
    return allReading();
  }

  @PostMapping("/reading/{id}/read")
  public List<ReadingResponse> markRead(@PathVariable Long id, @RequestBody MarkReadRequest body) {
    relationship.markRead(id, body.takeaway(), Requests.optionalDate(body.readOn()));
    return allReading();
  }

  /** Turns a takeaway into a gesture idea, which is the point of having written it down. */
  @PostMapping("/reading/{id}/promote")
  public IdeaResponse promote(
      @PathVariable Long id, @RequestBody(required = false) PromoteRequest body) {
    return IdeaResponse.from(
        relationship.promoteTakeaway(
            id,
            body == null ? null : body.title(),
            body == null ? null : Requests.optionalEnum(Effort.class, body.effort(), "effort")));
  }

  @DeleteMapping("/reading/{id}")
  public Deleted deleteReading(@PathVariable Long id) {
    relationship.deleteReading(id);
    return Deleted.of(id);
  }

  private List<ReadingResponse> allReading() {
    return relationship.listReading().stream().map(ReadingResponse::from).toList();
  }
}
