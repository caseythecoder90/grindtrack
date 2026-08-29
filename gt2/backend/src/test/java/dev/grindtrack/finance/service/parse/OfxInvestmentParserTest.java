package dev.grindtrack.finance.service.parse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * OFX/QFX investment statements.
 *
 * <p>The fixture mirrors the real JPMorgan 401k download's structure exactly — the SGML nesting,
 * the unclosed-tag tolerance, the two different as-of stamps — with invented figures. This repo is
 * public, so the shapes are faithful and the numbers are not.
 */
class OfxInvestmentParserTest {

  private final OfxInvestmentParser parser = new OfxInvestmentParser();

  private static final String QFX =
      """
      OFXHEADER:100
      DATA:OFXSGML
      VERSION:102

      <OFX>
        <INVSTMTMSGSRSV1>
          <INVSTMTTRNRS>
            <INVSTMTRS>
              <DTASOF>20260829125401.219</DTASOF>
              <INVPOSLIST>
                <POSMF>
                  <INVPOS>
                    <UNITS>43.47943</UNITS>
                    <UNITPRICE>31.5726</UNITPRICE>
                    <MKTVAL>1372.76</MKTVAL>
                    <DTPRICEASOF>20260828000000.000</DTPRICEASOF>
                  </INVPOS>
                </POSMF>
              </INVPOSLIST>
            </INVSTMTRS>
          </INVSTMTTRNRS>
        </INVSTMTMSGSRSV1>
      </OFX>
      """;

  @Test
  void aQfxFileIsRecognizedByItsHeader() {
    assertThat(parser.canParse(QFX)).isTrue();
  }

  @Test
  void aCsvIsNotClaimedByThisParser() {
    // The CSV parsers own those; this must not intercept one on its way past.
    assertThat(parser.canParse("Transaction Date,Post Date,Description,Amount\n")).isFalse();
    assertThat(parser.canParse("")).isFalse();
    assertThat(parser.canParse(null)).isFalse();
  }

  @Test
  void theMarketValueBecomesTheBalance() {
    ParsedStatement s = parser.parse(QFX);

    assertThat(s.format()).isEqualTo(StatementFormat.OFX_INVESTMENT);
    assertThat(s.closingBalance()).isEqualByComparingTo("1372.76");
  }

  @Test
  void theBalanceIsDatedFromThePriceNotTheDownload() {
    // DTASOF is when the file was generated; DTPRICEASOF is the close the value belongs to.
    // Dating it "today" would make a stale figure look fresh.
    assertThat(parser.parse(QFX).balanceAsOf()).hasToString("2026-08-28");
  }

  @Test
  void everyHoldingIsCountedNotJustTheFirst() {
    // A 401k split across three funds is worth all three. Taking the first would under-report,
    // and under-reporting net worth is the kind of wrong nobody notices.
    String threeFunds =
        QFX.replace(
            "</INVPOSLIST>",
            """
                <POSMF><INVPOS><MKTVAL>500.00</MKTVAL></INVPOS></POSMF>
                <POSMF><INVPOS><MKTVAL>127.24</MKTVAL></INVPOS></POSMF>
              </INVPOSLIST>
            """);

    assertThat(parser.parse(threeFunds).closingBalance()).isEqualByComparingTo("2000.00");
  }

  @Test
  void unclosedTagsAreHandledBecauseOfxV1IsSgmlNotXml() {
    String sgml =
        """
        OFXHEADER:100
        <OFX>
          <INVPOSLIST>
            <POSMF>
              <INVPOS>
                <MKTVAL>2500.50
                <DTPRICEASOF>20260828000000.000
              </INVPOS>
            </POSMF>
          </INVPOSLIST>
        </OFX>
        """;

    ParsedStatement s = parser.parse(sgml);
    assertThat(s.closingBalance()).isEqualByComparingTo("2500.50");
    assertThat(s.balanceAsOf()).hasToString("2026-08-28");
  }

  @Test
  void noTransactionsAreImportedAndTheScreenIsToldWhy() {
    // Contributions come out of payroll pre-tax. As SPEND they invent spending that never
    // happened; as INCOME they invent income. Neither, and "0 imported" needs explaining.
    ParsedStatement s = parser.parse(QFX);

    assertThat(s.rows()).isEmpty();
    assertThat(s.notes()).isNotEmpty();
    assertThat(s.notes().get(0)).contains("pre-tax");
  }

  @Test
  void aTransactionsOnlyExportIsRefusedWithSomethingActionable() {
    String noPositions =
        """
        OFXHEADER:100
        <OFX>
          <INVTRANLIST>
            <INVBUY><INVTRAN><FITID>x</FITID></INVTRAN><TOTAL>-270.83</TOTAL></INVBUY>
          </INVTRANLIST>
        </OFX>
        """;

    assertThatThrownBy(() -> parser.parse(noPositions))
        .isInstanceOf(StatementParseException.class)
        .hasMessageContaining("positions");
  }
}
