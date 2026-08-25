package dev.grindtrack.finance.api;

import dev.grindtrack.finance.api.FinanceDtos.AccountRequest;
import dev.grindtrack.finance.api.FinanceDtos.AccountResponse;
import dev.grindtrack.finance.api.FinanceDtos.BalanceRequest;
import dev.grindtrack.finance.api.FinanceDtos.CategorizeRequest;
import dev.grindtrack.finance.api.FinanceDtos.GoalRequest;
import dev.grindtrack.finance.api.FinanceDtos.GoalResponse;
import dev.grindtrack.finance.api.FinanceDtos.ReclassifyRequest;
import dev.grindtrack.finance.api.FinanceDtos.SummaryResponse;
import dev.grindtrack.finance.api.FinanceDtos.TransactionRequest;
import dev.grindtrack.finance.api.FinanceDtos.TransactionResponse;
import dev.grindtrack.finance.domain.Account;
import dev.grindtrack.finance.domain.AccountType;
import dev.grindtrack.finance.domain.Institution;
import dev.grindtrack.finance.domain.SavingsGoal;
import dev.grindtrack.finance.domain.Transaction;
import dev.grindtrack.finance.domain.TransactionRepository;
import dev.grindtrack.finance.domain.TxnType;
import dev.grindtrack.finance.service.FinanceService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
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
  private final TransactionRepository transactions;

  public FinanceController(FinanceService finance, TransactionRepository transactions) {
    this.finance = finance;
    this.transactions = transactions;
  }

  // ------------------------------------------------------------- dashboard

  @GetMapping("/summary")
  public SummaryResponse summary() {
    List<AccountResponse> accounts =
        finance.listAccounts(false).stream().map(this::toAccountResponse).toList();
    List<GoalResponse> goals = finance.listGoals(false).stream().map(this::toGoalResponse).toList();
    return new SummaryResponse(
        finance.savingsBalance(),
        finance.netWorth(),
        goals,
        accounts,
        finance.listUncategorized().size());
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
      throw new BadRequest("balance is required");
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

  /** The review inbox: rows nothing has confidently categorized yet. */
  @GetMapping("/transactions/uncategorized")
  public List<TransactionResponse> uncategorized() {
    return finance.listUncategorized().stream().map(TransactionResponse::from).toList();
  }

  @PostMapping("/transactions")
  public ResponseEntity<?> addTransaction(@RequestBody TransactionRequest body) {
    if (body.accountId() == null) {
      throw new BadRequest("accountId is required");
    }
    if (body.amount() == null || body.amount().signum() == 0) {
      throw new BadRequest("amount is required and cannot be zero");
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

  @PatchMapping("/transactions/{id}/type")
  public TransactionResponse reclassify(
      @PathVariable Long id, @RequestBody ReclassifyRequest body) {
    TxnType type = optionalTxnType(body.txnType());
    if (type == null) {
      throw new BadRequest("txnType must be SPEND, INCOME, TRANSFER or PAYMENT");
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
    return AccountResponse.from(a, transactions.countByAccountId(a.getId()));
  }

  private GoalResponse toGoalResponse(SavingsGoal g) {
    return GoalResponse.from(g, finance.savingsBalance(), finance.progressPercent(g));
  }

  // ---------- validation ----------

  private static String requireName(String value) {
    String name = value == null ? "" : value.trim();
    if (name.isBlank() || name.length() > MAX_NAME_CHARS) {
      throw new BadRequest("a name is required (max " + MAX_NAME_CHARS + " chars)");
    }
    return name;
  }

  private static String requireDescription(String value) {
    String description = value == null ? "" : value.trim();
    if (description.isBlank() || description.length() > MAX_DESCRIPTION_CHARS) {
      throw new BadRequest("a description is required (max " + MAX_DESCRIPTION_CHARS + " chars)");
    }
    return description;
  }

  private static BigDecimal requireTarget(BigDecimal value) {
    if (value == null || value.signum() <= 0) {
      throw new BadRequest("targetAmount must be greater than zero");
    }
    return value;
  }

  private static Institution requireInstitution(String value) {
    try {
      return Institution.valueOf(value == null ? "" : value.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new BadRequest(
          "institution must be one of CAPITAL_ONE, CHASE, WELLS_FARGO, "
              + "BANK_OF_AMERICA, AIDVANTAGE, OTHER");
    }
  }

  private static AccountType requireAccountType(String value) {
    try {
      return AccountType.valueOf(value == null ? "" : value.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new BadRequest("accountType must be CHECKING, SAVINGS, CREDIT_CARD or LOAN");
    }
  }

  private static TxnType optionalTxnType(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return TxnType.valueOf(value.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new BadRequest("txnType must be SPEND, INCOME, TRANSFER or PAYMENT");
    }
  }

  private static LocalDate requireDate(String value, String field) {
    LocalDate parsed = optionalDate(value);
    if (parsed == null) {
      throw new BadRequest(field + " is required as YYYY-MM-DD");
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
      throw new BadRequest("dates must be YYYY-MM-DD");
    }
  }

  @ExceptionHandler(BadRequest.class)
  ResponseEntity<Map<String, String>> onBadRequest(BadRequest e) {
    return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
  }

  @ExceptionHandler(NoSuchElementException.class)
  ResponseEntity<Map<String, String>> onNotFound(NoSuchElementException e) {
    return ResponseEntity.status(404).body(Map.of("error", "not found: " + e.getMessage()));
  }

  private static final class BadRequest extends RuntimeException {
    BadRequest(String message) {
      super(message);
    }
  }
}
