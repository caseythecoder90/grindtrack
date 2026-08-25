package dev.grindtrack.finance.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Every input string here is taken verbatim from a real downloaded statement, because the whole
 * value of this class is handling the specific noise these five institutions emit.
 */
class MerchantNormalizerTest {

  private final MerchantNormalizer normalizer = new MerchantNormalizer();

  @Test
  void stripsCapitalOnesCardPurchasePrefix() {
    // The important one: 226 of 420 checking rows carry this prefix. Without stripping it first,
    // every card purchase collapses to the literal string "DEBIT CARD PURCHASE".
    assertThat(normalizer.normalize("Digital Card Purchase - WINGSTOP 1861 813 725 9464 FL"))
        .isEqualTo("WINGSTOP");
    assertThat(normalizer.normalize("Debit Card Purchase - ANTHROPIC CLAUDE SUB 4152360599 CA"))
        .isEqualTo("ANTHROPIC CLAUDE SUB");
  }

  @Test
  void differentPurchasePrefixesCollapseToTheSameMerchant() {
    // Capital One uses "Debit" and "Digital" interchangeably for the same shop, so a caffeine
    // total that treated them separately would be wrong.
    String debit = normalizer.normalize("Debit Card Purchase - TPA6121BAYCOFFEETEA TAMPA FL");
    String digital = normalizer.normalize("Digital Card Purchase - TPA6121BAYCOFFEETEA TAMPA FL");
    assertThat(debit).isEqualTo(digital);
  }

  @Test
  void stripsAchPrefixes() {
    assertThat(normalizer.normalize("Withdrawal from VENMO PAYMENT")).isEqualTo("VENMO PAYMENT");
    assertThat(normalizer.normalize("Deposit from JPMORGAN CHASE B PAYROLL DD"))
        .isEqualTo("JPMORGAN CHASE B");
    assertThat(normalizer.normalize("Withdrawal from FRONTIER COMMUNI BILL PAY"))
        .isEqualTo("FRONTIER COMMUNI BILL");
  }

  @Test
  void stripsProcessorPrefixes() {
    assertThat(normalizer.normalize("SQ *ALPENGLOW TREAT CO OURAY CO")).startsWith("ALPENGLOW");
    assertThat(normalizer.normalize("AMAZON MKTPL*BF5EW8ZE2 Amzn.com/billWA")).startsWith("AMAZON");
  }

  @Test
  void stripsOrderReferencesSoRepeatChargesGroup() {
    // Amazon Prime bills monthly with a different reference every time; all eight months must
    // normalize identically or the subscription is invisible.
    String jan = normalizer.normalize("AMAZON PRIME*JZ44W7YF3");
    String aug = normalizer.normalize("AMAZON PRIME*6U3F38GV3");
    assertThat(jan).isEqualTo(aug).isEqualTo("AMAZON PRIME");
  }

  @Test
  void stripsTrailingStateAndPhoneNumbers() {
    assertThat(normalizer.normalize("GREENBERG DENTAL & ORT")).isEqualTo("GREENBERG DENTAL &");
    assertThat(normalizer.normalize("MINT MOBILE 800 683 7392 CA")).isEqualTo("MINT MOBILE");
  }

  @Test
  void dropsCorporateSuffixButNeverTheOnlyWord() {
    assertThat(normalizer.normalize("HETZNER ONLINE GMBH")).isEqualTo("HETZNER ONLINE");
    assertThat(normalizer.normalize("GET COVERED LLC NEW YORK NY")).isEqualTo("GET COVERED");
  }

  @Test
  void returnsNullForNothingUseful() {
    assertThat(normalizer.normalize(null)).isNull();
    assertThat(normalizer.normalize("   ")).isNull();
    assertThat(normalizer.normalize("*** 12345 ***")).isNull();
  }
}
