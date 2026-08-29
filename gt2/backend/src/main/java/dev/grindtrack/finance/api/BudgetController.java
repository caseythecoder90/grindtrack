package dev.grindtrack.finance.api;

import dev.grindtrack.finance.domain.Budget;
import dev.grindtrack.finance.domain.BudgetExtra;
import dev.grindtrack.finance.service.BudgetService;
import dev.grindtrack.finance.service.BudgetService.MonthView;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
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

  private static final int MAX_LABEL_CHARS = 120;

  private final BudgetService budget;

  public BudgetController(BudgetService budget) {
    this.budget = budget;
  }

  public record LineRequest(
      String category, BigDecimal monthlyAmount, String note, Boolean active, Integer sortOrder) {}

  public record LineResponse(
      Long id,
      String category,
      BigDecimal monthlyAmount,
      String note,
      boolean active,
      int sortOrder) {

    static LineResponse from(Budget b) {
      return new LineResponse(
          b.getId(),
          b.getCategory(),
          b.getMonthlyAmount(),
          b.getNote(),
          b.isActive(),
          b.getSortOrder());
    }
  }

  /**
   * @param amount negative for a one-off cost, positive for one-off money in
   * @param category optional; when set, the cost counts against that category for the month
   */
  public record ExtraRequest(
      String month, String label, BigDecimal amount, String category, String note) {}

  public record ExtraResponse(
      Long id, String month, String label, BigDecimal amount, String category, String note) {

    static ExtraResponse from(BudgetExtra e) {
      return new ExtraResponse(
          e.getId(),
          YearMonth.from(e.getMonth()).toString(),
          e.getLabel(),
          e.getAmount(),
          e.getCategory(),
          e.getNote());
    }
  }

  public record IncomeRequest(BigDecimal expectedMonthlyIncome) {}

  // ------------------------------------------------------------- the month

  /**
   * @param month {@code yyyy-MM}; defaults to the current month
   */
  @GetMapping("/month")
  public MonthView month(@RequestParam(required = false) String month) {
    return budget.month(parseMonth(month));
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
  public ResponseEntity<?> deleteLine(@PathVariable Long id) {
    budget.delete(id);
    return ResponseEntity.ok(Map.of("deleted", id));
  }

  // ------------------------------------------------------- this month only

  /**
   * @param from {@code yyyy-MM}; defaults to the current month, so the list reads as "what is still
   *     coming" rather than a history of one-offs already absorbed
   */
  @GetMapping("/extras")
  public List<ExtraResponse> extras(@RequestParam(required = false) String from) {
    return budget.extrasFrom(parseMonth(from)).stream().map(ExtraResponse::from).toList();
  }

  @PostMapping("/extras")
  public ExtraResponse createExtra(@RequestBody ExtraRequest body) {
    return ExtraResponse.from(
        budget.addExtra(
            parseMonth(body.month()),
            requireLabel(body.label()),
            body.amount(),
            body.category(),
            body.note()));
  }

  @PutMapping("/extras/{id}")
  public ExtraResponse updateExtra(@PathVariable Long id, @RequestBody ExtraRequest body) {
    return ExtraResponse.from(
        budget.updateExtra(
            id,
            parseMonth(body.month()),
            requireLabel(body.label()),
            body.amount(),
            body.category(),
            body.note()));
  }

  @DeleteMapping("/extras/{id}")
  public ResponseEntity<?> deleteExtra(@PathVariable Long id) {
    budget.deleteExtra(id);
    return ResponseEntity.ok(Map.of("deleted", id));
  }

  // ------------------------------------------------------------------ income

  /** Null or zero clears the override and goes back to the trailing average of real deposits. */
  @PutMapping("/income")
  public Map<String, Object> setIncome(@RequestBody IncomeRequest body) {
    BigDecimal saved =
        budget.setExpectedIncome(body.expectedMonthlyIncome()).getExpectedMonthlyIncome();
    return Map.of(
        "expectedMonthlyIncome",
        saved == null ? "" : saved.toPlainString(),
        "estimated",
        saved == null);
  }

  // ---------------------------------------------------------------- helpers

  private static YearMonth parseMonth(String value) {
    if (value == null || value.isBlank()) {
      return YearMonth.now();
    }
    try {
      return YearMonth.parse(value.trim());
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException("month must be yyyy-MM, for example 2026-08");
    }
  }

  private static String requireLabel(String value) {
    String label = value == null ? "" : value.trim();
    if (label.isBlank() || label.length() > MAX_LABEL_CHARS) {
      throw new IllegalArgumentException(
          "a label is required (max "
              + MAX_LABEL_CHARS
              + " chars), so the number explains itself"
              + " when you look back at it");
    }
    return label;
  }
}
