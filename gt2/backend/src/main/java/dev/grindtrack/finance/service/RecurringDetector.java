package dev.grindtrack.finance.service;

import dev.grindtrack.finance.domain.Transaction;
import dev.grindtrack.finance.domain.TransactionRepository;
import dev.grindtrack.finance.domain.TxnType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Finds the charges that come back every month.
 *
 * <p>This is the shortcut into a budget. Setting one up from a blank page means guessing at
 * numbers; setting one up from "here are the eleven things that bill you every month and what they
 * actually cost" takes a couple of minutes and is right the first time.
 *
 * <p>It is also the subscription audit nobody gets round to doing. A charge that has run monthly
 * for eight months and stopped three months ago is either cancelled or about to surprise you, and
 * one you do not recognize at all is the entire point of looking.
 *
 * <p>Detection is deliberately conservative. Three occurrences minimum, and the gaps between them
 * have to actually look like a cadence — anything ambiguous is left out rather than presented as a
 * commitment, because a wrong entry here becomes a wrong budget line.
 */
@Service
public class RecurringDetector {

  /**
   * Two points make a line through any two dates. Three is the first number that means anything.
   */
  private static final int MIN_OCCURRENCES = 3;

  /** How far back to look. Long enough to catch quarterly bills, short enough to stay current. */
  private static final int LOOKBACK_MONTHS = 8;

  /** A charge not seen in this long is reported as lapsed rather than as a live commitment. */
  private static final int LAPSED_AFTER_DAYS = 75;

  private final TransactionRepository transactions;

  public RecurringDetector(TransactionRepository transactions) {
    this.transactions = transactions;
  }

  /**
   * One merchant that bills on a rhythm.
   *
   * @param typicalAmount the median charge, positive. Median rather than mean because one annual
   *     renewal among eleven monthly charges would drag a mean somewhere that describes no actual
   *     transaction
   * @param monthlyEquivalent what this costs per month once cadence is accounted for, so a
   *     quarterly bill and a monthly one can be added together and compared
   * @param variable true when the amount moves around — a utility rather than a subscription. Worth
   *     knowing before treating the typical amount as a budget line
   * @param lapsed true when nothing has arrived for a while: cancelled, or about to reappear
   */
  public record Recurring(
      String merchant,
      String category,
      String cadence,
      int occurrences,
      BigDecimal typicalAmount,
      BigDecimal monthlyEquivalent,
      BigDecimal lowest,
      BigDecimal highest,
      boolean variable,
      boolean lapsed,
      String firstSeen,
      String lastSeen,
      String nextExpected) {}

  /**
   * @param monthlyCommitment everything live, expressed per month. The fixed nut, in one number
   */
  public record RecurringReport(
      BigDecimal monthlyCommitment, int liveCount, int lapsedCount, List<Recurring> items) {}

  public RecurringReport detect() {
    LocalDate to = LocalDate.now();
    LocalDate from = to.minusMonths(LOOKBACK_MONTHS);

    Map<String, List<Transaction>> byMerchant = new LinkedHashMap<>();
    for (Transaction t : transactions.findCountableBetween(from, to)) {
      if (t.getTxnType() != TxnType.SPEND || t.getMerchant() == null) {
        continue;
      }
      byMerchant.computeIfAbsent(t.getMerchant(), k -> new ArrayList<>()).add(t);
    }

    List<Recurring> found = new ArrayList<>();
    for (Map.Entry<String, List<Transaction>> entry : byMerchant.entrySet()) {
      Recurring recurring = analyse(entry.getKey(), entry.getValue(), to);
      if (recurring != null) {
        found.add(recurring);
      }
    }

    found.sort(Comparator.comparing(Recurring::monthlyEquivalent).reversed());

    BigDecimal commitment =
        found.stream()
            .filter(r -> !r.lapsed())
            .map(Recurring::monthlyEquivalent)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    return new RecurringReport(
        commitment,
        (int) found.stream().filter(r -> !r.lapsed()).count(),
        (int) found.stream().filter(Recurring::lapsed).count(),
        found);
  }

