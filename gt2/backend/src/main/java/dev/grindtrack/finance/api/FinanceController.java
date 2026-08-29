package dev.grindtrack.finance.api;

import dev.grindtrack.finance.api.FinanceDtos.AccountRequest;
import dev.grindtrack.finance.api.FinanceDtos.AccountResponse;
import dev.grindtrack.finance.api.FinanceDtos.BalanceRequest;
import dev.grindtrack.finance.api.FinanceDtos.CategorizeAndLearnRequest;
import dev.grindtrack.finance.api.FinanceDtos.CategorizeRequest;
import dev.grindtrack.finance.api.FinanceDtos.GoalRequest;
import dev.grindtrack.finance.api.FinanceDtos.GoalResponse;
import dev.grindtrack.finance.api.FinanceDtos.ReclassifyRequest;
import dev.grindtrack.finance.api.FinanceDtos.RuleRequest;
import dev.grindtrack.finance.api.FinanceDtos.RuleResponse;
import dev.grindtrack.finance.api.FinanceDtos.SummaryResponse;
import dev.grindtrack.finance.api.FinanceDtos.TransactionRequest;
import dev.grindtrack.finance.api.FinanceDtos.TransactionResponse;
import dev.grindtrack.finance.domain.Account;
import dev.grindtrack.finance.domain.AccountType;
import dev.grindtrack.finance.domain.CategoryRule;
import dev.grindtrack.finance.domain.CategorySource;
import dev.grindtrack.finance.domain.Institution;
import dev.grindtrack.finance.domain.MatchType;
import dev.grindtrack.finance.domain.SavingsGoal;
import dev.grindtrack.finance.domain.Transaction;
import dev.grindtrack.finance.domain.TxnType;
import dev.grindtrack.finance.service.CategoryRuleService;
import dev.grindtrack.finance.service.FinanceService;
import dev.grindtrack.finance.service.RecurringDetector;
import dev.grindtrack.web.BadRequestException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Accounts, transactions and savings goals.
 *
 * <p>Unlike {@code TodoController} this one does go through a service: normalization, transaction
 * typing and dedupe are real logic that the statement importers in phase 2 must reuse verbatim, so
 * they cannot live in the controller.
 */
@RestController
@RequestMapping("/api/finance")
public class FinanceController {

  private static final int MAX_NAME_CHARS = 120;
  private static final int MAX_DESCRIPTION_CHARS = 500;

  private final FinanceService finance;
  private final CategoryRuleService rules;
  private final RecurringDetector recurringDetector;

  public FinanceController(
      FinanceService finance, CategoryRuleService rules, RecurringDetector recurringDetector) {
    this.finance = finance;
    this.rules = rules;
    this.recurringDetector = recurringDetector;
  }

  // ------------------------------------------------------------- dashboard

  @GetMapping("/summary")
  public SummaryResponse summary() {
    List<AccountResponse> accounts =
        finance.listAccounts(false).stream().map(this::toAccountResponse).toList();
    // Read the savings total once and hand it down: every goal needs it twice, and re-querying it
    // per goal made the dashboard issue the same SUM four or five times per load.
    BigDecimal savings = finance.savingsBalance();
    List<GoalResponse> goals =
        finance.listGoals(false).stream().map(g -> toGoalResponse(g, savings)).toList();
    return new SummaryResponse(
        savings, finance.netWorth(), goals, accounts, finance.listUncategorized().size());
  }

  /**
   * Where the money went, over a window.
   *
   * <p>Defaults to the last 30 days. Transfers and card payments are excluded by the query, so
   * these totals are spending rather than movement.
   */
  @GetMapping("/spending")
  public FinanceService.SpendSummary spending(
      @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
    LocalDate end = from == null && to == null ? LocalDate.now() : optionalDate(to);
    LocalDate start = optionalDate(from);
    if (end == null) {
      end = LocalDate.now();
    }
    if (start == null) {
      start = end.minusDays(30);
    }
    if (start.isAfter(end)) {
      throw new BadRequestException("from must be on or before to");
    }
    return finance.spendBetween(start, end);
  }

