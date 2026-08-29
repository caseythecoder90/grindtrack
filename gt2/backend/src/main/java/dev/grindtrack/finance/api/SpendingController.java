package dev.grindtrack.finance.api;

import dev.grindtrack.finance.service.FinanceService;
import dev.grindtrack.finance.service.FinanceService.MonthTotal;
import dev.grindtrack.finance.service.FinanceService.SpendSummary;
import dev.grindtrack.finance.service.RecurringDetector;
import dev.grindtrack.finance.service.RecurringDetector.RecurringReport;
import dev.grindtrack.web.BadRequestException;
import dev.grindtrack.web.Requests;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only analysis over the transaction history: where the money went, and what goes every month.
 *
 * <p>Split from {@link FinanceController} because nothing here is CRUD — no endpoint takes a body
 * and none of them writes. They answer questions, and the two collaborators are two different ways
 * of answering one.
 */
@RestController
@RequestMapping("/api/finance")
public class SpendingController {

  /** The window a bare {@code /spending} means: recent enough to still be actionable. */
  private static final int DEFAULT_WINDOW_DAYS = 30;

  private static final int MAX_MONTHS = 36;

  private final FinanceService finance;
  private final RecurringDetector recurringDetector;

  public SpendingController(FinanceService finance, RecurringDetector recurringDetector) {
    this.finance = finance;
    this.recurringDetector = recurringDetector;
  }

  /**
   * Where the money went, over a window.
   *
   * <p>Defaults to the last 30 days. Transfers and card payments are excluded by the query, so
   * these totals are spending rather than movement.
   */
  @GetMapping("/spending")
  public SpendSummary spending(
      @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
    LocalDate end = Requests.optionalDate(to);
    if (end == null) {
      end = LocalDate.now();
    }
    LocalDate start = Requests.optionalDate(from);
    if (start == null) {
      start = end.minusDays(DEFAULT_WINDOW_DAYS);
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
  public List<MonthTotal> monthly(@RequestParam(defaultValue = "6") int months) {
    if (months < 1 || months > MAX_MONTHS) {
      throw new BadRequestException("months must be between 1 and " + MAX_MONTHS);
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
  public RecurringReport recurring() {
    return recurringDetector.detect();
  }
}
