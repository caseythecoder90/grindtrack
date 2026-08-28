package dev.grindtrack.finance.service.parse;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Chase cards.
 *
 * <p>{@code Transaction Date,Post Date,Description,Category,Type,Amount,Memo}
 *
 * <p>The friendliest of the six: one already-signed {@code Amount} column, negative for purchases,
 * which is the convention the whole app uses. Nothing needs reconstructing.
 */
@Component
public class ChaseParser implements StatementParser {

  @Override
  public StatementFormat format() {
    return StatementFormat.CHASE;
  }

  @Override
  public boolean canParse(List<String> header) {
    return header.contains("Post Date") && header.contains("Amount") && header.contains("Memo");
  }

  @Override
  public ParsedStatement parse(List<List<String>> rows) {
    List<String> header = rows.get(0).stream().map(String::trim).toList();
    int cTxnDate = header.indexOf("Transaction Date");
    int cPosted = header.indexOf("Post Date");
    int cDesc = header.indexOf("Description");
    int cCategory = header.indexOf("Category");
    int cAmount = header.indexOf("Amount");

    if (cPosted < 0 || cDesc < 0 || cAmount < 0) {
      throw new StatementParseException(
          "This looks like a Chase export but is missing a required column.");
    }

    List<ParsedRow> parsed = new ArrayList<>();
    List<List<String>> data = rows.subList(1, rows.size());
    int unreadable = 0;

    for (List<String> row : data) {
      var posted = Amounts.date(Csv.at(row, cPosted));
      BigDecimal amount = Amounts.money(Csv.at(row, cAmount));
      String description = Csv.at(row, cDesc);
      if (posted == null || amount == null || description.isEmpty()) {
        unreadable++;
        continue;
      }
      parsed.add(
          ParsedRow.of(posted, amount, description)
              .withTransactionDate(Amounts.date(Csv.at(row, cTxnDate)))
              .withIssuerCategory(Csv.at(row, cCategory)));
    }

    if (parsed.isEmpty()) {
      throw new StatementParseException("No readable transactions in this file.");
    }
    return ParsedStatement.of(format(), parsed, data.size(), unreadable);
  }
}
