package dev.grindtrack.finance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.grindtrack.finance.domain.CategoryRule;
import dev.grindtrack.finance.domain.CategoryRuleRepository;
import dev.grindtrack.finance.domain.CategorySource;
import dev.grindtrack.finance.domain.MatchType;
import dev.grindtrack.finance.domain.Transaction;
import dev.grindtrack.finance.domain.TransactionRepository;
import dev.grindtrack.finance.domain.TxnType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The rules engine, and specifically the two properties everything else leans on: a rule never
 * overwrites a human decision, and the first matching rule wins.
 */
class CategoryRuleServiceTest {

  private CategoryRuleRepository rules;
  private TransactionRepository transactions;
  private CategoryRuleService service;

  @BeforeEach
  void setUp() {
    rules = mock(CategoryRuleRepository.class);
    transactions = mock(TransactionRepository.class);
    service = new CategoryRuleService(rules, transactions);

    when(rules.save(any(CategoryRule.class))).thenAnswer(i -> i.getArgument(0));
    when(rules.findByPatternIgnoreCaseAndMatchType(anyString(), any()))
        .thenReturn(Optional.empty());
  }

  private static Transaction txn(String description, String merchant) {
    Transaction t =
        new Transaction(1L, LocalDate.of(2026, 8, 1), new BigDecimal("-10.00"), description);
    t.applyImportedDetail(null, merchant, null, TxnType.SPEND, false);
    return t;
  }

  @Test
  void aRuleCategorizesAMatchingTransaction() {
    when(rules.findByActiveTrueOrderByPriorityAscIdAsc())
        .thenReturn(List.of(new CategoryRule("WHOLEFDS", MatchType.CONTAINS, "Groceries", 100)));

    Transaction t = txn("WHOLEFDS ATL #123", "WHOLEFDS ATL");
    assertThat(service.applyTo(List.of(t))).isEqualTo(1);
    assertThat(t.getCategory()).isEqualTo("Groceries");
    assertThat(t.getCategorySource()).isEqualTo(CategorySource.RULE);
  }

  @Test
  void theFirstMatchWinsWhichIsWhatPriorityIsFor() {
    // A narrow rule has to be able to sit in front of a broad one without either knowing about
    // the other. Priority is the only thing arranging that.
    when(rules.findByActiveTrueOrderByPriorityAscIdAsc())
        .thenReturn(
            List.of(
                new CategoryRule("AMAZON PRIME", MatchType.CONTAINS, "Subscriptions", 10),
                new CategoryRule("AMAZON", MatchType.CONTAINS, "Shopping", 100)));

    Transaction prime = txn("AMAZON PRIME MEMBERSHIP", "AMAZON PRIME");
    Transaction other = txn("AMAZON MKTPL ORDER", "AMAZON MKTPL");
    service.applyTo(List.of(prime, other));

    assertThat(prime.getCategory()).isEqualTo("Subscriptions");
    assertThat(other.getCategory()).isEqualTo("Shopping");
  }

  @Test
  void aHandPickedCategoryIsNeverOverwrittenByARule() {
    // This is what makes correcting a row worth the effort, and re-running rules safe.
    when(rules.findByActiveTrueOrderByPriorityAscIdAsc())
        .thenReturn(List.of(new CategoryRule("AMZN", MatchType.CONTAINS, "Shopping", 100)));

    Transaction t = txn("AMZN MKTP US*2K4LM7", "AMZN MKTP US");
    t.categorizeManually("Medical");

    assertThat(service.applyTo(List.of(t))).isZero();
    assertThat(t.getCategory()).isEqualTo("Medical");
    assertThat(t.getCategorySource()).isEqualTo(CategorySource.MANUAL);
  }

  @Test
  void anInactiveRuleMatchesNothing() {
    CategoryRule rule = new CategoryRule("WHOLEFDS", MatchType.CONTAINS, "Groceries", 100);
    rule.update("WHOLEFDS", MatchType.CONTAINS, "Groceries", 100, false);
    when(rules.findByActiveTrueOrderByPriorityAscIdAsc()).thenReturn(List.of(rule));

    Transaction t = txn("WHOLEFDS ATL", "WHOLEFDS ATL");
    assertThat(service.applyTo(List.of(t))).isZero();
    assertThat(t.getCategory()).isNull();
  }

  @Test
  void equalsMatchingDoesNotCatchSubstrings() {
    // A CONTAINS rule for a short name like BP would claim BPM SUPPLY. EQUALS exists for that.
    when(rules.findByActiveTrueOrderByPriorityAscIdAsc())
        .thenReturn(List.of(new CategoryRule("BP", MatchType.EQUALS, "Fuel", 100)));

    Transaction exact = txn("BP", "BP");
    Transaction other = txn("BPM SUPPLY CO", "BPM SUPPLY");
    service.applyTo(List.of(exact, other));

    assertThat(exact.getCategory()).isEqualTo("Fuel");
    assertThat(other.getCategory()).isNull();
  }

