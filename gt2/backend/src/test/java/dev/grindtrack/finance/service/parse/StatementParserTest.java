package dev.grindtrack.finance.service.parse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Parser coverage for all six export formats.
 *
 * <p>Every fixture mirrors the real column layout, date format and sign convention exactly, but the
 * merchants and amounts are invented. This repo is public, so no real statement data goes in it —
 * the shapes are what matter, and the shapes are faithful.
 */
class StatementParserTest {

  private final CapitalOneDepositParser capitalOneDeposit = new CapitalOneDepositParser();
  private final CapitalOneCreditParser capitalOneCredit = new CapitalOneCreditParser();
  private final ChaseParser chase = new ChaseParser();
  private final BankOfAmericaParser bankOfAmerica = new BankOfAmericaParser();
  private final WellsFargoParser wellsFargo = new WellsFargoParser();
  private final AidvantageParser aidvantage = new AidvantageParser();

  private ParsedStatement parse(StatementParser parser, String csv) {
    List<List<String>> rows = Csv.parse(csv);
    assertThat(parser.canParse(rows.get(0).stream().map(String::trim).toList())).isTrue();
    return parser.parse(rows);
  }

  // ---------- Capital One deposit: unsigned amount + separate type column ----------

  private static final String CAPITAL_ONE_DEPOSIT_CSV =
      """
      Account Number,Transaction Description,Transaction Date,Transaction Type,Transaction Amount,Balance
      1234,Debit Card Purchase - EXAMPLE DINER 555 010 2030 FL,08/24/26,Debit,42.10,1500.00
      1234,Deposit from EXAMPLE EMPLOYER PAYROLL DD,08/22/26,Credit,1200.00,1542.10
      1234,Withdrawal to Savings XXXXXXX9999,08/20/26,Debit,500.00,342.10
      """;

  @Test
  void capitalOneDepositReconstructsSignFromTheTypeColumn() {
    ParsedStatement s = parse(capitalOneDeposit, CAPITAL_ONE_DEPOSIT_CSV);

    assertThat(s.format()).isEqualTo(StatementFormat.CAPITAL_ONE_DEPOSIT);
    assertThat(s.rows()).hasSize(3);
    // Debit rows are positive in the file; they must come out negative.
    assertThat(s.rows().get(0).amount()).isEqualByComparingTo("-42.10");
    assertThat(s.rows().get(1).amount()).isEqualByComparingTo("1200.00");
    assertThat(s.rows().get(2).amount()).isEqualByComparingTo("-500.00");
  }

  @Test
  void capitalOneDepositTakesTheBalanceFromTheLatestRowNotTheFirstLine() {
    // Rows arrive newest-first in practice, but nothing guarantees it, so the balance must follow
    // the newest date rather than the file order.
    ParsedStatement s =
        parse(
            capitalOneDeposit,
            """
            Account Number,Transaction Description,Transaction Date,Transaction Type,Transaction Amount,Balance
            1234,Older row,08/01/26,Debit,10.00,900.00
            1234,Newest row,08/24/26,Debit,20.00,1500.00
            1234,Middle row,08/10/26,Debit,30.00,1200.00
            """);

    assertThat(s.closingBalance()).isEqualByComparingTo("1500.00");
    assertThat(s.balanceAsOf()).hasToString("2026-08-24");
  }

  @Test
  void capitalOneDepositParsesTwoDigitYears() {
    ParsedStatement s = parse(capitalOneDeposit, CAPITAL_ONE_DEPOSIT_CSV);
    assertThat(s.rows().get(0).postedDate()).hasToString("2026-08-24");
  }

  // ---------- Capital One credit: separate Debit/Credit columns ----------

  private static final String CAPITAL_ONE_CREDIT_CSV =
      """
      Transaction Date,Posted Date,Card No.,Description,Category,Debit,Credit
      2026-08-04,2026-08-05,4321,EXAMPLE DENTAL GROUP,Health Care,60.00,
      2026-08-01,2026-08-02,4321,CAPITAL ONE AUTOPAY PYMT,Payment/Credit,,57.40
      """;

  @Test
  void capitalOneCreditReadsBothColumnsAndKeepsTheIssuerCategory() {
    ParsedStatement s = parse(capitalOneCredit, CAPITAL_ONE_CREDIT_CSV);

    assertThat(s.rows()).hasSize(2);
    assertThat(s.rows().get(0).amount()).isEqualByComparingTo("-60.00");
    assertThat(s.rows().get(0).issuerCategory()).isEqualTo("Health Care");
    assertThat(s.rows().get(1).amount()).isEqualByComparingTo("57.40");
    // Both dates are supplied by this format.
    assertThat(s.rows().get(0).postedDate()).hasToString("2026-08-05");
    assertThat(s.rows().get(0).transactionDate()).hasToString("2026-08-04");
  }

  @Test
  void capitalOneCreditReportsTheCardNumber() {
    // This is what lets the import refuse a Savor statement uploaded into a Quicksilver account.
    assertThat(parse(capitalOneCredit, CAPITAL_ONE_CREDIT_CSV).cardNumbers())
        .containsExactly("4321");
  }

  // ---------- Chase: already signed ----------

