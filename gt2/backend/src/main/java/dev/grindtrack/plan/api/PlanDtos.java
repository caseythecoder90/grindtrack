package dev.grindtrack.plan.api;

import dev.grindtrack.plan.domain.PlanItem;
import dev.grindtrack.plan.domain.PlanQuarter;
import dev.grindtrack.plan.domain.PlanReference;
import java.util.List;

/** Request/response shapes for the plan API. */
public final class PlanDtos {

  private PlanDtos() {}

  public record ItemResponse(
      Long id,
      String type,
      String title,
      String details,
      String targetLabel,
      String targetDate,
      Integer yearNum,
      Integer qtr,
      String tier,
      String status,
      String notes,
      String completedAt,
      int sortOrder) {

    static ItemResponse from(PlanItem i) {
      return new ItemResponse(
          i.getId(),
          i.getItemType(),
          i.getTitle(),
          i.getDetails(),
          i.getTargetLabel(),
          i.getTargetDate() == null ? null : i.getTargetDate().toString(),
          i.getYearNum(),
          i.getQtr(),
          i.getTier(),
          i.getStatus(),
          i.getNotes(),
          i.getCompletedAt() == null ? null : i.getCompletedAt().toString(),
          i.getSortOrder());
    }
  }

  public record QuarterResponse(
      int qtr,
      String windowLabel,
      int yearNum,
      String primaryFocus,
      String secondaryFocus,
      String careerTrack,
      String deliverables) {

    static QuarterResponse from(PlanQuarter q) {
      return new QuarterResponse(
          q.getQtr(),
          q.getWindowLabel(),
          q.getYearNum(),
          q.getPrimaryFocus(),
          q.getSecondaryFocus(),
          q.getCareerTrack(),
          q.getDeliverables());
    }
  }

  public record ReferenceResponse(String sheet, String title, String contentJson, int sortOrder) {

    static ReferenceResponse from(PlanReference r) {
      return new ReferenceResponse(
          r.getSheet(), r.getTitle(), r.getContentJson(), r.getSortOrder());
    }
  }

  public record PlanResponse(
      List<ItemResponse> items,
      List<QuarterResponse> quarters,
      List<ReferenceResponse> reference) {}

  public record UpdateItemRequest(String status, String notes) {}

  public record ImportItem(
      String type,
      String title,
      String details,
      String targetLabel,
      String targetDate,
      Integer yearNum,
      Integer qtr,
      String tier,
      String status,
      String notes,
      Integer sortOrder) {}

  public record ImportQuarter(
      Integer qtr,
      String windowLabel,
      Integer yearNum,
      String primaryFocus,
      String secondaryFocus,
      String careerTrack,
      String deliverables) {}

  public record ImportReference(
      String sheet, String title, String contentJson, Integer sortOrder) {}

  public record ImportRequest(
      List<ImportItem> items, List<ImportQuarter> quarters, List<ImportReference> reference) {}
}
