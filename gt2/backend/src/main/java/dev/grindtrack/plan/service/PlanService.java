package dev.grindtrack.plan.service;

import dev.grindtrack.plan.domain.PlanItem;
import dev.grindtrack.plan.domain.PlanItemRepository;
import dev.grindtrack.plan.domain.PlanQuarter;
import dev.grindtrack.plan.domain.PlanQuarterRepository;
import dev.grindtrack.plan.domain.PlanReference;
import dev.grindtrack.plan.domain.PlanReferenceRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanService {

  private final PlanItemRepository items;
  private final PlanQuarterRepository quarters;
  private final PlanReferenceRepository references;

  public PlanService(
      PlanItemRepository items,
      PlanQuarterRepository quarters,
      PlanReferenceRepository references) {
    this.items = items;
    this.quarters = quarters;
    this.references = references;
  }

  public List<PlanItem> allItems() {
    return items.findAll();
  }

  public List<PlanQuarter> allQuarters() {
    return quarters.findAll();
  }

  public List<PlanReference> allReferences() {
    return references.findAll();
  }

  @Transactional
  public Optional<PlanItem> update(Long id, String status, String notes) {
    return items
        .findById(id)
        .map(
            item -> {
              if (status != null) {
                item.setStatus(status);
              }
              if (notes != null) {
                item.setNotes(notes);
              }
              return items.save(item);
            });
  }

  /**
   * Replaces all plan content. Items are matched to existing rows by (type, title): matches keep
   * the user's status, completion date, and notes, so re-importing an evolved workbook never loses
   * progress. New items take the status the workbook declares.
   */
  @Transactional
  public int importPlan(
      List<PlanItem> newItems, List<PlanQuarter> newQuarters, List<PlanReference> newReferences) {
    Map<String, PlanItem> previous = new HashMap<>();
    for (PlanItem existing : items.findAll()) {
      previous.put(key(existing), existing);
    }
    for (PlanItem incoming : newItems) {
      PlanItem match = previous.get(key(incoming));
      if (match != null) {
        incoming.carryProgressFrom(match);
      }
    }
    items.deleteAll();
    quarters.deleteAll();
    references.deleteAll();
    items.flush();
    items.saveAll(newItems);
    quarters.saveAll(newQuarters);
    references.saveAll(newReferences);
    return newItems.size();
  }

  private static String key(PlanItem item) {
    return item.getItemType() + "|" + item.getTitle();
  }
}
