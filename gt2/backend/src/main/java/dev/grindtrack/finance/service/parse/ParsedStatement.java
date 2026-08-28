package dev.grindtrack.finance.service.parse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What a parser produces from one uploaded file, before any of it touches the database.
 *
 * @param format which parser handled it
 * @param rows the transactions found, already normalized to the app's conventions
 * @param closingBalance the account balance the statement itself asserts, when it carries one.
 *     Capital One's deposit exports include a running balance column, and Aidvantage reports unpaid
 *     principal — both are more authoritative than a hand-typed figure, so the import uses them to
 *     refresh the account.
 * @param balanceAsOf the date that closing balance applies to
 * @param cardNumbers distinct card numbers seen in the file. Capital One's credit exports name the
 *     card, which is the only way to catch a Savor statement being uploaded into Quicksilver — a
 *     genuine risk with three cards at the same bank.
 * @param pendingSkipped rows held back because the bank had not settled them yet
 */
public record ParsedStatement(
    StatementFormat format,
    List<ParsedRow> rows,
    BigDecimal closingBalance,
    LocalDate balanceAsOf,
    List<String> cardNumbers,
    int pendingSkipped) {

  public LocalDate periodStart() {
    return rows.stream().map(ParsedRow::postedDate).min(LocalDate::compareTo).orElse(null);
  }

  public LocalDate periodEnd() {
    return rows.stream().map(ParsedRow::postedDate).max(LocalDate::compareTo).orElse(null);
  }
}
