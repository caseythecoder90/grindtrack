package dev.grindtrack.relationship.api;

import dev.grindtrack.relationship.domain.Idea;
import dev.grindtrack.relationship.domain.Moment;
import dev.grindtrack.relationship.domain.Reading;
import dev.grindtrack.relationship.service.RelationshipSummary;
import dev.grindtrack.relationship.service.RelationshipSummary.Closeness;
import dev.grindtrack.relationship.service.RelationshipSummary.Recency;
import dev.grindtrack.relationship.service.RelationshipSummary.Upcoming;
import java.math.BigDecimal;
import java.util.List;

/**
 * Request/response shapes for the relationship API.
 *
 * <p>These were split across two files that should have held neither: seven request records sat in
 * {@code RelationshipController}, and the response records sat in {@code RelationshipService} — so
 * the wire contract was declared by the service layer, and the entity-to-DTO mapping was written
 * twice, once in each. Both copies were live: the summary endpoint used the service's, the write
 * endpoints used the controller's.
 *
 * <p>{@link Recency}, {@link Closeness} and {@link Upcoming} are passed through from the service
 * rather than re-declared here. They are computed values with no entity behind them — there is
 * nothing to map, and a parallel copy would only be a second place to forget a field.
 */
public final class RelationshipDtos {

  private RelationshipDtos() {}

  // -------------------------------------------------------------- requests

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

  /** Body of "I did this", where even the date is optional — absent means today. */
  public record DoneRequest(String on) {}

  // ------------------------------------------------------------- responses

  /**
   * @param isPrivate whether the frontend holds this back behind the discreet toggle. Sent as a
   *     field rather than left to the client to infer from the kind, so the rule lives in one place
   */
  public record MomentResponse(
      Long id, String occurredOn, String kind, String note, Short feltClose, boolean isPrivate) {

    public static MomentResponse from(Moment m) {
      return new MomentResponse(
          m.getId(),
          m.getOccurredOn().toString(),
          m.getKind().name(),
          m.getNote(),
          m.getFeltClose(),
          m.getKind().isPrivate());
    }
  }

  public record IdeaResponse(
      Long id,
      String kind,
      String title,
      String detail,
      String occasion,
      BigDecimal estCost,
      String effort,
      String status) {

    public static IdeaResponse from(Idea i) {
      return new IdeaResponse(
          i.getId(),
          i.getKind().name(),
          i.getTitle(),
          i.getDetail(),
          i.getOccasion(),
          i.getEstCost(),
          i.getEffort() == null ? null : i.getEffort().name(),
          i.getStatus().name());
    }
  }

  public record ReadingResponse(
      Long id,
      String title,
      String url,
      String source,
      String kind,
      String status,
      String takeaway,
      String readOn) {

    public static ReadingResponse from(Reading r) {
      return new ReadingResponse(
          r.getId(),
          r.getTitle(),
          r.getUrl(),
          r.getSource(),
          r.getKind().name(),
          r.getStatus().name(),
          r.getTakeaway(),
          r.getReadOn() == null ? null : r.getReadOn().toString());
    }
  }

  public record SummaryResponse(
      List<Recency> recency,
      Closeness closeness,
      List<Upcoming> upcoming,
      List<IdeaResponse> readyIdeas,
      List<MomentResponse> lately) {

    public static SummaryResponse from(RelationshipSummary s) {
      return new SummaryResponse(
          s.recency(),
          s.closeness(),
          s.upcoming(),
          s.readyIdeas().stream().map(IdeaResponse::from).toList(),
          s.lately().stream().map(MomentResponse::from).toList());
    }
  }
}
