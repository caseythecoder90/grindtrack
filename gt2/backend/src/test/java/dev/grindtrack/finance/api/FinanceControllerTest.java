package dev.grindtrack.finance.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.grindtrack.finance.domain.Account;
import dev.grindtrack.finance.domain.AccountType;
import dev.grindtrack.finance.domain.CategoryRule;
import dev.grindtrack.finance.domain.Institution;
import dev.grindtrack.finance.domain.MatchType;
import dev.grindtrack.finance.domain.Transaction;
import dev.grindtrack.finance.domain.TxnType;
import dev.grindtrack.finance.service.CategoryRuleService;
import dev.grindtrack.finance.service.FinanceService;
import dev.grindtrack.finance.service.RecurringDetector;
import dev.grindtrack.web.ApiExceptionHandler;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Standalone MockMvc tests for the finance HTTP surface.
 *
 * <p>Services are mocked here rather than built over mock repositories, which is a deliberate
 * departure from {@code TodoControllerTest}. What needs pinning at this layer is the part that has
 * no coverage anywhere else: which bodies are rejected, with what status, and what the response
 * looks like. {@link FinanceService} and {@link CategoryRuleService} already have their own tests,
 * and routing a request through real ones would only re-assert those.
 */
class FinanceControllerTest {

  private FinanceService finance;
  private CategoryRuleService rules;
  private RecurringDetector recurring;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    finance = mock(FinanceService.class);
    rules = mock(CategoryRuleService.class);
    recurring = mock(RecurringDetector.class);
    mvc =
        MockMvcBuilders.standaloneSetup(new FinanceController(finance, rules, recurring))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    when(finance.listAccounts(anyBoolean())).thenReturn(List.of());
    when(finance.listGoals(anyBoolean())).thenReturn(List.of());
    when(finance.listUncategorized()).thenReturn(List.of());
    when(finance.savingsBalance()).thenReturn(BigDecimal.ZERO);
    when(finance.netWorth()).thenReturn(BigDecimal.ZERO);
  }

  private static Account account() {
    Account a = new Account("Checking", Institution.CAPITAL_ONE, AccountType.CHECKING);
    a.update("Checking", Institution.CAPITAL_ONE, AccountType.CHECKING, "5830", false, true, 0);
    return a;
  }

  private static Transaction txn() {
    return new Transaction(1L, LocalDate.of(2026, 8, 20), new BigDecimal("-12.00"), "EXAMPLE SHOP");
  }

  // ---------- accounts ----------

  @Test
  void anUnknownInstitutionIsRejectedWithTheAllowedList() throws Exception {
    mvc.perform(
            post("/api/finance/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"Checking\",\"institution\":\"BARCLAYS\",\"accountType\":\"CHECKING\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("institution")));
  }

  @Test
  void anUnknownAccountTypeIsRejected() throws Exception {
    mvc.perform(
            post("/api/finance/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"Checking\",\"institution\":\"CHASE\",\"accountType\":\"BROKERAGE\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("accountType")));
  }

  @Test
  void aBlankNameIsRejectedBeforeTheServiceIsCalled() throws Exception {
    mvc.perform(
            post("/api/finance/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"  \",\"institution\":\"CHASE\",\"accountType\":\"CREDIT_CARD\"}"))
        .andExpect(status().isBadRequest());

    verify(finance, never())
        .createAccount(
            any(), any(), any(), any(), anyBoolean(), org.mockito.ArgumentMatchers.anyInt());
  }

  @Test
  void recordingABalanceWithoutAnAmountIsRejected() throws Exception {
    mvc.perform(
            patch("/api/finance/accounts/1/balance")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"asOf\":\"2026-08-20\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("balance")));
  }

  // ---------- transactions ----------

  @Test
  void aZeroAmountIsRejectedBecauseItRecordsNothing() throws Exception {
    mvc.perform(
            post("/api/finance/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"accountId\":1,\"postedDate\":\"2026-08-20\",\"amount\":0,\"description\":\"x\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("amount")));
  }

  @Test
  void aMalformedDateIsRejectedRatherThanSilentlyDropped() throws Exception {
    mvc.perform(
            post("/api/finance/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"accountId\":1,\"postedDate\":\"20-08-2026\",\"amount\":-5,\"description\":\"x\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void aDuplicateTransactionAnswers409RatherThanCreatingASecondRow() throws Exception {
    // The dedupe promise, at the HTTP boundary: an identical row is a conflict, not a success.
    when(finance.addTransaction(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(Optional.empty());

    mvc.perform(
            post("/api/finance/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"accountId\":1,\"postedDate\":\"2026-08-20\",\"amount\":-5,\"description\":\"EXAMPLE\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").exists());
  }

  @Test
  void anUnknownTxnTypeOnReclassifyIsRejected() throws Exception {
    mvc.perform(
            patch("/api/finance/transactions/1/type")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"txnType\":\"REFUND\"}"))
        .andExpect(status().isBadRequest());
  }

  // ---------- categorize and learn ----------

  @Test
  void categorizingWithoutACategoryIsRejected() throws Exception {
    mvc.perform(
            post("/api/finance/transactions/1/categorize")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"  \",\"createRule\":true}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void filingARowCanAlsoCreateTheRuleAndSaysWhichOne() throws Exception {
    when(finance.categorize(eq(1L), eq("Groceries"))).thenReturn(txn());
    when(rules.promote(eq(1L), eq("Groceries")))
        .thenReturn(
            Optional.of(new CategoryRule("WHOLEFDS", MatchType.CONTAINS, "Groceries", 100)));

    mvc.perform(
            post("/api/finance/transactions/1/categorize")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"Groceries\",\"createRule\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rule.pattern").value("WHOLEFDS"))
        .andExpect(jsonPath("$.ruleExisted").value(false));
  }

  @Test
  void aMerchantThatAlreadyHasARuleReportsItRatherThanFailing() throws Exception {
    when(finance.categorize(eq(1L), eq("Groceries"))).thenReturn(txn());
    when(rules.promote(eq(1L), eq("Groceries"))).thenReturn(Optional.empty());

    mvc.perform(
            post("/api/finance/transactions/1/categorize")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"Groceries\",\"createRule\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ruleExisted").value(true));
  }

  @Test
  void notAskingForARuleDoesNotCreateOne() throws Exception {
    when(finance.categorize(eq(1L), eq("Groceries"))).thenReturn(txn());

    mvc.perform(
            post("/api/finance/transactions/1/categorize")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"Groceries\"}"))
        .andExpect(status().isOk());

    verify(rules, never()).promote(any(), any());
  }

  // ---------- rules ----------

  @Test
  void anUnknownMatchTypeIsRejected() throws Exception {
    mvc.perform(
            post("/api/finance/rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pattern\":\"X\",\"matchType\":\"FUZZY\",\"category\":\"Y\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("matchType")));
  }

  @Test
  void aServiceRejectionReachesTheCallerAsA400WithItsMessage() throws Exception {
    // An uncompilable regex is an invariant the service owns; the message has to survive the trip.
    when(rules.create(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt()))
        .thenThrow(new IllegalArgumentException("that is not a valid regular expression"));

    mvc.perform(
            post("/api/finance/rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pattern\":\"[unclosed\",\"matchType\":\"REGEX\",\"category\":\"Y\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.error").value(org.hamcrest.Matchers.containsString("regular expression")));
  }

  // ---------- windows ----------

  @Test
  void aBackwardsDateRangeIsRejected() throws Exception {
    mvc.perform(get("/api/finance/spending").param("from", "2026-08-20").param("to", "2026-08-01"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("on or before")));
  }

  @Test
  void anAbsurdMonthCountIsRejected() throws Exception {
    mvc.perform(get("/api/finance/spending/monthly").param("months", "400"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/finance/spending/monthly").param("months", "0"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void spendingDefaultsToTheLastThirtyDaysWhenNoWindowIsGiven() throws Exception {
    when(finance.spendBetween(any(), any()))
        .thenReturn(
            new FinanceService.SpendSummary(
                "2026-07-29",
                "2026-08-28",
                new BigDecimal("-100"),
                new BigDecimal("500"),
                new BigDecimal("400"),
                3,
                List.of(),
                List.of()));

    mvc.perform(get("/api/finance/spending"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.transactionCount").value(3));
  }

  // ---------- summary ----------

  @Test
  void theSummaryReadsTheSavingsTotalOnceRatherThanPerGoal() throws Exception {
    // Re-querying the same SUM per goal is what this endpoint used to do; the shape of the fix is
    // worth pinning so it does not quietly come back.
    mvc.perform(get("/api/finance/summary")).andExpect(status().isOk());

    verify(finance, org.mockito.Mockito.times(1)).savingsBalance();
  }

  @Test
  void aMissingAccountAnswers404() throws Exception {
    when(finance.spendBetween(any(), any()))
        .thenThrow(new java.util.NoSuchElementException("account 99"));

    mvc.perform(get("/api/finance/spending"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("not found")));
  }

  @Test
  void reclassifyPassesTheParsedTypeThrough() throws Exception {
    when(finance.reclassify(eq(1L), eq(TxnType.TRANSFER))).thenReturn(txn());

    mvc.perform(
            patch("/api/finance/transactions/1/type")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"txnType\":\"transfer\"}"))
        .andExpect(status().isOk());

    verify(finance).reclassify(1L, TxnType.TRANSFER);
  }

  @Test
  void accountCreationPassesEveryParsedFieldToTheService() throws Exception {
    when(finance.createAccount(
            any(), any(), any(), any(), anyBoolean(), org.mockito.ArgumentMatchers.anyInt()))
        .thenReturn(account());

    mvc.perform(
            post("/api/finance/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"BigTimeSavings\",\"institution\":\"capital_one\",\"accountType\":\"savings\","
                        + "\"last4\":\"3711\",\"countsTowardSavings\":true,\"sortOrder\":2}"))
        .andExpect(status().isOk());

    // Lower-case input is accepted: the enum parse upper-cases before matching.
    verify(finance)
        .createAccount(
            "BigTimeSavings", Institution.CAPITAL_ONE, AccountType.SAVINGS, "3711", true, 2);
  }
}
