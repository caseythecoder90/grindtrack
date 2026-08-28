package dev.grindtrack.finance.service.parse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * What a parser produces from one uploaded file, before any of it touches the database.
 *
 * <p>The three counts exist so an import can never quietly lose rows. Every non-header row a parser
 * sees lands in exactly one of them: it becomes a {@code rows} entry, or it is held back as
 * pending, or it could not be read at all. {@code dataRowCount} is the total, and the import screen
 * shows the arithmetic — 40 in the file, 12 new, 27 already present, 1 pending — so a file that
 * half-fails to parse says so instead of reporting a confident, wrong, smaller number.
 *
 * @param format which parser handled it
 * @param rows the transactions found, already normalized to the app's conventions
 * @param dataRowCount every non-header row the parser saw, readable or not
 * @param pendingSkipped rows held back because the bank had not settled them yet
 * @param unreadableSkipped rows the parser could not make sense of — an unparseable date or amount,
 *     or a missing description. Always worth surfacing: a run of these means a bank changed its
 *     export format.
 * @param closingBalance the account balance the statement itself asserts, when it carries one.
 *     Capital One's deposit exports include a running balance column, and Aidvantage reports unpaid
 *     principal — both are more authoritative than a hand-typed figure, so the import uses them to
 *     refresh the account.
 * @param balanceAsOf the date that closing balance applies to
 * @param cardNumbers distinct card numbers seen in the file. Capital One's credit exports name the
 *     card, which is the only way to catch a Savor statement being uploaded into Quicksilver — a
 *     genuine risk with three cards at the same bank.
 * @param notes parser-level remarks shown to the user alongside the counts, for the cases where a
 *     correct import still looks surprising — Aidvantage importing zero rows on purpose, above all.
 */
public record ParsedStatement(
    StatementFormat format,
    List<ParsedRow> rows,
    int dataRowCount,
    int pendingSkipped,
    int unreadableSkipped,
    BigDecimal closingBalance,
    LocalDate balanceAsOf,
    List<String> cardNumbers,
    List<String> notes) {

  /** The common case: transactions, some possibly unreadable, and nothing else. */
  public static ParsedStatement of(
      StatementFormat format, List<ParsedRow> rows, int dataRowCount, int unreadableSkipped) {
    return new ParsedStatement(
        format, rows, dataRowCount, 0, unreadableSkipped, null, null, List.of(), List.of());
  }

  public ParsedStatement withBalance(BigDecimal balance, LocalDate asOf) {
    return new ParsedStatement(
        format,
        rows,
        dataRowCount,
        pendingSkipped,
        unreadableSkipped,
        balance,
        asOf,
        cardNumbers,
        notes);
  }

  public ParsedStatement withCardNumbers(Collection<String> cards) {
    return new ParsedStatement(
        format,
        rows,
        dataRowCount,
        pendingSkipped,
        unreadableSkipped,
        closingBalance,
        balanceAsOf,
        List.copyOf(cards),
        notes);
  }

  public ParsedStatement withPendingSkipped(int pending) {
    return new ParsedStatement(
        format,
        rows,
        dataRowCount,
        pending,
        unreadableSkipped,
        closingBalance,
        balanceAsOf,
        cardNumbers,
        notes);
  }

  public ParsedStatement withNote(String note) {
    List<String> combined = new ArrayList<>(notes);
    combined.add(note);
    return new ParsedStatement(
        format,
        rows,
        dataRowCount,
        pendingSkipped,
        unreadableSkipped,
        closingBalance,
        balanceAsOf,
        cardNumbers,
        List.copyOf(combined));
  }

  public LocalDate periodStart() {
    return rows.stream().map(ParsedRow::postedDate).min(LocalDate::compareTo).orElse(null);
  }

  public LocalDate periodEnd() {
    return rows.stream().map(ParsedRow::postedDate).max(LocalDate::compareTo).orElse(null);
  }
}
