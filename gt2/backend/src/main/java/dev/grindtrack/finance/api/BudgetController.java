package dev.grindtrack.finance.api;

import dev.grindtrack.finance.api.BudgetDtos.ExtraRequest;
import dev.grindtrack.finance.api.BudgetDtos.ExtraResponse;
import dev.grindtrack.finance.api.BudgetDtos.IncomeRequest;
import dev.grindtrack.finance.api.BudgetDtos.IncomeResponse;
import dev.grindtrack.finance.api.BudgetDtos.LineRequest;
import dev.grindtrack.finance.api.BudgetDtos.LineResponse;
import dev.grindtrack.finance.service.BudgetMonth;
import dev.grindtrack.finance.service.BudgetService;
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
 * The monthly budget.
 *
 * <p>Separate from {@link FinanceController} because a budget is a plan and everything there is a
 * record of what happened. The two only meet in {@link BudgetService#month}, which reconciles them.
 */
@RestController
@RequestMapping("/api/finance/budget")
public class BudgetController {

  /** A label long enough to say what the number was for, short enough to read in the list. */
  private static final int MAX_LABEL_CHARS = 120;

  private static final String LABEL_REQUIRED =
      "label is required, so the number explains itself when you look back at it";

  private final BudgetService budget;

  public BudgetController(BudgetService budget) {
    this.budget = budget;
  }

  // ------------------------------------------------------------- the month

  /**
   * @param month {@code yyyy-MM}; defaults to the current month
   */
  @GetMapping("/month")
  public BudgetMonth month(@RequestParam(required = false) String month) {
    return budget.month(Requests.monthOrNow(month));
  }

  // -------------------------------------------------------------- the plan

  @GetMapping("/lines")
  public List<LineResponse> lines(@RequestParam(defaultValue = "true") boolean includeInactive) {
    return budget.list(includeInactive).stream().map(LineResponse::from).toList();
  }

  @PostMapping("/lines")
  public LineResponse createLine(@RequestBody LineRequest body) {
    return LineResponse.from(
        budget.create(
            body.category(),
            body.monthlyAmount(),
            body.note(),
            body.sortOrder() == null ? 0 : body.sortOrder()));
  }

  @PutMapping("/lines/{id}")
  public LineResponse updateLine(@PathVariable Long id, @RequestBody LineRequest body) {
    return LineResponse.from(
        budget.update(
            id,
            body.category(),
            body.monthlyAmount(),
            body.note(),
            body.active() == null || body.active(),
            body.sortOrder() == null ? 0 : body.sortOrder()));
  }

  @DeleteMapping("/lines/{id}")
  public Deleted deleteLine(@PathVariable Long id) {
    budget.delete(id);
    return Deleted.of(id);
  }

  // ------------------------------------------------------- this month only

  /**
   * @param from {@code yyyy-MM}; defaults to the current month, so the list reads as "what is still
   *     coming" rather than a history of one-offs already absorbed
   */
  @GetMapping("/extras")
  public List<ExtraResponse> extras(@RequestParam(required = false) String from) {
    return budget.extrasFrom(Requests.monthOrNow(from)).stream().map(ExtraResponse::from).toList();
  }

  @PostMapping("/extras")
  public ExtraResponse createExtra(@RequestBody ExtraRequest body) {
    return ExtraResponse.from(
        budget.addExtra(
            Requests.monthOrNow(body.month()),
            Requests.requireText(body.label(), LABEL_REQUIRED, MAX_LABEL_CHARS),
            body.amount(),
            body.category(),
            body.note()));
  }

  @PutMapping("/extras/{id}")
  public ExtraResponse updateExtra(@PathVariable Long id, @RequestBody ExtraRequest body) {
    return ExtraResponse.from(
        budget.updateExtra(
            id,
            Requests.monthOrNow(body.month()),
            Requests.requireText(body.label(), LABEL_REQUIRED, MAX_LABEL_CHARS),
            body.amount(),
            body.category(),
            body.note()));
  }

  @DeleteMapping("/extras/{id}")
  public Deleted deleteExtra(@PathVariable Long id) {
    budget.deleteExtra(id);
    return Deleted.of(id);
  }

  // ------------------------------------------------------------------ income

  /** Null or zero clears the override and goes back to the trailing average of real deposits. */
  @PutMapping("/income")
  public IncomeResponse setIncome(@RequestBody IncomeRequest body) {
    return IncomeResponse.of(
        budget.setExpectedIncome(body.expectedMonthlyIncome()).getExpectedMonthlyIncome());
  }
}
