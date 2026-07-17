package dev.grindtrack.plan.api;

import dev.grindtrack.plan.api.PlanDtos.ImportItem;
import dev.grindtrack.plan.api.PlanDtos.ImportQuarter;
import dev.grindtrack.plan.api.PlanDtos.ImportReference;
import dev.grindtrack.plan.api.PlanDtos.ImportRequest;
import dev.grindtrack.plan.api.PlanDtos.ItemResponse;
import dev.grindtrack.plan.api.PlanDtos.PlanResponse;
import dev.grindtrack.plan.api.PlanDtos.QuarterResponse;
import dev.grindtrack.plan.api.PlanDtos.ReferenceResponse;
import dev.grindtrack.plan.api.PlanDtos.UpdateItemRequest;
import dev.grindtrack.plan.domain.PlanItem;
import dev.grindtrack.plan.domain.PlanQuarter;
import dev.grindtrack.plan.domain.PlanReference;
import dev.grindtrack.plan.service.PlanService;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/plan")
public class PlanController {

  private static final Set<String> ITEM_TYPES =
      Set.of("milestone", "cert", "module", "book", "project");
  private static final Set<String> STATUSES = Set.of("not_started", "in_progress", "done");
  private static final int MAX_NOTES_CHARS = 10_000;

  private final PlanService planService;

  public PlanController(PlanService planService) {
    this.planService = planService;
  }

  @GetMapping
  public PlanResponse plan() {
    return new PlanResponse(
        planService.allItems().stream().map(ItemResponse::from).toList(),
        planService.allQuarters().stream().map(QuarterResponse::from).toList(),
        planService.allReferences().stream().map(ReferenceResponse::from).toList());
  }

  @PatchMapping("/items/{id}")
  public ResponseEntity<?> update(@PathVariable Long id, @RequestBody UpdateItemRequest body) {
    if (body.status() != null && !STATUSES.contains(body.status())) {
      return badRequest("status must be one of " + STATUSES);
    }
    if (body.notes() != null && body.notes().length() > MAX_NOTES_CHARS) {
      return badRequest("notes are limited to " + MAX_NOTES_CHARS + " characters");
    }
    return planService
        .update(id, body.status(), body.notes())
        .<ResponseEntity<?>>map(item -> ResponseEntity.ok(ItemResponse.from(item)))
        .orElseGet(() -> ResponseEntity.status(404).body(Map.of("error", "no such item")));
  }

  /** Full plan (re-)import from plan.json; progress on matching items is preserved. */
  @PostMapping("/import")
  public ResponseEntity<?> importPlan(@RequestBody ImportRequest body) {
    if (body.items() == null || body.items().isEmpty()) {
      return badRequest("items must not be empty");
    }
    List<PlanItem> newItems = new ArrayList<>();
    for (ImportItem in : body.items()) {
      if (in.type() == null || !ITEM_TYPES.contains(in.type())) {
        return badRequest("item type must be one of " + ITEM_TYPES + ": " + in.title());
      }
      if (in.title() == null || in.title().isBlank() || in.title().length() > 300) {
        return badRequest("every item needs a title (max 300 chars)");
      }
      String status = in.status() == null ? "not_started" : in.status();
      if (!STATUSES.contains(status)) {
        return badRequest("bad status '" + status + "' on: " + in.title());
      }
      LocalDate targetDate;
      try {
        targetDate = in.targetDate() == null ? null : LocalDate.parse(in.targetDate());
      } catch (DateTimeParseException e) {
        return badRequest("bad targetDate on: " + in.title());
      }
      newItems.add(
          new PlanItem(
              in.type(),
              in.title().trim(),
              in.details(),
              in.targetLabel(),
              targetDate,
              in.yearNum(),
              in.qtr(),
              in.tier(),
              status,
              in.notes(),
              in.sortOrder() == null ? 0 : in.sortOrder()));
    }
    List<PlanQuarter> newQuarters = new ArrayList<>();
    if (body.quarters() != null) {
      for (ImportQuarter q : body.quarters()) {
        if (q.qtr() == null || q.qtr() < 1 || q.qtr() > 12 || q.windowLabel() == null) {
          return badRequest("quarters need qtr 1-12 and a windowLabel");
        }
        newQuarters.add(
            new PlanQuarter(
                q.qtr(),
                q.windowLabel(),
                q.yearNum() == null ? (q.qtr() - 1) / 4 + 1 : q.yearNum(),
                q.primaryFocus(),
                q.secondaryFocus(),
                q.careerTrack(),
                q.deliverables()));
      }
    }
    List<PlanReference> newReferences = new ArrayList<>();
    if (body.reference() != null) {
      int i = 0;
      for (ImportReference r : body.reference()) {
        if (r.sheet() == null || r.title() == null || r.contentJson() == null) {
          return badRequest("reference sheets need sheet, title, and contentJson");
        }
        newReferences.add(
            new PlanReference(
                r.sheet(), r.title(), r.contentJson(), r.sortOrder() == null ? i : r.sortOrder()));
        i++;
      }
    }
    int imported = planService.importPlan(newItems, newQuarters, newReferences);
    return ResponseEntity.ok(
        Map.of(
            "items", imported, "quarters", newQuarters.size(), "reference", newReferences.size()));
  }

  private static ResponseEntity<Map<String, String>> badRequest(String message) {
    return ResponseEntity.badRequest().body(Map.of("error", message));
  }
}
