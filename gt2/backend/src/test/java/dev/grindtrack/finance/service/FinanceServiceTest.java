package dev.grindtrack.finance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.grindtrack.finance.domain.Account;
import dev.grindtrack.finance.domain.AccountRepository;
import dev.grindtrack.finance.domain.AccountType;
import dev.grindtrack.finance.domain.Institution;
import dev.grindtrack.finance.domain.SavingsGoalRepository;
import dev.grindtrack.finance.domain.TransactionRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The rule that keeps the savings figure honest.
 *
 * <p>A savings goal here answers one question — how much could go toward a house right now — so
 * only cash accounts may carry the flag. A 401k is money you own and belongs in net worth, but
 * counting $30k of it as house fund would move the progress bar by a third of a down payment that
 * cannot actually be spent.
 */
class FinanceServiceTest {

  private AccountRepository accounts;
  private FinanceService service;

  @BeforeEach
  void setUp() {
    accounts = mock(AccountRepository.class);
    service =
        new FinanceService(
            accounts,
            mock(TransactionRepository.class),
            mock(SavingsGoalRepository.class),
            new MerchantNormalizer(),
            new TxnTypeClassifier(),
            mock(CategoryRuleService.class));
    when(accounts.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));
  }

  private Account existing(AccountType type) {
    Account a = new Account("Existing", Institution.OTHER, type);
    when(accounts.findById(1L)).thenReturn(Optional.of(a));
    return a;
  }

  @Test
  void aRetirementAccountCannotBeFlaggedAsSavings() {
    assertThatThrownBy(
            () ->
                service.createAccount(
                    "JPMorgan 401k", Institution.OTHER, AccountType.RETIREMENT, null, true, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cannot count toward a savings goal");

    verify(accounts, never()).save(any());
  }

  @Test
  void aRetirementAccountIsFineAsLongAsItIsNotClaimingToBeTheHouseFund() {
    assertThatCode(
            () ->
                service.createAccount(
                    "Visa 401k", Institution.OTHER, AccountType.RETIREMENT, null, false, 0))
        .doesNotThrowAnyException();
  }

  @Test
  void aCardOrLoanCannotBeFlaggedAsSavingsEither() {
    // The more obvious half of the same rule: a debt is not savings.
    for (AccountType type : new AccountType[] {AccountType.CREDIT_CARD, AccountType.LOAN}) {
      assertThatThrownBy(() -> service.createAccount("x", Institution.OTHER, type, null, true, 0))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void cashAccountsAreTheOnlyOnesThatMayHoldAGoal() {
    for (AccountType type : new AccountType[] {AccountType.CHECKING, AccountType.SAVINGS}) {
      assertThatCode(() -> service.createAccount("x", Institution.OTHER, type, null, true, 0))
          .doesNotThrowAnyException();
    }
  }

  @Test
  void theRuleAlsoHoldsWhenAnAccountIsEditedRatherThanCreated() {
    // The likelier path in practice: an account is created as savings, then retyped later.
    existing(AccountType.SAVINGS);

    assertThatThrownBy(
            () ->
                service.updateAccount(
                    1L, "401k", Institution.OTHER, AccountType.RETIREMENT, null, true, true, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cannot count toward a savings goal");
  }

  @Test
  void retirementIsAnAssetNotALiabilitySoItAddsToNetWorth() {
    assertThat(AccountType.RETIREMENT.isLiability()).isFalse();
    assertThat(AccountType.RETIREMENT.canCountTowardSavings()).isFalse();
  }
}
