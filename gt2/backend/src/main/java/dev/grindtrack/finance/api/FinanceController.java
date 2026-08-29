package dev.grindtrack.finance.api;

import dev.grindtrack.finance.api.FinanceDtos.AccountRequest;
import dev.grindtrack.finance.api.FinanceDtos.AccountResponse;
import dev.grindtrack.finance.api.FinanceDtos.BalanceRequest;
import dev.grindtrack.finance.api.FinanceDtos.GoalRequest;
import dev.grindtrack.finance.api.FinanceDtos.GoalResponse;
import dev.grindtrack.finance.api.FinanceDtos.SummaryResponse;
import dev.grindtrack.finance.api.FinanceDtos.TransactionResponse;
import dev.grindtrack.finance.domain.Account;
import dev.grindtrack.finance.domain.AccountType;
import dev.grindtrack.finance.domain.Institution;
import dev.grindtrack.finance.domain.SavingsGoal;
import dev.grindtrack.finance.service.FinanceService;
import dev.grindtrack.web.BadRequestException;
import dev.grindtrack.web.Requests;
import dev.grindtrack.web.Responses.Deleted;
import java.math.BigDecimal;
import java.util.List;
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
 * What you own and what you are saving toward: the dashboard summary, accounts and savings goals.
 *
 * <p>This class used to be the whole of {@code /api/finance} — five hundred lines over five
 * unrelated resources, injecting three services. Transactions and category rules are now {@link
 * TransactionController} and {@link CategoryRuleController}, which leaves one service here and a
 * file you can read end to end.
 */
@RestController
@RequestMapping("/api/finance")
public class FinanceController {

  private static final int MAX_NAME_CHARS = 120;

  private final FinanceService finance;

  public FinanceController(FinanceService finance) {
    this.finance = finance;
  }

  // ------------------------------------------------------------- dashboard

  @GetMapping("/summary")
  public SummaryResponse summary() {
    // Read the savings total once and hand it down: every goal needs it twice, and re-querying it
    // per goal made the dashboard issue the same SUM four or five times per load.
    BigDecimal savings = finance.savingsBalance();
    return new SummaryResponse(
        savings,
        finance.netWorth(),
        finance.listGoals(false).stream().map(g -> GoalResponse.from(g, savings)).toList(),
        accountResponses(false),
        finance.listUncategorized().size());
  }

  // ---------------------------------------------------------------- accounts

  @GetMapping("/accounts")
  public List<AccountResponse> listAccounts(
      @RequestParam(defaultValue = "false") boolean includeInactive) {
    return accountResponses(includeInactive);
  }

  @PostMapping("/accounts")
  public AccountResponse createAccount(@RequestBody AccountRequest body) {
    return accountResponse(
        finance.createAccount(
            Requests.requireText(body.name(), "name is required", MAX_NAME_CHARS),
            Requests.enumValue(Institution.class, body.institution(), "institution"),
            Requests.enumValue(AccountType.class, body.accountType(), "accountType"),
            body.last4(),
            Boolean.TRUE.equals(body.countsTowardSavings()),
            body.sortOrder() == null ? 0 : body.sortOrder()));
  }

  @PutMapping("/accounts/{id}")
  public AccountResponse updateAccount(@PathVariable Long id, @RequestBody AccountRequest body) {
    return accountResponse(
        finance.updateAccount(
            id,
            Requests.requireText(body.name(), "name is required", MAX_NAME_CHARS),
            Requests.enumValue(Institution.class, body.institution(), "institution"),
            Requests.enumValue(AccountType.class, body.accountType(), "accountType"),
            body.last4(),
            Boolean.TRUE.equals(body.countsTowardSavings()),
            body.active() == null || body.active(),
            body.sortOrder() == null ? 0 : body.sortOrder()));
  }

  /** Records a balance reading. Sign is corrected server-side for cards and loans. */
  @PatchMapping("/accounts/{id}/balance")
  public AccountResponse recordBalance(@PathVariable Long id, @RequestBody BalanceRequest body) {
    if (body.balance() == null) {
      throw new BadRequestException("balance is required");
    }
    return accountResponse(
        finance.recordBalance(id, body.balance(), Requests.optionalDate(body.asOf())));
  }

  @DeleteMapping("/accounts/{id}")
  public Deleted deleteAccount(@PathVariable Long id) {
    finance.deleteAccount(id);
    return Deleted.of(id);
  }

  /** One account's transactions, newest first — a sub-resource of the account, not a search. */
  @GetMapping("/accounts/{id}/transactions")
  public List<TransactionResponse> listByAccount(@PathVariable Long id) {
    return finance.listByAccount(id).stream().map(TransactionResponse::from).toList();
  }

  // ------------------------------------------------------------------ goals

  @GetMapping("/goals")
  public List<GoalResponse> listGoals(
      @RequestParam(defaultValue = "false") boolean includeInactive) {
    BigDecimal savings = finance.savingsBalance();
    return finance.listGoals(includeInactive).stream()
        .map(g -> GoalResponse.from(g, savings))
        .toList();
  }

  @PostMapping("/goals")
  public GoalResponse createGoal(@RequestBody GoalRequest body) {
    return goalResponse(
        finance.createGoal(
            Requests.requireText(body.name(), "name is required", MAX_NAME_CHARS),
            requireTarget(body.targetAmount()),
            Requests.optionalDate(body.targetDate()),
            body.note(),
            body.sortOrder() == null ? 0 : body.sortOrder()));
  }

  @PutMapping("/goals/{id}")
  public GoalResponse updateGoal(@PathVariable Long id, @RequestBody GoalRequest body) {
    return goalResponse(
        finance.updateGoal(
            id,
            Requests.requireText(body.name(), "name is required", MAX_NAME_CHARS),
            requireTarget(body.targetAmount()),
            Requests.optionalDate(body.targetDate()),
            body.note(),
            body.active() == null || body.active(),
            body.sortOrder() == null ? 0 : body.sortOrder()));
  }

  @DeleteMapping("/goals/{id}")
  public Deleted deleteGoal(@PathVariable Long id) {
    finance.deleteGoal(id);
    return Deleted.of(id);
  }

  // ---------------------------------------------------------------- mapping

  private List<AccountResponse> accountResponses(boolean includeInactive) {
    return finance.listAccounts(includeInactive).stream().map(this::accountResponse).toList();
  }

  private AccountResponse accountResponse(Account a) {
    return AccountResponse.from(a, finance.transactionCount(a.getId()));
  }

  private GoalResponse goalResponse(SavingsGoal goal) {
    return GoalResponse.from(goal, finance.savingsBalance());
  }

  private static BigDecimal requireTarget(BigDecimal value) {
    if (value == null || value.signum() <= 0) {
      throw new BadRequestException("targetAmount must be greater than zero");
    }
    return value;
  }
}
