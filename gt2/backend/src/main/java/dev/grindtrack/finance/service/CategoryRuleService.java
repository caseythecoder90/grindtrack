package dev.grindtrack.finance.service;

import dev.grindtrack.finance.domain.CategoryRule;
import dev.grindtrack.finance.domain.CategoryRuleRepository;
import dev.grindtrack.finance.domain.CategorySource;
import dev.grindtrack.finance.domain.MatchType;
import dev.grindtrack.finance.domain.Transaction;
import dev.grindtrack.finance.domain.TransactionRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies category rules, and owns the rules themselves.
 *
 * <p>This closes the loop the importer left open. Before it, every imported row arrived {@link
 * CategorySource#UNCATEGORIZED} and the only way to give it a category was one row at a time, which
 * does not survive eight hundred transactions.
 *
 * <p>Two properties are load-bearing, and both come from {@link Transaction#categorizeByRule}: a
 * rule never overwrites a category a person chose, and re-running the rules is therefore always
 * safe. Together they are what make "fix one row, promote it to a rule, re-run" a workflow rather
 * than a gamble.
 */
@Service
public class CategoryRuleService {

  private final CategoryRuleRepository rules;
  private final TransactionRepository transactions;

  public CategoryRuleService(CategoryRuleRepository rules, TransactionRepository transactions) {
    this.rules = rules;
    this.transactions = transactions;
  }

  /** What a rules run changed, so the caller can report it rather than just claiming success. */
  public record ApplyResult(int examined, int categorized, int stillUncategorized) {}

  // ------------------------------------------------------------------ rules

  public List<CategoryRule> list(boolean includeInactive) {
    return includeInactive
        ? rules.findAllByOrderByPriorityAscIdAsc()
        : rules.findByActiveTrueOrderByPriorityAscIdAsc();
  }

  /**
   * @throws IllegalArgumentException if the pattern is unusable or already claimed. Both are user
   *     mistakes with an obvious fix, so the message is written to be read.
   */
  @Transactional
  public CategoryRule create(String pattern, MatchType matchType, String category, int priority) {
    MatchType type = matchType == null ? MatchType.CONTAINS : matchType;
    String invalid = type.validate(pattern);
    if (invalid != null) {
      throw new IllegalArgumentException(invalid);
    }
    if (category == null || category.isBlank()) {
      throw new IllegalArgumentException("a category is required");
    }
    rules
        .findByPatternIgnoreCaseAndMatchType(pattern.trim(), type)
        .ifPresent(
            existing -> {
              throw new IllegalArgumentException(
                  "a rule for "
                      + existing.getPattern()
                      + " already files those into "
                      + existing.getCategory());
            });
    return rules.save(new CategoryRule(pattern, type, category, priority));
  }

  @Transactional
  public CategoryRule update(
      Long id, String pattern, MatchType matchType, String category, int priority, boolean active) {
    CategoryRule rule =
        rules.findById(id).orElseThrow(() -> new NoSuchElementException("rule " + id));
    MatchType type = matchType == null ? MatchType.CONTAINS : matchType;
    String invalid = type.validate(pattern);
    if (invalid != null) {
      throw new IllegalArgumentException(invalid);
    }
    if (category == null || category.isBlank()) {
      throw new IllegalArgumentException("a category is required");
    }
    rule.update(pattern, type, category, priority, active);
    return rules.save(rule);
  }

  @Transactional
  public void delete(Long id) {
    rules.deleteById(id);
  }

  // ----------------------------------------------------------------- apply

  /**
   * Applies rules to transactions that have not been saved yet, on the import path.
   *
   * <p>Runs before the rows are written, so an import lands already categorized instead of dumping
   * several hundred rows into the review inbox.
   */
  @Transactional
  public int applyTo(Collection<Transaction> unsaved) {
    return applyTo(unsaved, true);
  }

  /**
   * @param recordHits false for an import preview. A dry run must leave the database exactly as it
   *     found it, and bumping rule hit counts would be a write — a small one, but the preview's
   *     whole promise is that pressing it changes nothing.
   */
  @Transactional
  public int applyTo(Collection<Transaction> unsaved, boolean recordHits) {
    if (unsaved.isEmpty()) {
      return 0;
    }
    return apply(unsaved, rules.findByActiveTrueOrderByPriorityAscIdAsc(), recordHits);
  }

  /**
   * Re-runs every rule over every row automation is allowed to touch.
   *
   * <p>This is the operation that makes rules worth writing: add one, run this, and the whole
   * history re-files itself. Rows a person corrected by hand are excluded by the query, and could
   * not be overwritten even if they were not.
   */
  @Transactional
  public ApplyResult backfill() {
    List<Transaction> candidates =
        transactions.findByCategorySourceNotOrderByIdAsc(CategorySource.MANUAL);
    int changed = apply(candidates, rules.findByActiveTrueOrderByPriorityAscIdAsc(), true);
    transactions.saveAll(candidates);
    int uncategorized =
        (int)
            candidates.stream()
                .filter(t -> t.getCategorySource() == CategorySource.UNCATEGORIZED)
                .count();
    return new ApplyResult(candidates.size(), changed, uncategorized);
  }

  private int apply(
      Collection<Transaction> targets, List<CategoryRule> active, boolean recordHits) {
    if (active.isEmpty()) {
      return 0;
    }
    Map<Long, Integer> hits = new HashMap<>();
    int changed = 0;

    for (Transaction txn : targets) {
      if (!txn.getCategorySource().isOverwritable()) {
        continue;
      }
      // Upper-cased once per transaction rather than once per rule per transaction.
      String merchant =
          txn.getMerchant() == null ? null : txn.getMerchant().toUpperCase(Locale.ROOT);
      String description = txn.getRawDescription().toUpperCase(Locale.ROOT);

      for (CategoryRule rule : active) {
        if (rule.matches(merchant, description)) {
          if (txn.categorizeByRule(rule.getCategory())) {
            changed++;
          }
          hits.merge(rule.getId(), 1, Integer::sum);
          break; // first match wins, which is what priority is for
        }
      }
    }

    if (recordHits && !hits.isEmpty()) {
      List<CategoryRule> touched = new ArrayList<>();
      for (CategoryRule rule : active) {
        Integer count = hits.get(rule.getId());
        if (count != null) {
          rule.recordHits(count);
          touched.add(rule);
        }
      }
      rules.saveAll(touched);
    }
    return changed;
  }

  /**
   * Turns a correction into a rule.
   *
   * <p>The point of the review inbox: fix one row, and the same merchant is never asked about
   * again. Returns empty when a rule for that pattern already exists, which is not an error. It
   * means the rule was already doing its job and this row predates it.
   */
  @Transactional
  public Optional<CategoryRule> promote(Long transactionId, String category) {
    Transaction txn =
        transactions
            .findById(transactionId)
            .orElseThrow(() -> new NoSuchElementException("txn " + transactionId));

    // The normalized merchant is the better pattern when there is one: it has already been
    // stripped of the store number, phone number and city that make a raw description unique to a
    // single purchase and therefore useless as a rule.
    String pattern = txn.getMerchant() == null ? txn.getRawDescription() : txn.getMerchant();
    if (pattern == null || pattern.isBlank()) {
      return Optional.empty();
    }
    if (rules.findByPatternIgnoreCaseAndMatchType(pattern.trim(), MatchType.CONTAINS).isPresent()) {
      return Optional.empty();
    }
    return Optional.of(create(pattern, MatchType.CONTAINS, category, 100));
  }
}