  /**
   * The last few months side by side.
   *
   * <p>A single window tells you what you spent; a run of months tells you whether that is normal,
   * which is the question you actually have.
   */
  @GetMapping("/spending/monthly")
  public List<FinanceService.MonthTotal> monthly(@RequestParam(defaultValue = "6") int months) {
    if (months < 1 || months > 36) {
      throw new BadRequestException("months must be between 1 and 36");
    }
    return finance.monthlyTotals(months);
  }

  /**
   * The charges that come back every month.
   *
   * <p>Two jobs: it is the fastest way to build a budget that is right the first time, and it is
   * the subscription audit nobody gets round to doing.
   */
  @GetMapping("/recurring")
  public RecurringDetector.RecurringReport recurring() {
    return recurringDetector.detect();
  }

  // ------------------------------------------------------------------ rules

  @GetMapping("/rules")
  public List<RuleResponse> listRules(
      @RequestParam(defaultValue = "true") boolean includeInactive) {
    return rules.list(includeInactive).stream().map(RuleResponse::from).toList();
  }

  @PostMapping("/rules")
  public RuleResponse createRule(@RequestBody RuleRequest body) {
    return RuleResponse.from(
        rules.create(
            body.pattern(),
            matchType(body.matchType()),
            body.category(),
            body.priority() == null ? 100 : body.priority()));
  }

  @PutMapping("/rules/{id}")
  public RuleResponse updateRule(@PathVariable Long id, @RequestBody RuleRequest body) {
    return RuleResponse.from(
        rules.update(
            id,
            body.pattern(),
            matchType(body.matchType()),
            body.category(),
            body.priority() == null ? 100 : body.priority(),
            body.active() == null || body.active()));
  }

  @DeleteMapping("/rules/{id}")
  public ResponseEntity<?> deleteRule(@PathVariable Long id) {
    rules.delete(id);
    return ResponseEntity.ok(Map.of("deleted", id));
  }

  /**
   * Re-runs every rule over the whole history.
   *
   * <p>The operation that makes writing a rule worth it: rows imported before the rule existed get
   * filed by it too. Hand-corrected rows are never touched.
   */
  @PostMapping("/rules/apply")
  public CategoryRuleService.ApplyResult applyRules() {
    return rules.backfill();
  }

  // ---------------------------------------------------------------- accounts

  @GetMapping("/accounts")
  public List<AccountResponse> listAccounts(
      @RequestParam(defaultValue = "false") boolean includeInactive) {
    return finance.listAccounts(includeInactive).stream().map(this::toAccountResponse).toList();
  }

  @PostMapping("/accounts")
  public AccountResponse createAccount(@RequestBody AccountRequest body) {
    Account account =
        finance.createAccount(
            requireName(body.name()),
            requireInstitution(body.institution()),
            requireAccountType(body.accountType()),
            body.last4(),
            Boolean.TRUE.equals(body.countsTowardSavings()),
            body.sortOrder() == null ? 0 : body.sortOrder());
    return toAccountResponse(account);
  }

  @PutMapping("/accounts/{id}")
  public AccountResponse updateAccount(@PathVariable Long id, @RequestBody AccountRequest body) {
    Account account =
        finance.updateAccount(
            id,
            requireName(body.name()),
            requireInstitution(body.institution()),
            requireAccountType(body.accountType()),
            body.last4(),
            Boolean.TRUE.equals(body.countsTowardSavings()),
            body.active() == null || body.active(),
            body.sortOrder() == null ? 0 : body.sortOrder());
    return toAccountResponse(account);
  }

  /** Records a balance reading. Sign is corrected server-side for cards and loans. */
  @PatchMapping("/accounts/{id}/balance")
  public AccountResponse recordBalance(@PathVariable Long id, @RequestBody BalanceRequest body) {
    if (body.balance() == null) {
      throw new BadRequestException("balance is required");
    }
    return toAccountResponse(finance.recordBalance(id, body.balance(), optionalDate(body.asOf())));
  }

  @DeleteMapping("/accounts/{id}")
  public ResponseEntity<?> deleteAccount(@PathVariable Long id) {
    finance.deleteAccount(id);
    return ResponseEntity.ok(Map.of("deleted", id));
  }

  // ------------------------------------------------------------ transactions

  @GetMapping("/accounts/{id}/transactions")
  public List<TransactionResponse> listByAccount(@PathVariable Long id) {
    return finance.listByAccount(id).stream().map(TransactionResponse::from).toList();
  }

