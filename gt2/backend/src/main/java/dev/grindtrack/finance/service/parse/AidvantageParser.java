package dev.grindtrack.finance.service.parse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Aidvantage student loans — and the one format that deliberately imports <em>no transactions</em>.
 *
 * <p>{@code Date,LoanName,Description,Principal,Interest,Fees,Total,UnpaidPrincipalBalanceValue}
 *
 * <p>The file is a sub-ledger, not a statement. A single $572.40 monthly payment appears here as
 * twelve rows, one per loan, splitting that payment into principal and interest per loan. The real
 * money movement is already captured as one {@code ADVS ED SERV} debit on the Capital One checking
 * account. Importing these rows as transactions would count the same payment twice — once in
 * checking and again, spread across twelve lines, here. Across the downloaded history that is 296
 * phantom rows totalling $13,737.
 *
 * <p>What the file <em>is</em> good for is the balance. Each row carries the unpaid principal for
 * its loan at that date, so the most recent row per loan, summed, is exactly what is still owed.
 * That figure updates the loan account and flows into net worth.
 *
 * <p>The file also arrives with an HTML doctype glued onto the front of the header line, which the
 * import strips before parsing.
 */
@Component
public class AidvantageParser implements StatementParser {

  @Override
  public StatementFormat format() {
    return StatementFormat.AIDVANTAGE;
  }

  @Override
  public boolean canParse(List<String> header) {
    return header.contains("LoanName") && header.contains("UnpaidPrincipalBalanceValue");
  }

  @Override
  public ParsedStatement parse(List<List<String>> rows) {
    List<String> header = rows.get(0).stream().map(String::trim).toList();
    int cDate = header.indexOf("Date");
    int cLoan = header.indexOf("LoanName");
    int cUnpaid = header.indexOf("UnpaidPrincipalBalanceValue");

    if (cDate < 0 || cLoan < 0 || cUnpaid < 0) {
      throw new StatementParseException(
          "This looks like an Aidvantage export but is missing a required column.");
    }

    // Latest row per loan wins; that row's unpaid principal is the current balance.
    Map<String, LocalDate> latestDate = new HashMap<>();
    Map<String, BigDecimal> latestBalance = new HashMap<>();

    for (List<String> row : rows.subList(1, rows.size())) {
      LocalDate date = Amounts.date(Csv.at(row, cDate));
      String loan = Csv.at(row, cLoan);
      BigDecimal unpaid = Amounts.money(Csv.at(row, cUnpaid));
      if (date == null || loan.isEmpty() || unpaid == null) {
        continue;
      }
      LocalDate seen = latestDate.get(loan);
      if (seen == null || date.isAfter(seen)) {
        latestDate.put(loan, date);
        latestBalance.put(loan, unpaid);
      }
    }

    if (latestBalance.isEmpty()) {
      throw new StatementParseException("No readable loan balances in this file.");
    }

    BigDecimal owed = latestBalance.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    LocalDate asOf = latestDate.values().stream().max(LocalDate::compareTo).orElse(null);

    // No transactions on purpose — see the class comment.
    return new ParsedStatement(format(), List.of(), owed, asOf, List.of(), 0);
  }
}
