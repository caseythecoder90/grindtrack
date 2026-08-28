package dev.grindtrack.relationship.api;

import dev.grindtrack.relationship.domain.Effort;
import dev.grindtrack.relationship.domain.IdeaKind;
import dev.grindtrack.relationship.domain.IdeaStatus;
import dev.grindtrack.relationship.domain.MomentKind;
import dev.grindtrack.relationship.domain.ReadingKind;
import dev.grindtrack.relationship.domain.ReadingStatus;
import dev.grindtrack.relationship.service.RelationshipService;
import dev.grindtrack.relationship.service.RelationshipService.IdeaView;
import dev.grindtrack.relationship.service.RelationshipService.MomentView;
import dev.grindtrack.relationship.service.RelationshipService.ReadingView;
import dev.grindtrack.relationship.service.RelationshipService.Summary;
import dev.grindtrack.relationship.service.RelationshipService.Upcoming;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
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
 */
@RestController
@RequestMapping("/api/relationship")
public class RelationshipController {

  private final RelationshipService relationship;

  public RelationshipController(RelationshipService relationship) {
    this.relationship = relationship;
  }

  public record MomentRequest(String occurredOn, String kind, String note, Short feltClose) {}

  public record IdeaRequest(
      String kind,
      String title,
      String detail,
      String occasion,
      BigDecimal estCost,
      String effort,
      String status) {}

  public record OccasionRequest(
      String label, String date, Boolean recurring, Integer leadDays, String note) {}

  public record ReadingRequest(String title, String url, String source, String kind) {}

  public record MarkReadRequest(String takeaway, String readOn) {}

  public record PromoteRequest(String title, String effort) {}

  public record DoneRequest(String on) {}

  // --------------------------------------------------------------- summary

  @GetMapping("/summary")
  public Summary summary() {
    return relationship.summary();
  }

  // --------------------------------------------------------------- moments

  @GetMapping("/moments")
  public List<MomentView> moments(@RequestParam(defaultValue = "60") int limit) {
    return relationship.timeline(limit);
  }

  @PostMapping("/moments")
  public MomentView log(@RequestBody MomentRequest body) {
    return view(
        relationship.log(
            optionalDate(body.occurredOn()),
            momentKind(body.kind()),
            body.note(),
            body.feltClose()));
  }

  @PutMapping("/moments/{id}")
  public MomentView updateMoment(@PathVariable Long id, @RequestBody MomentRequest body) {
    return view(
        relationship.updateMoment(
            id,
            optionalDate(body.occurredOn()),
            momentKind(body.kind()),
            body.note(),
            body.feltClose()));
  }

  @DeleteMapping("/moments/{id}")
  public ResponseEntity<?> deleteMoment(@PathVariable Long id) {
    relationship.deleteMoment(id);
    return ResponseEntity.ok(Map.of("deleted", id));
  }

  // ----------------------------------------------------------------- ideas

  @GetMapping("/ideas")
  public List<IdeaView> ideas(@RequestParam(defaultValue = "false") boolean includeDone) {
    return relationship.listIdeas(includeDone);
  }

  @PostMapping("/ideas")
  public IdeaView createIdea(@RequestBody IdeaRequest body) {
    return toView(
        relationship.createIdea(
            ideaKind(body.kind()),
            body.title(),
            body.detail(),
            body.occasion(),
            body.estCost(),
            effort(body.effort())));
  }

  @PutMapping("/ideas/{id}")
  public IdeaView updateIdea(@PathVariable Long id, @RequestBody IdeaRequest body) {
    return toView(
        relationship.updateIdea(
            id,
            ideaKind(body.kind()),
            body.title(),
            body.detail(),
            body.occasion(),
            body.estCost(),
            effort(body.effort()),
            ideaStatus(body.status())));
  }

  /** Acting on an idea logs it as a moment and takes it off the list. */
  @PostMapping("/ideas/{id}/done")
  public MomentView completeIdea(
      @PathVariable Long id, @RequestBody(required = false) DoneRequest body) {
    return view(relationship.completeIdea(id, body == null ? null : optionalDate(body.on())));
  }

  @DeleteMapping("/ideas/{id}")
  public ResponseEntity<?> deleteIdea(@PathVariable Long id) {
    relationship.deleteIdea(id);
    return ResponseEntity.ok(Map.of("deleted", id));
  }

  // ------------------------------------------------------------- occasions

  @GetMapping("/occasions")
  public List<Upcoming> occasions() {
    return relationship.allOccasions(LocalDate.now());
  }