  /**
   * Every transaction, paged and filtered, for browsing rather than reviewing.
   *
   * @param txnType optional: SPEND, INCOME, TRANSFER or PAYMENT. Filtering to INCOME is how you
   *     find out why an expected-income figure looks wrong
   * @param sort {@code amount} for biggest-first by absolute value, anything else for newest-first
   */
  @GetMapping("/transactions")
  public Map<String, Object> browse(
      @RequestParam(required = false) Long accountId,
      @RequestParam(required = false) String txnType,
      @RequestParam(defaultValue = "false") boolean uncategorizedOnly,
      @RequestParam(defaultValue = "date") String sort,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {

    if (page < 0) {
      throw new BadRequestException("page cannot be negative");
    }
    if (size < 1 || size > 200) {
      throw new BadRequestException("size must be between 1 and 200");
    }

    FinanceService.TransactionPage found =
        finance.browse(
            accountId,
            optionalTxnType(txnType),
            uncategorizedOnly ? CategorySource.UNCATEGORIZED : null,
            page,
            size,
            "amount".equalsIgnoreCase(sort));

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("items", found.items().stream().map(TransactionResponse::from).toList());
    body.put("page", found.page());
    body.put("size", found.size());
    body.put("totalElements", found.totalElements());
    body.put("totalPages", found.totalPages());
    return body;
  }

  /**
   * Re-runs the type classifier over every transaction.
   *
   * <p>For after the classifier is corrected: the fix reaches future imports on its own, but months
   * of already-imported rows keep whatever the old rule decided until this is run.
   */
  @PostMapping("/transactions/reclassify")
  public FinanceService.ReclassifyResult reclassifyAll() {
    return finance.reclassifyAll();
  }

  /** The review inbox: rows nothing has confidently categorized yet. */
  @GetMapping("/transactions/uncategorized")
  public List<TransactionResponse> uncategorized() {
    return finance.listUncategorized().stream().map(TransactionResponse::from).toList();
  }

  @PostMapping("/transactions")
  public ResponseEntity<?> addTransaction(@RequestBody TransactionRequest body) {
    if (body.accountId() == null) {
      throw new BadRequestException("accountId is required");
    }
    if (body.amount() == null || body.amount().signum() == 0) {
      throw new BadRequestException("amount is required and cannot be zero");
    }
    String description = requireDescription(body.description());
    LocalDate posted = requireDate(body.postedDate(), "postedDate");

    Optional<Transaction> saved =
        finance.addTransaction(
            body.accountId(),
            posted,
            optionalDate(body.transactionDate()),
            body.amount(),
            description,
            optionalTxnType(body.txnType()),
            body.notes());

    return saved
        .<ResponseEntity<?>>map(t -> ResponseEntity.ok(TransactionResponse.from(t)))
        .orElseGet(
            () ->
                ResponseEntity.status(409)
                    .body(Map.of("error", "an identical transaction already exists")));
  }

  @PatchMapping("/transactions/{id}/category")
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
   *
   * @return the updated row, plus the rule if one was created and {@code ruleExisted} when a rule
   *     for that merchant was already on file
   */
  @PostMapping("/transactions/{id}/categorize")
  public Map<String, Object> categorizeAndLearn(
      @PathVariable Long id, @RequestBody CategorizeAndLearnRequest body) {
    if (body.category() == null || body.category().isBlank()) {
      throw new BadRequestException("a category is required");
    }
    Transaction saved = finance.categorize(id, body.category());
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("transaction", TransactionResponse.from(saved));

    if (Boolean.TRUE.equals(body.createRule())) {
      Optional<CategoryRule> rule = rules.promote(id, body.category());
      response.put("rule", rule.map(RuleResponse::from).orElse(null));
      response.put("ruleExisted", rule.isEmpty());
    }
    return response;
  }

  @PatchMapping("/transactions/{id}/type")
  public TransactionResponse reclassify(
      @PathVariable Long id, @RequestBody ReclassifyRequest body) {
    TxnType type = optionalTxnType(body.txnType());
    if (type == null) {
      throw new BadRequestException("txnType must be SPEND, INCOME, TRANSFER or PAYMENT");
    }
    return TransactionResponse.from(finance.reclassify(id, type));
  }

  @DeleteMapping("/transactions/{id}")
  public ResponseEntity<?> deleteTransaction(@PathVariable Long id) {
    finance.deleteTransaction(id);
    return ResponseEntity.ok(Map.of("deleted", id));
  }

  // ------------------------------------------------------------------ goals

  @GetMapping("/goals")
  public List<GoalResponse> listGoals(
      @RequestParam(defaultValue = "false") boolean includeInactive) {
    return finance.listGoals(includeInactive).stream().map(this::toGoalResponse).toList();
  }

  @PostMapping("/goals")
  public GoalResponse createGoal(@RequestBody GoalRequest body) {
    return toGoalResponse(
        finance.createGoal(
            requireName(body.name()),
            requireTarget(body.targetAmount()),
            optionalDate(body.targetDate()),
            body.note(),
            body.sortOrder() == null ? 0 : body.sortOrder()));
  }

  @PutMapping("/goals/{id}")
  public GoalResponse updateGoal(@PathVariable Long id, @RequestBody GoalRequest body) {
    return toGoalResponse(
        finance.updateGoal(
            id,
            requireName(body.name()),
            requireTarget(body.targetAmount()),
            optionalDate(body.targetDate()),
            body.note(),
            body.active() == null || body.active(),
            body.sortOrder() == null ? 0 : body.sortOrder()));
  }

  @DeleteMapping("/goals/{id}")
  public ResponseEntity<?> deleteGoal(@PathVariable Long id) {
    finance.deleteGoal(id);
    return ResponseEntity.ok(Map.of("deleted", id));
  }

  // ---------- mapping ----------

  private AccountResponse toAccountResponse(Account a) {
    return AccountResponse.from(a, finance.transactionCount(a.getId()));
  }

  private GoalResponse toGoalResponse(SavingsGoal g) {
    return toGoalResponse(g, finance.savingsBalance());
  }

  private GoalResponse toGoalResponse(SavingsGoal g, BigDecimal savings) {
    return GoalResponse.from(g, savings, finance.progressPercent(g, savings));
  }

  // ---------- validation ----------

  private static String requireName(String value) {
    String name = value == null ? "" : value.trim();
    if (name.isBlank() || name.length() > MAX_NAME_CHARS) {
      throw new BadRequestException("a name is required (max " + MAX_NAME_CHARS + " chars)");
    }
    return name;
  }

  private static String requireDescription(String value) {
    String description = value == null ? "" : value.trim();
    if (description.isBlank() || description.length() > MAX_DESCRIPTION_CHARS) {
      throw new BadRequestException(
          "a description is required (max " + MAX_DESCRIPTION_CHARS + " chars)");
    }
    return description;
  }

  private static BigDecimal requireTarget(BigDecimal value) {
    if (value == null || value.signum() <= 0) {
      throw new BadRequestException("targetAmount must be greater than zero");
    }
    return value;
  }

  private static Institution requireInstitution(String value) {
    try {
      return Institution.valueOf(value == null ? "" : value.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new BadRequestException(
          "institution must be one of CAPITAL_ONE, CHASE, WELLS_FARGO, "
              + "BANK_OF_AMERICA, AIDVANTAGE, OTHER");
    }
  }

  private static AccountType requireAccountType(String value) {
    try {
      return AccountType.valueOf(value == null ? "" : value.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new BadRequestException("accountType must be CHECKING, SAVINGS, CREDIT_CARD or LOAN");
    }
  }

  private static MatchType matchType(String value) {
    if (value == null || value.isBlank()) {
      return MatchType.CONTAINS;
    }
    try {
      return MatchType.valueOf(value.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new BadRequestException("matchType must be CONTAINS, EQUALS or REGEX");
    }
  }

  private static TxnType optionalTxnType(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return TxnType.valueOf(value.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new BadRequestException("txnType must be SPEND, INCOME, TRANSFER or PAYMENT");
    }
  }

  private static LocalDate requireDate(String value, String field) {
    LocalDate parsed = optionalDate(value);
    if (parsed == null) {
      throw new BadRequestException(field + " is required as YYYY-MM-DD");
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
      throw new BadRequestException("dates must be YYYY-MM-DD");
    }
  }
}
