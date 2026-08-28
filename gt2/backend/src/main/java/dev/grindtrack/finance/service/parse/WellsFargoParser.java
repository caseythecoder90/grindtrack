package dev.grindtrack.finance.service.parse;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Wells Fargo cards.
 *
 * <p>{@code DATE,DESCRIPTION,AMOUNT,CHECK #,STATUS} — every field quoted, headers uppercase.
 *
 * <p>The only format that exports unsettled rows, via {@code STATUS}. Pending rows are skipped
 * rather than imported, and the count is reported so nothing looks silently missing.
 *
 * <p>The reason to skip rather than import-and-reconcile: a pending transaction's amount routinely
 * changes when it settles (tips, fuel holds), and its description often changes too. Since the
 * fingerprint is built from date, amount and description, the settled version would not match the
 * pending one and the same purchase would land twice. Waiting for the next import costs nothing —
 * these rows post within days and the range overlap picks them up.
 */
@Component
public class WellsFargoParser implements StatementParser {

  private static final String PENDING = "pending";

  @Override
  public StatementFormat format() {
    return StatementFormat.WELLS_FARGO;
  }

  @Override
  public boolean canParse(List<String> header) {
    return header.contains("STATUS") && header.contains("DESCRIPTION") && header.contains("AMOUNT");
  }

  @Override
  public ParsedStatement parse(List<List<String>> rows) {
    List<String> header = rows.get(0).stream().map(String::trim).toList();
    int cDate = header.indexOf("DATE");
    int cDesc = header.indexOf("DESCRIPTION");
    int cAmount = header.indexOf("AMOUNT");
    int cStatus = header.indexOf("STATUS");

    if (cDate < 0 || cDesc < 0 || cAmount < 0) {
      throw new StatementParseException(
          "This looks like a Wells Fargo export but is missing a required column.");
    }

    List<ParsedRow> parsed = new ArrayList<>();
    int pending = 0;

    for (List<String> row : rows.subList(1, rows.size())) {
      var date = Amounts.date(Csv.at(row, cDate));
      BigDecimal amount = Amounts.money(Csv.at(row, cAmount));
      String description = Csv.at(row, cDesc);
      if (date == null || amount == null || description.isEmpty()) {
        continue;
      }
      if (cStatus >= 0 && Csv.at(row, cStatus).toLowerCase().contains(PENDING)) {
        pending++;
        continue;
      }
      parsed.add(ParsedRow.of(date, amount, description));
    }

    if (parsed.isEmpty() && pending == 0) {
      throw new StatementParseException("No readable transactions in this file.");
    }
    return new ParsedStatement(format(), parsed, null, null, List.of(), pending);
  }
}