  @Test
  void chaseKeepsTheSignItWasGiven() {
    ParsedStatement s =
        parse(
            chase,
            """
            Transaction Date,Post Date,Description,Category,Type,Amount,Memo
            08/23/2026,08/23/2026,EXAMPLE STORE*ABC123,Shopping,Sale,-28.46,
            08/01/2026,08/01/2026,AUTOMATIC PAYMENT - THANK YOU,,Payment,236.00,
            """);

    assertThat(s.rows().get(0).amount()).isEqualByComparingTo("-28.46");
    assertThat(s.rows().get(0).issuerCategory()).isEqualTo("Shopping");
    assertThat(s.rows().get(1).amount()).isEqualByComparingTo("236.00");
    // Blank category must not become the empty string.
    assertThat(s.rows().get(1).issuerCategory()).isNull();
  }

  // ---------- Bank of America: quoted fields and a real reference number ----------

  @Test
  void bankOfAmericaUsesItsReferenceNumberAsTheFingerprint() {
    ParsedStatement s =
        parse(
            bankOfAmerica,
            """
            Posted Date,Reference Number,Payee,Address,Amount
            05/07/2026,24610436126004040633249,"EXAMPLE SHOP #304 BRANDON FL","BRANDON       FL ",-68.74
            """);

    assertThat(s.rows()).hasSize(1);
    assertThat(s.rows().get(0).externalReference()).isEqualTo("24610436126004040633249");
    assertThat(s.rows().get(0).description()).isEqualTo("EXAMPLE SHOP #304 BRANDON FL");
    assertThat(s.rows().get(0).amount()).isEqualByComparingTo("-68.74");
  }

  // ---------- Wells Fargo: pending rows are held back ----------

  @Test
  void wellsFargoSkipsPendingRowsAndCountsThem() {
    // A pending amount changes when it settles, so importing it would create a second row for the
    // same purchase once the settled version arrives with a different fingerprint.
    ParsedStatement s =
        parse(
            wellsFargo,
            """
            "DATE","DESCRIPTION","AMOUNT","CHECK #","STATUS"
            "08/24/2026","EXAMPLE CAFE NEW YORK NY","-11.89",,"Posted"
            "08/25/2026","EXAMPLE FUEL STOP","-45.00",,"Pending"
            "08/22/2026","EXAMPLE GROCER","-13.28",,"Posted"
            """);

    assertThat(s.rows()).hasSize(2);
    assertThat(s.pendingSkipped()).isEqualTo(1);
    assertThat(s.rows()).noneMatch(r -> r.description().contains("FUEL"));
  }

  // ---------- Aidvantage: a sub-ledger, not transactions ----------

  private static final String AIDVANTAGE_CSV =
      """
      Date,LoanName,Description,Principal,Interest,Fees,Total,UnpaidPrincipalBalanceValue,
      07/28/2026,1-02 Example Loan - Subsidized,PAYMENT,-$27.89,-$9.39,$0.00,-$37.28,"$2,980.76",
      07/28/2026,1-03 Example Loan - Unsubsidized,PAYMENT,-$32.56,-$10.92,$0.00,-$43.48,"$3,466.60",
      06/28/2026,1-02 Example Loan - Subsidized,PAYMENT,-$27.50,-$9.78,$0.00,-$37.28,"$3,008.65",
      06/28/2026,1-03 Example Loan - Unsubsidized,PAYMENT,-$32.10,-$11.38,$0.00,-$43.48,"$3,498.70",
      """;

  @Test
  void aidvantageProducesNoTransactionsBecauseItIsASubLedger() {
    // The real money movement is one servicer debit on the checking account. These rows split that
    // single payment across every loan; importing them would count the same payment twice.
    ParsedStatement s = parse(aidvantage, AIDVANTAGE_CSV);
    assertThat(s.rows()).isEmpty();
  }

  @Test
  void aidvantageSumsTheLatestUnpaidPrincipalPerLoan() {
    ParsedStatement s = parse(aidvantage, AIDVANTAGE_CSV);
    // July balances only — the June rows for the same two loans must not be added on top.
    assertThat(s.closingBalance()).isEqualByComparingTo("6447.36");
    assertThat(s.balanceAsOf()).hasToString("2026-07-28");
  }

  @Test
  void aidvantageHandlesQuotedThousandsSeparators() {
    // "$2,980.76" would break a naive comma split — which is why Csv exists.
    assertThat(parse(aidvantage, AIDVANTAGE_CSV).closingBalance()).isPositive();
  }

  // ---------- shared failure behaviour ----------

  @Test
  void aFileWithNoUsableRowsIsRejectedRatherThanImportedEmpty() {
    assertThatThrownBy(
            () ->
                chase.parse(
                    Csv.parse(
                        """
                        Transaction Date,Post Date,Description,Category,Type,Amount,Memo
                        ,,,,,,
                        """)))
        .isInstanceOf(StatementParseException.class)
        .hasMessageContaining("No readable transactions");
  }

  @Test
  void parsersDoNotClaimEachOthersFiles() {
    List<String> chaseHeader =
        Csv.parse(CAPITAL_ONE_CREDIT_CSV).get(0).stream().map(String::trim).toList();
    assertThat(chase.canParse(chaseHeader)).isFalse();
    assertThat(bankOfAmerica.canParse(chaseHeader)).isFalse();
    assertThat(wellsFargo.canParse(chaseHeader)).isFalse();
    assertThat(aidvantage.canParse(chaseHeader)).isFalse();
    assertThat(capitalOneDeposit.canParse(chaseHeader)).isFalse();
    assertThat(capitalOneCredit.canParse(chaseHeader)).isTrue();
  }
}
