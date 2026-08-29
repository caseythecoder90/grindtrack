package dev.grindtrack.finance.api;

import dev.grindtrack.finance.api.FinanceDtos.CategorizeAndLearnRequest;
import dev.grindtrack.finance.api.FinanceDtos.CategorizeAndLearnResponse;
import dev.grindtrack.finance.api.FinanceDtos.CategorizeRequest;
import dev.grindtrack.finance.api.FinanceDtos.ReclassifyRequest;
import dev.grindtrack.finance.api.FinanceDtos.TransactionPageResponse;
import dev.grindtrack.finance.api.FinanceDtos.TransactionRequest;
import dev.grindtrack.finance.api.FinanceDtos.TransactionResponse;
import dev.grindtrack.finance.domain.CategorySource;
import dev.grindtrack.finance.domain.Transaction;
import dev.grindtrack.finance.domain.TxnType;
import dev.grindtrack.finance.service.CategoryRuleService;
import dev.grindtrack.finance.service.FinanceService;
import dev.grindtrack.finance.service.FinanceService.ReclassifyResult;
import dev.grindtrack.web.BadRequestException;
import dev.grindtrack.web.ConflictException;
import dev.grindtrack.web.Requests;
import dev.grindtrack.web.Responses.Deleted;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Individual transactions: browsing them, adding one by hand, and correcting how one was filed.
 *
 * <p>Two services, which the conventions call a smell worth a minute of thought. It survives that
 * minute: {@link #categorizeAndLearn} is the review inbox's whole reason to exist, and filing a row
 * and writing the rule that files the next one are genuinely two capabilities.
 */
@RestController
@RequestMapping("/api/finance/transactions")
public class TransactionController {

  private static final int MAX_DESCRIPTION_CHARS = 500;

  /** Big enough to scroll a month, small enough that a typo'd page size is not a table scan. */
  private static final int MAX_PAGE_SIZE = 200;

  private final FinanceService finance;
  private final CategoryRuleService rules;

  public TransactionController(FinanceService finance, CategoryRuleService rules) {
    this.finance = finance;
    this.rules = rules;
  }

  /**
   * Every transaction, paged and filtered, for browsing rather than reviewing.
   *
   * @param txnType optional: SPEND, INCOME, TRANSFER or PAYMENT. Filtering to INCOME is how you
   *     find out why an expected-income figure looks wrong
   * @param sort {@code amount} for biggest-first by absolute value, anything else for newest-first
   */
  @GetMapping
  public TransactionPageResponse browse(
      @RequestParam(required = false) Long accountId,
      @RequestParam(required = false) String txnType,
      @RequestParam(defaultValue = "false") boolean uncategorizedOnly,
      @RequestParam(defaultValue = "date") String sort,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {

    if (page < 0) {
      throw new BadRequestException("page cannot be negative");
    }
    if (size < 1 || size > MAX_PAGE_SIZE) {
      throw new BadRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
    }

    return TransactionPageResponse.from(
        finance.browse(
            accountId,
            Requests.optionalEnum(TxnType.class, txnType, "txnType"),
            uncategorizedOnly ? CategorySource.UNCATEGORIZED : null,
            page,
            size,
            "amount".equalsIgnoreCase(sort)));
  }

  /** The review inbox: rows nothing has confidently categorized yet. */
  @GetMapping("/uncategorized")
  public List<TransactionResponse> uncategorized() {
    return finance.listUncategorized().stream().map(TransactionResponse::from).toList();
  }

  /**
   * @throws ConflictException when an identical row already exists, rather than filing a second
   *     copy of it
   */
  @PostMapping
  public TransactionResponse addTransaction(@RequestBody TransactionRequest body) {
    if (body.accountId() == null) {
      throw new BadRequestException("accountId is required");
    }
    if (body.amount() == null || body.amount().signum() == 0) {
      throw new BadRequestException("amount is required and cannot be zero");
    }
    LocalDate posted =
        Requests.requireDate(body.postedDate(), "postedDate is required as YYYY-MM-DD");

    return finance
        .addTransaction(
            body.accountId(),
            posted,
            Requests.optionalDate(body.transactionDate()),
            body.amount(),
            Requests.requireText(
                body.description(), "description is required", MAX_DESCRIPTION_CHARS),
            Requests.optionalEnum(TxnType.class, body.txnType(), "txnType"),
            body.notes())
        .map(TransactionResponse::from)
        .orElseThrow(() -> new ConflictException("an identical transaction already exists"));
  }

  @PatchMapping("/{id}/category")
  public TransactionResponse categorize(
      @PathVariable Long id, @RequestBody CategorizeRequest body) {
    return TransactionResponse.from(finance.categorize(id, body.category()));
  }

  /**
   * Categorizes a row and, optionally, teaches the app to do it next time.
   *
   * <p>This is the review inbox's whole reason to exist. Fixing one row is tedious; fixing one row
   * and never being asked about that merchant again is a workflow. The rule is created from the
   * normalized merchant, which is already stripped of the store number and city that would
   * otherwise make it match exactly one purchase.
   */
  @PostMapping("/{id}/categorize")
  public CategorizeAndLearnResponse categorizeAndLearn(
      @PathVariable Long id, @RequestBody CategorizeAndLearnRequest body) {
    if (body.category() == null || body.category().isBlank()) {
      throw new BadRequestException("a category is required");
    }
    Transaction saved = finance.categorize(id, body.category());
    return Boolean.TRUE.equals(body.createRule())
        ? CategorizeAndLearnResponse.of(saved, rules.promote(id, body.category()))
        : CategorizeAndLearnResponse.of(saved);
  }

  @PatchMapping("/{id}/type")
  public TransactionResponse reclassify(
      @PathVariable Long id, @RequestBody ReclassifyRequest body) {
    return TransactionResponse.from(
        finance.reclassify(id, Requests.enumValue(TxnType.class, body.txnType(), "txnType")));
  }

  /**
   * Re-runs the type classifier over every transaction.
   *
   * <p>For after the classifier is corrected: the fix reaches future imports on its own, but months
   * of already-imported rows keep whatever the old rule decided until this is run.
   */
  @PostMapping("/reclassify")
  public ReclassifyResult reclassifyAll() {
    return finance.reclassifyAll();
  }

  @DeleteMapping("/{id}")
  public Deleted deleteTransaction(@PathVariable Long id) {
    finance.deleteTransaction(id);
    return Deleted.of(id);
  }
}
