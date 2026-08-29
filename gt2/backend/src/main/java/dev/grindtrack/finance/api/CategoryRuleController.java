package dev.grindtrack.finance.api;

import dev.grindtrack.finance.api.FinanceDtos.RuleRequest;
import dev.grindtrack.finance.api.FinanceDtos.RuleResponse;
import dev.grindtrack.finance.domain.MatchType;
import dev.grindtrack.finance.service.CategoryRuleService;
import dev.grindtrack.finance.service.CategoryRuleService.ApplyResult;
import dev.grindtrack.web.Requests;
import dev.grindtrack.web.Responses.Deleted;
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
 * The rules that file a merchant into a category without being asked twice.
 *
 * <p>Whether a pattern is a compilable regular expression is checked by {@link
 * CategoryRuleService}, not here: that is true of a rule however it is created, and a rule created
 * by an import job would otherwise skip the check entirely.
 */
@RestController
@RequestMapping("/api/finance/rules")
public class CategoryRuleController {

  /** Matching a substring is what almost every rule wants, so it is what an absent value means. */
  private static final MatchType DEFAULT_MATCH_TYPE = MatchType.CONTAINS;

  private static final int DEFAULT_PRIORITY = 100;

  private final CategoryRuleService rules;

  public CategoryRuleController(CategoryRuleService rules) {
    this.rules = rules;
  }

  @GetMapping
  public List<RuleResponse> list(@RequestParam(defaultValue = "true") boolean includeInactive) {
    return rules.list(includeInactive).stream().map(RuleResponse::from).toList();
  }

  @PostMapping
  public RuleResponse create(@RequestBody RuleRequest body) {
    return RuleResponse.from(
        rules.create(
            body.pattern(),
            matchType(body),
            body.category(),
            body.priority() == null ? DEFAULT_PRIORITY : body.priority()));
  }

  @PutMapping("/{id}")
  public RuleResponse update(@PathVariable Long id, @RequestBody RuleRequest body) {
    return RuleResponse.from(
        rules.update(
            id,
            body.pattern(),
            matchType(body),
            body.category(),
            body.priority() == null ? DEFAULT_PRIORITY : body.priority(),
            body.active() == null || body.active()));
  }

  @DeleteMapping("/{id}")
  public Deleted delete(@PathVariable Long id) {
    rules.delete(id);
    return Deleted.of(id);
  }

  /**
   * Re-runs every rule over the whole history.
   *
   * <p>The operation that makes writing a rule worth it: rows imported before the rule existed get
   * filed by it too. Hand-corrected rows are never touched.
   */
  @PostMapping("/apply")
  public ApplyResult apply() {
    return rules.backfill();
  }

  private static MatchType matchType(RuleRequest body) {
    return Requests.enumValue(MatchType.class, body.matchType(), "matchType", DEFAULT_MATCH_TYPE);
  }
}
