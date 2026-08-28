package dev.grindtrack.finance.service.parse;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Bank of America cards.
 *
 * <p>{@code Posted Date,Reference Number,Payee,Address,Amount}
 *
 * <p>The only format that supplies a genuine unique identifier per transaction. A bank-assigned
 * reference beats anything this app can compute, so it becomes the fingerprint directly — which
 * makes re-import deduplication exact here rather than merely reliable.
 *
 * <p>{@code Address} is dropped: it duplicates the city and state already trailing the payee, and
 * the merchant normalizer strips those anyway.
 */
@Component
public class BankOfAmericaParser implements StatementParser {

  @Override
  public StatementFormat format() {
    return StatementFormat.BANK_OF_AMERICA;
  }

  @Override
  public boolean canParse(List<String> header) {
    return header.contains("Reference Number") && header.contains("Payee");
  }

  @Override
  public ParsedStatement parse(List<List<String>> rows) {
    List<String> header = rows.get(0).stream().map(String::trim).toList();
    int cPosted = header.indexOf("Posted Date");
    int cReference = header.indexOf("Reference Number");
    int cPayee = header.indexOf("Payee");
    int cAmount = header.indexOf("Amount");

    if (cPosted < 0 || cPayee < 0 || cAmount < 0) {
      throw new StatementParseException(
          "This looks like a Bank of America export but is missing a required column.");
    }

    List<ParsedRow> parsed = new ArrayList<>();
    for (List<String> row : rows.subList(1, rows.size())) {
      var posted = Amounts.date(Csv.at(row, cPosted));
      BigDecimal amount = Amounts.money(Csv.at(row, cAmount));
      String payee = Csv.at(row, cPayee);
      if (posted == null || amount == null || payee.isEmpty()) {
        continue;
      }
      parsed.add(
          ParsedRow.of(posted, amount, payee).withExternalReference(Csv.at(row, cReference)));
    }

    if (parsed.isEmpty()) {
      throw new StatementParseException("No readable transactions in this file.");
    }
    return new ParsedStatement(format(), parsed, null, null, List.of(), 0);
  }
}
