package dev.grindtrack.finance.service;

import dev.grindtrack.finance.domain.AccountType;
import dev.grindtrack.finance.domain.TxnType;
import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Decides whether a row is real spending or just money moving around.
 *
 * <p>This is the guard against the double-counting problem: a $500 purchase on a credit card and
 * the $500 payment that later clears it are the same $500. Counting both makes reported spending
 * roughly twice the truth. In the first pass over real statements, 78 of 947 rows were transfers or
 * card payments, including a $15,500 run of checking-to-savings moves and $6,623 of Wells Fargo
 * card payments.
 *
 * <p>Patterns below are taken verbatim from the descriptions the five institutions actually emit.
 * The classifier is deliberately conservative: anything it is unsure about stays {@link
 * TxnType#SPEND} and shows up for review, because a wrongly-included transfer is visible in a
 * category list whereas a wrongly-excluded expense silently disappears.
 */
@Component
public class TxnTypeClassifier {

  /** Payments toward a card or loan. Settles debt already recorded as SPEND. */
  private static final List<Pattern> PAYMENT =
      List.of(
          Pattern.compile("CAPITAL ONE (AUTOPAY|MOBILE|CRCARDPMT)", Pattern.CASE_INSENSITIVE),
          Pattern.compile("CHASE CREDIT CRD", Pattern.CASE_INSENSITIVE),
          Pattern.compile("WELLS FARGO CARD", Pattern.CASE_INSENSITIVE),
          Pattern.compile("\\bAUTOPAY\\b|\\bCRCARDPMT\\b", Pattern.CASE_INSENSITIVE),
          Pattern.compile("AUTOMATIC PAYMENT\\s*-?\\s*THANK", Pattern.CASE_INSENSITIVE),
          Pattern.compile("PAYMENT\\s*/?\\s*CREDIT", Pattern.CASE_INSENSITIVE),
          Pattern.compile("ONLINE (BANKING )?PAYMENT", Pattern.CASE_INSENSITIVE),
          Pattern.compile("ADVS ED SERV|AIDVANTAGE", Pattern.CASE_INSENSITIVE));

  /** Movement between two accounts that are both yours. Nets to zero. */
  private static final List<Pattern> TRANSFER =
      List.of(
          Pattern.compile(
              "(WITHDRAWAL|DEPOSIT|TRANSFER)\\s+(TO|FROM)\\s+360 CHECKING",
              Pattern.CASE_INSENSITIVE),
          Pattern.compile(
              "(WITHDRAWAL|DEPOSIT|TRANSFER)\\s+(TO|FROM)\\s+BIGTIMESAVINGS",
              Pattern.CASE_INSENSITIVE),
          Pattern.compile("OVERDRAFT TRANSFER", Pattern.CASE_INSENSITIVE),
          Pattern.compile("\\bINTERNAL TRANSFER\\b", Pattern.CASE_INSENSITIVE));

  /** Money genuinely arriving from outside the household. */
  private static final List<Pattern> INCOME =
      List.of(
          Pattern.compile("PAYROLL", Pattern.CASE_INSENSITIVE),
          Pattern.compile("MONTHLY INTEREST PAID", Pattern.CASE_INSENSITIVE),
          Pattern.compile("\\bDIRECT DEP\\b|\\bDD\\b", Pattern.CASE_INSENSITIVE));

  /**
   * @param rawDescription the bank's own wording, unmodified
   * @param amount signed — negative is money out
   * @param accountType the account the row belongs to
   */
  public TxnType classify(String rawDescription, BigDecimal amount, AccountType accountType) {
    if (rawDescription == null) {
      return TxnType.SPEND;
    }
    String d = rawDescription.trim();

    if (matchesAny(TRANSFER, d)) {
      return TxnType.TRANSFER;
    }
    if (matchesAny(PAYMENT, d)) {
      return TxnType.PAYMENT;
    }
    if (matchesAny(INCOME, d)) {
      return TxnType.INCOME;
    }

    // On a card or loan, a credit that is not an identified payment is a refund or statement
    // credit — money coming back, so it offsets spending rather than counting as income.
    boolean inbound = amount != null && amount.signum() > 0;
    if (inbound && accountType != null && accountType.isLiability()) {
      return TxnType.SPEND;
    }
    if (inbound) {
      return TxnType.INCOME;
    }
    return TxnType.SPEND;
  }

  private static boolean matchesAny(List<Pattern> patterns, String value) {
    for (Pattern p : patterns) {
      if (p.matcher(value).find()) {
        return true;
      }
    }
    return false;
  }
}