  @Test
  void aRuleMatchesTheRawDescriptionWhenTheMerchantDoesNot() {
    // Normalization keeps improving, so rows imported earlier may carry a worse merchant. Falling
    // back to the bank's original wording keeps old rows reachable by new rules.
    when(rules.findByActiveTrueOrderByPriorityAscIdAsc())
        .thenReturn(List.of(new CategoryRule("PEOPLE GAS", MatchType.CONTAINS, "Utilities", 100)));

    Transaction t = txn("TECO PEOPLE GAS BILL PAY", "TECO");
    assertThat(service.applyTo(List.of(t))).isEqualTo(1);
    assertThat(t.getCategory()).isEqualTo("Utilities");
  }

  @Test
  void hitCountsAreRecordedSoDeadRulesAreVisible() {
    CategoryRule rule = new CategoryRule("WHOLEFDS", MatchType.CONTAINS, "Groceries", 100);
    when(rules.findByActiveTrueOrderByPriorityAscIdAsc()).thenReturn(List.of(rule));

    service.applyTo(List.of(txn("WHOLEFDS ATL", "WHOLEFDS"), txn("WHOLEFDS TPA", "WHOLEFDS")));

    assertThat(rule.getHitCount()).isEqualTo(2);
    assertThat(rule.getLastApplied()).isNotNull();
  }

  @Test
  void apreviewDoesNotRecordHits() {
    CategoryRule rule = new CategoryRule("WHOLEFDS", MatchType.CONTAINS, "Groceries", 100);
    when(rules.findByActiveTrueOrderByPriorityAscIdAsc()).thenReturn(List.of(rule));

    service.applyTo(List.of(txn("WHOLEFDS ATL", "WHOLEFDS")), false);

    assertThat(rule.getHitCount()).isZero();
    verify(rules, never()).saveAll(any());
  }

  @Test
  void backfillRefilesHistoryAndReportsWhatIsLeft() {
    // Add a rule, run this, and everything imported before it existed re-files itself.
    when(rules.findByActiveTrueOrderByPriorityAscIdAsc())
        .thenReturn(List.of(new CategoryRule("WHOLEFDS", MatchType.CONTAINS, "Groceries", 100)));
    Transaction matched = txn("WHOLEFDS ATL", "WHOLEFDS ATL");
    Transaction unmatched = txn("SOMETHING ELSE", "SOMETHING ELSE");
    when(transactions.findByCategorySourceNotOrderByIdAsc(CategorySource.MANUAL))
        .thenReturn(List.of(matched, unmatched));

    CategoryRuleService.ApplyResult result = service.backfill();

    assertThat(result.examined()).isEqualTo(2);
    assertThat(result.categorized()).isEqualTo(1);
    assertThat(result.stillUncategorized()).isEqualTo(1);
    verify(transactions).saveAll(any());
  }

  // ---------- validation ----------

  @Test
  void aBrokenRegexIsRejectedWhenTheRuleIsSavedNotWhenItRuns() {
    assertThatThrownBy(() -> service.create("[unclosed", MatchType.REGEX, "Whatever", 100))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not a valid regular expression");
  }

  @Test
  void aDuplicatePatternIsRejectedBecauseTheSecondRuleWouldBeUnreachable() {
    when(rules.findByPatternIgnoreCaseAndMatchType("WHOLEFDS", MatchType.CONTAINS))
        .thenReturn(
            Optional.of(new CategoryRule("WHOLEFDS", MatchType.CONTAINS, "Groceries", 100)));

    assertThatThrownBy(() -> service.create("WHOLEFDS", MatchType.CONTAINS, "Shopping", 50))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Groceries");
  }

  @Test
  void aBlankCategoryIsRejected() {
    assertThatThrownBy(() -> service.create("WHOLEFDS", MatchType.CONTAINS, "  ", 100))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("category is required");
  }

  // ---------- promotion from the review inbox ----------

  @Test
  void promotingACorrectionBuildsTheRuleFromTheNormalizedMerchant() {
    // The raw description carries a store number and city, so it would match exactly one purchase
    // and be useless as a rule. The normalized merchant is the reusable half.
    Transaction t = txn("Digital Card Purchase - WINGSTOP 1861 813 725 9464 FL", "WINGSTOP");
    when(transactions.findById(5L)).thenReturn(Optional.of(t));

    Optional<CategoryRule> created = service.promote(5L, "Dining");

    assertThat(created).isPresent();
    assertThat(created.get().getPattern()).isEqualTo("WINGSTOP");
    assertThat(created.get().getCategory()).isEqualTo("Dining");
  }

  @Test
  void promotingAMerchantThatAlreadyHasARuleIsNotAnError() {
    Transaction t = txn("WINGSTOP 1861", "WINGSTOP");
    when(transactions.findById(5L)).thenReturn(Optional.of(t));
    when(rules.findByPatternIgnoreCaseAndMatchType("WINGSTOP", MatchType.CONTAINS))
        .thenReturn(Optional.of(new CategoryRule("WINGSTOP", MatchType.CONTAINS, "Dining", 100)));

    assertThat(service.promote(5L, "Dining")).isEmpty();
    verify(rules, never()).save(any());
  }
}
