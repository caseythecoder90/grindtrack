package dev.grindtrack.finance.service.parse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * OFX and QFX statements from a brokerage or 401k provider.
 *
 * <p>Deliberately <em>not</em> a {@link StatementParser}. That interface's contract is rows of
 * cells — {@code canParse(List<String> header)} and {@code parse(List<List<String>> rows)} — and an
 * OFX file is SGML, not a table. Forcing it through would mean widening that contract to something
 * meaningless for the six CSV formats that legitimately share it. One extra branch in the import
 * service is a smaller price than an interface that no longer says anything.
 *
 * <p><strong>Balance only, no transactions</strong> — the same call as Aidvantage, for a different
 * reason. A 401k contribution comes out of payroll pre-tax and never touches the checking account,
 * so importing one as SPEND would invent roughly $1,200 a month of spending that never happened,
 * and as INCOME it would invent income. It is neither: it is money moving into an asset you already
 * own. What actually matters for net worth is the current market value, and this format states it
 * outright.
 *
 * <p>That last point is why this parser exists at all and the Visa 401k CSV has no equivalent. Here
 * the value is declared in {@code MKTVAL}. There it would have to be derived by summing
 * contributions, dividends and a "change in market value" row, with fund-exchange rows that cancel
 * out — arithmetic with several defensible answers, which is the kind of number this app should
 * never write into net worth on its own.
 */
@Component
public class OfxInvestmentParser {

  /** Both v1 SGML and v2 XML start with this, and nothing else in {@code statements/} does. */
  private static final Pattern OFX_MARKER =
      Pattern.compile("^\\s*(OFXHEADER|<\\?OFX|<OFX>)", Pattern.CASE_INSENSITIVE);

  /**
   * OFX v1 is SGML and routinely leaves tags unclosed: {@code <MKTVAL>1372.76} with the next tag
   * ending it. Capturing up to the next {@code <} or line break handles both that and the closed
   * form this particular provider happens to emit.
   */
  private static final Pattern MKTVAL = tag("MKTVAL");

  private static final Pattern PRICE_AS_OF = tag("DTPRICEASOF");
  private static final Pattern AS_OF = tag("DTASOF");

  private static Pattern tag(String name) {
    return Pattern.compile("<" + name + ">\\s*([^<\\r\\n]*)", Pattern.CASE_INSENSITIVE);
  }

  public boolean canParse(String content) {
    return content != null && OFX_MARKER.matcher(content).find();
  }

  public ParsedStatement parse(String content) {
    BigDecimal total = BigDecimal.ZERO;
    int positions = 0;

    // Sum every holding: a 401k with three funds in it reports three POSMF blocks, and the
    // account is worth all of them. Taking only the first would silently under-report.
    Matcher m = MKTVAL.matcher(content);
    while (m.find()) {
      BigDecimal value = Amounts.money(m.group(1));
      if (value != null) {
        total = total.add(value);
        positions++;
      }
    }

    if (positions == 0) {
      throw new StatementParseException(
          "That OFX file has no holdings in it, so there is no balance to read. A transactions-only"
              + " export will not work here — download the one that includes positions.");
    }

    LocalDate asOf = firstDate(content, PRICE_AS_OF, AS_OF);

    return ParsedStatement.of(StatementFormat.OFX_INVESTMENT, java.util.List.of(), positions, 0)
        .withBalance(total, asOf)
        .withNote(
            "No transactions imported, and that is correct: contributions come out of payroll"
                + " pre-tax and never touch your checking account, so counting them as spending or"
                + " income would invent money that never moved. The "
                + (positions == 1 ? "holding was" : positions + " holdings were")
                + " used to set the account balance.");
  }

  /**
   * Prefers the date the price was struck over the moment the file was generated.
   *
   * <p>They differ — this provider stamps {@code DTASOF} with the download time and {@code
   * DTPRICEASOF} with the previous close. The balance belongs to the close, and dating it "today"
   * would make a figure look fresher than it is.
   */
  private static LocalDate firstDate(String content, Pattern... patterns) {
    for (Pattern pattern : patterns) {
      Matcher m = pattern.matcher(content);
      if (m.find()) {
        LocalDate parsed = ofxDate(m.group(1));
        if (parsed != null) {
          return parsed;
        }
      }
    }
    return null;
  }

  /** OFX timestamps are {@code YYYYMMDDHHMMSS.sss[TZ]}; only the leading date is wanted. */
  private static LocalDate ofxDate(String raw) {
    if (raw == null) {
      return null;
    }
    String digits = raw.trim();
    if (digits.length() < 8) {
      return null;
    }
    try {
      return LocalDate.of(
          Integer.parseInt(digits.substring(0, 4)),
          Integer.parseInt(digits.substring(4, 6)),
          Integer.parseInt(digits.substring(6, 8)));
    } catch (NumberFormatException | java.time.DateTimeException e) {
      return null;
    }
  }
}