  private Recurring analyse(String merchant, List<Transaction> rows, LocalDate today) {
    if (rows.size() < MIN_OCCURRENCES) {
      return null;
    }
    rows.sort(Comparator.comparing(Transaction::getPostedDate));

    List<Long> gaps = new ArrayList<>();
    for (int i = 1; i < rows.size(); i++) {
      gaps.add(
          ChronoUnit.DAYS.between(rows.get(i - 1).getPostedDate(), rows.get(i).getPostedDate()));
    }
    long medianGap = medianLong(gaps);
    String cadence = cadenceFor(medianGap);
    if (cadence == null) {
      return null;
    }

    // Gaps have to be consistent, not just average out. Four charges in one week and one six
    // months later have a plausible median and are not a subscription.
    long tolerance = Math.max(4, Math.round(medianGap * 0.4));
    boolean consistent = gaps.stream().allMatch(g -> Math.abs(g - medianGap) <= tolerance);
    if (!consistent) {
      return null;
    }

    List<BigDecimal> amounts = rows.stream().map(t -> t.getAmount().abs()).sorted().toList();
    BigDecimal typical = median(amounts);
    BigDecimal lowest = amounts.get(0);
    BigDecimal highest = amounts.get(amounts.size() - 1);
    if (typical.signum() <= 0) {
      return null;
    }

    // "Variable" means the amount moves enough that budgeting the typical figure would mislead.
    boolean variable =
        highest.subtract(lowest).compareTo(typical.multiply(new BigDecimal("0.25"))) > 0;

    LocalDate lastSeen = rows.get(rows.size() - 1).getPostedDate();
    boolean lapsed = ChronoUnit.DAYS.between(lastSeen, today) > LAPSED_AFTER_DAYS;

    return new Recurring(
        merchant,
        mostCommonCategory(rows),
        cadence,
        rows.size(),
        typical,
        monthlyEquivalent(typical, medianGap),
        lowest,
        highest,
        variable,
        lapsed,
        rows.get(0).getPostedDate().toString(),
        lastSeen.toString(),
        lastSeen.plusDays(medianGap).toString());
  }

  /** Named rhythms only. A 47-day gap is not a cadence, it is a coincidence. */
  private static String cadenceFor(long days) {
    if (days >= 6 && days <= 8) {
      return "WEEKLY";
    }
    if (days >= 13 && days <= 16) {
      return "FORTNIGHTLY";
    }
    if (days >= 26 && days <= 35) {
      return "MONTHLY";
    }
    if (days >= 85 && days <= 95) {
      return "QUARTERLY";
    }
    if (days >= 355 && days <= 375) {
      return "YEARLY";
    }
    return null;
  }

  /** Everything expressed per month, so a quarterly bill can sit in the same total as a monthly. */
  private static BigDecimal monthlyEquivalent(BigDecimal amount, long gapDays) {
    return amount
        .multiply(new BigDecimal("30.44"))
        .divide(BigDecimal.valueOf(Math.max(gapDays, 1)), 2, RoundingMode.HALF_UP);
  }

  private static String mostCommonCategory(List<Transaction> rows) {
    Map<String, Integer> counts = new LinkedHashMap<>();
    for (Transaction t : rows) {
      if (t.getCategory() != null) {
        counts.merge(t.getCategory(), 1, Integer::sum);
      }
    }
    return counts.entrySet().stream()
        .max(Map.Entry.comparingByValue())
        .map(Map.Entry::getKey)
        .orElse(null);
  }

  private static long medianLong(List<Long> values) {
    List<Long> sorted = values.stream().sorted().toList();
    int mid = sorted.size() / 2;
    return sorted.size() % 2 == 1
        ? sorted.get(mid)
        : Math.round((sorted.get(mid - 1) + sorted.get(mid)) / 2.0);
  }

  private static BigDecimal median(List<BigDecimal> sorted) {
    int mid = sorted.size() / 2;
    return sorted.size() % 2 == 1
        ? sorted.get(mid)
        : sorted.get(mid - 1).add(sorted.get(mid)).divide(BigDecimal.TWO, 2, RoundingMode.HALF_UP);
  }
}
