package dev.grindtrack.finance.service.parse;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Capital One Quicksilver and Savor cards.
 *
 * <p>{@code Transaction Date,Posted Date,Card No.,Description,Category,Debit,Credit}
 *
 * <p>Debit and Credit are separate columns, each holding a positive number or nothing at all. The
 * {@code Card No.} column is why this parser reports card numbers: with three Capital One cards on
 * the account, uploading the Savor export into a Quicksilver account is an easy mistake and an
 * annoying one to unpick, so the import refuses when the file names a different card.
 *
 * <p>{@code Category} is Capital One's own guess. It is kept as the issuer category rather than the
 * category, so it can seed rules later without being mistaken for a decision a human made.
 */
@Component
public class CapitalOneCreditParser implements StatementParser {

  @Override
  public StatementFormat format() {
    return StatementFormat.CAPITAL_ONE_CREDIT;
  }

  @Override
  public boolean canParse(List<String> header) {
    return header.contains("Debit") && header.contains("Credit") && header.contains("Card No.");
  }

  @Override
  public ParsedStatement parse(List<List<String>> rows) {
    List<String> header = rows.get(0).stream().map(String::trim).toList();
    int cTxnDate = header.indexOf("Transaction Date");
    int cPosted = header.indexOf("Posted Date");
    int cCard = header.indexOf("Card No.");
    int cDesc = header.indexOf("Description");
    int cCategory = header.indexOf("Category");
    int cDebit = header.indexOf("Debit");
    int cCredit = header.indexOf("Credit");

    if (cPosted < 0 || cDesc < 0 || cDebit < 0 || cCredit < 0) {
      throw new StatementParseException(
          "This looks like a Capital One card export but is missing a required column.");
    }

    List<ParsedRow> parsed = new ArrayList<>();
    Set<String> cards = new LinkedHashSet<>();

    for (List<String> row : rows.subList(1, rows.size())) {
      var posted = Amounts.date(Csv.at(row, cPosted));
      String description = Csv.at(row, cDesc);
      if (posted == null || description.isEmpty()) {
        continue;
      }

      BigDecimal debit = Amounts.money(Csv.at(row, cDebit));
      BigDecimal credit = Amounts.money(Csv.at(row, cCredit));
      BigDecimal amount =
          debit != null ? Amounts.outflow(debit) : (credit != null ? Amounts.inflow(credit) : null);
      if (amount == null) {
        continue;
      }

      String card = Csv.at(row, cCard);
      if (!card.isEmpty()) {
        cards.add(card);
      }

      parsed.add(
          ParsedRow.of(posted, amount, description)
              .withTransactionDate(Amounts.date(Csv.at(row, cTxnDate)))
              .withIssuerCategory(Csv.at(row, cCategory)));
    }

    if (parsed.isEmpty()) {
      throw new StatementParseException("No readable transactions in this file.");
    }
    return new ParsedStatement(format(), parsed, null, null, List.copyOf(cards), 0);
  }
}
