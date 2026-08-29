package dev.grindtrack.finance.api;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
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

import dev.grindtrack.finance.domain.CategoryRule;
import dev.grindtrack.finance.domain.MatchType;
import dev.grindtrack.finance.domain.Transaction;
import dev.grindtrack.finance.domain.TxnType;
import dev.grindtrack.finance.service.CategoryRuleService;
import dev.grindtrack.finance.service.FinanceService;
import dev.grindtrack.finance.service.FinanceService.TransactionPage;
import dev.grindtrack.web.ApiExceptionHandler;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Standalone MockMvc tests for the transaction endpoints, including the review inbox's write. */
class TransactionControllerTest {

  private FinanceService finance;
  private CategoryRuleService rules;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    finance = mock(FinanceService.class);
    rules = mock(CategoryRuleService.class);
    mvc =
        MockMvcBuilders.standaloneSetup(new TransactionController(finance, rules))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    when(finance.listUncategorized()).thenReturn(List.of());
  }

  private static Transaction txn() {
    return new Transaction(1L, LocalDate.of(2026, 8, 20), new BigDecimal("-12.00"), "EXAMPLE SHOP");
  }

  // ---------- adding ----------

  @Test
  void aZeroAmountIsRejectedBecauseItRecordsNothing() throws Exception {
    mvc.perform(
            post("/api/finance/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"accountId\":1,\"postedDate\":\"2026-08-20\",\"amount\":0,\"description\":\"x\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value(containsString("amount")));
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

  // ---------- browsing ----------

  @Test
  void thePageBodyStatesEveryFieldAClientNeedsToIterate() throws Exception {
    when(finance.browse(any(), any(), any(), eq(0), eq(50), eq(false)))
        .thenReturn(new TransactionPage(List.of(txn()), 0, 50, 137, 3));

    mvc.perform(get("/api/finance/transactions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(50))
        .andExpect(jsonPath("$.totalElements").value(137))
        .andExpect(jsonPath("$.totalPages").value(3));
  }

  @Test
  void anOutOfRangePageOrSizeIsRejected() throws Exception {
    mvc.perform(get("/api/finance/transactions").param("page", "-1"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/finance/transactions").param("size", "500"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/finance/transactions").param("size", "0"))
        .andExpect(status().isBadRequest());
  }

  // ---------- reclassifying ----------

  @Test
  void anUnknownTxnTypeOnReclassifyIsRejected() throws Exception {
    mvc.perform(
            patch("/api/finance/transactions/1/type")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"txnType\":\"REFUND\"}"))
        .andExpect(status().isBadRequest());
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
  void aMissingTransactionAnswers404() throws Exception {
    when(finance.categorize(any(), any())).thenThrow(new NoSuchElementException("txn 99"));

    mvc.perform(
            patch("/api/finance/transactions/99/category")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"Groceries\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value(containsString("not found")));
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
  void notAskingForARuleDoesNotCreateOneAndReportsNoneWasMade() throws Exception {
    when(finance.categorize(eq(1L), eq("Groceries"))).thenReturn(txn());

    mvc.perform(
            post("/api/finance/transactions/1/categorize")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"Groceries\"}"))
        .andExpect(status().isOk())
        // The keys are always present now that this is a record; the frontend reads rule for
        // truthiness, and an absent key and a null one must not mean different things.
        .andExpect(jsonPath("$.rule").doesNotExist())
        .andExpect(jsonPath("$.ruleExisted").value(false));

    verify(rules, never()).promote(any(), any());
  }
}
