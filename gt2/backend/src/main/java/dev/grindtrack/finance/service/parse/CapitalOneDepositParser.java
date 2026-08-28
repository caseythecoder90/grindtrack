package dev.grindtrack.finance.service.parse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Capital One 360 Checking and BigTimeSavings.
 *
 * <p>{@code Account Number,Transaction Description,Transaction Date,Transaction Type,Transaction
 * Amount,Balance}
 *
 * <p>Two things make this format distinct. The amount is always positive and the direction lives in
 * a separate {@code Transaction Type} column, so the sign has to be reconstructed. And it carries a
 * running {@code Balance}, which is the most authoritative statement of what the account holds —
 * the import uses the newest row's balance to refresh the account rather than trusting a figure
 * typed in by hand weeks ago.
 */
@Component
public class CapitalOneDepositParser implements StatementParser {

  @Override
  public StatementFormat format() {
    return StatementFormat.CAPITAL_ONE_DEPOSIT;
  }

  @Override
  public boolean canParse(List<String> header) {
    return header.contains("Transaction Type") && header.contains("Balance");
  }

  @Override
  public ParsedStatement parse(List<List<String>> rows) {
    List<String> header = rows.get(0).stream().map(String::trim).toList();
    int cDate = header.indexOf("Transaction Date");
    int cDesc = header.indexOf("Transaction Description");
    int cType = header.indexOf("Transaction Type");
    int cAmount = header.indexOf("Transaction Amount");
    int cBalance = header.indexOf("Balance");

    if (cDate < 0 || cDesc < 0 || cType < 0 || cAmount < 0) {
      throw new StatementParseException(
          "This looks like a Capital One deposit export but is missing a required column.");
    }

    List<ParsedRow> parsed = new ArrayList<>();
    List<List<String>> data = rows.subList(1, rows.size());
    int unreadable = 0;

    // Running balances are only meaningful in file order: the balance on the newest row is the
    // account total *after* that row. When several rows share the newest date, the one that
    // settles last is the one to believe, and which end of the tie that is depends on whether the
    // export runs newest-first (Capital One's own ordering) or oldest-first. Rather than assume,
    // read the ordering off the file and take the correct end.
    LocalDate newest = null;
    BigDecimal newestBalance = null;
    boolean oldestFirst = isOldestFirst(data, cDate);

    for (List<String> row : data) {
      LocalDate date = Amounts.date(Csv.at(row, cDate));
      BigDecimal magnitude = Amounts.money(Csv.at(row, cAmount));
      String description = Csv.at(row, cDesc);
      if (date == null || magnitude == null || description.isEmpty()) {
        unreadable++;
        continue;
      }

      boolean credit = Csv.at(row, cType).equalsIgnoreCase("Credit");
      BigDecimal amount = credit ? Amounts.inflow(magnitude) : Amounts.outflow(magnitude);
      parsed.add(ParsedRow.of(date, amount, description));

      if (cBalance < 0) {
        continue;
      }
      // Newest-first: the first row at the newest date wins. Oldest-first: the last one does.
      boolean better =
          newest == null || date.isAfter(newest) || (oldestFirst && date.isEqual(newest));
      if (better) {
        BigDecimal balance = Amounts.money(Csv.at(row, cBalance));
        if (balance != null) {
          newest = date;
          newestBalance = balance;
        }
      }
    }

    if (parsed.isEmpty()) {
      throw new StatementParseException("No readable transactions in this file.");
    }
    return ParsedStatement.of(format(), parsed, data.size(), unreadable)
        .withBalance(newestBalance, newest);
  }

  /**
   * Reads the file's ordering from its first and last readable dates.
   *
   * <p>Ties and single-row files report false, which lands on Capital One's actual newest-first
   * ordering — the safe default when the file does not say.
   */
  private static boolean isOldestFirst(List<List<String>> data, int cDate) {
    LocalDate first = null;
    LocalDate last = null;
    for (List<String> row : data) {
      LocalDate date = Amounts.date(Csv.at(row, cDate));
      if (date == null) {
        continue;
      }
      if (first == null) {
        first = date;
      }
      last = date;
    }
    return first != null && last != null && last.isAfter(first);
  }
}
