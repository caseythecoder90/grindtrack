package dev.grindtrack.plan.service;

import dev.grindtrack.plan.domain.PlanItem;
import dev.grindtrack.plan.domain.PlanItemRepository;
import dev.grindtrack.plan.domain.PlanQuarter;
import dev.grindtrack.plan.domain.PlanQuarterRepository;
import dev.grindtrack.plan.domain.PlanReference;
import dev.grindtrack.plan.domain.PlanReferenceRepository;
import java.util.ArrayList;
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
   * the user's status, completion date, notes <strong>and id</strong>, so re-importing an evolved
   * workbook never loses progress. New items take the status the workbook declares; items the
   * workbook no longer contains are removed.
   *
   * <p>Matched rows are updated in place rather than deleted and re-inserted. That is what makes a
   * plan item's id durable, which anything referencing one — a focus session recording which book
   * an hour went into — depends on. The old delete-all was safe only for as long as nothing pointed
   * at these rows.
   */
  @Transactional
  public int importPlan(
      List<PlanItem> newItems, List<PlanQuarter> newQuarters, List<PlanReference> newReferences) {
    Map<String, PlanItem> previous = new HashMap<>();
    for (PlanItem existing : items.findAll()) {
      previous.put(key(existing), existing);
    }

    List<PlanItem> toSave = new ArrayList<>(newItems.size());
    for (PlanItem incoming : newItems) {
      PlanItem match = previous.remove(key(incoming));
      if (match == null) {
        toSave.add(incoming);
      } else {
        match.replaceContentFrom(incoming);
        toSave.add(match);
      }
    }
    // Whatever is left in `previous` was not in the workbook this time: the item is gone.
    items.deleteAll(new ArrayList<>(previous.values()));
    items.flush();
    items.saveAll(toSave);

    quarters.deleteAll();
    references.deleteAll();
    quarters.saveAll(newQuarters);
    references.saveAll(newReferences);
    return newItems.size();
  }

  private static String key(PlanItem item) {
    return item.getItemType() + "|" + item.getTitle();
  }
}