  @PostMapping("/occasions")
  public ResponseEntity<?> createOccasion(@RequestBody OccasionRequest body) {
    relationship.createOccasion(
        body.label(),
        requireDate(body.date()),
        body.recurring() == null || body.recurring(),
        body.leadDays() == null ? 21 : body.leadDays(),
        body.note());
    return ResponseEntity.ok(relationship.allOccasions(LocalDate.now()));
  }

  @PutMapping("/occasions/{id}")
  public ResponseEntity<?> updateOccasion(
      @PathVariable Long id, @RequestBody OccasionRequest body) {
    relationship.updateOccasion(
        id,
        body.label(),
        requireDate(body.date()),
        body.recurring() == null || body.recurring(),
        body.leadDays() == null ? 21 : body.leadDays(),
        body.note());
    return ResponseEntity.ok(relationship.allOccasions(LocalDate.now()));
  }

  @DeleteMapping("/occasions/{id}")
  public ResponseEntity<?> deleteOccasion(@PathVariable Long id) {
    relationship.deleteOccasion(id);
    return ResponseEntity.ok(Map.of("deleted", id));
  }

  // --------------------------------------------------------------- reading

  @GetMapping("/reading")
  public List<ReadingView> reading(@RequestParam(required = false) String status) {
    return relationship.listReading(readingStatus(status));
  }

  @PostMapping("/reading")
  public ResponseEntity<?> addReading(@RequestBody ReadingRequest body) {
    relationship.addReading(body.title(), body.url(), body.source(), readingKind(body.kind()));
    return ResponseEntity.ok(relationship.listReading());
  }

  @PostMapping("/reading/{id}/read")
  public ResponseEntity<?> markRead(@PathVariable Long id, @RequestBody MarkReadRequest body) {
    relationship.markRead(id, body.takeaway(), optionalDate(body.readOn()));
    return ResponseEntity.ok(relationship.listReading());
  }

  /** Turns a takeaway into a gesture idea, which is the point of having written it down. */
  @PostMapping("/reading/{id}/promote")
  public IdeaView promote(
      @PathVariable Long id, @RequestBody(required = false) PromoteRequest body) {
    return toView(
        relationship.promoteTakeaway(
            id, body == null ? null : body.title(), body == null ? null : effort(body.effort())));
  }

  @DeleteMapping("/reading/{id}")
  public ResponseEntity<?> deleteReading(@PathVariable Long id) {
    relationship.deleteReading(id);
    return ResponseEntity.ok(Map.of("deleted", id));
  }

  // ---------------------------------------------------------------- mapping

  private static MomentView view(dev.grindtrack.relationship.domain.Moment m) {
    return new MomentView(
        m.getId(),
        m.getOccurredOn().toString(),
        m.getKind().name(),
        m.getNote(),
        m.getFeltClose(),
        m.getKind().isPrivate());
  }

  private static IdeaView toView(dev.grindtrack.relationship.domain.Idea i) {
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

  // ------------------------------------------------------------- parsing

  private static MomentKind momentKind(String value) {
    try {
      return MomentKind.valueOf(value == null ? "" : value.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "kind must be one of DATE_NIGHT, NOTE_LEFT, GIFT_GIVEN, INTIMACY, CONVERSATION, TRIP,"
              + " GESTURE");
    }
  }

  private static IdeaKind ideaKind(String value) {
    if (value == null || value.isBlank()) {
      return IdeaKind.GIFT;
    }
    try {
      return IdeaKind.valueOf(value.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("kind must be GIFT, DATE or GESTURE");
    }
  }

  private static IdeaStatus ideaStatus(String value) {
    if (value == null || value.isBlank()) {
      return IdeaStatus.IDEA;
    }
    try {
      return IdeaStatus.valueOf(value.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("status must be IDEA, PLANNED or DONE");
    }
  }

  private static Effort effort(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Effort.valueOf(value.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("effort must be SMALL, MEDIUM or BIG");
    }
  }

  private static ReadingKind readingKind(String value) {
    if (value == null || value.isBlank()) {
      return ReadingKind.ARTICLE;
    }
    try {
      return ReadingKind.valueOf(value.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("kind must be ARTICLE, BOOK or PODCAST");
    }
  }

  private static ReadingStatus readingStatus(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return ReadingStatus.valueOf(value.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("status must be TO_READ or READ");
    }
  }

  private static LocalDate requireDate(String value) {
    LocalDate parsed = optionalDate(value);
    if (parsed == null) {
      throw new IllegalArgumentException("a date is required as YYYY-MM-DD");
    }
    return parsed;
  }

  private static LocalDate optionalDate(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(value.trim());
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException("dates must be YYYY-MM-DD");
    }
  }
}
