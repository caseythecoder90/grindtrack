package dev.grindtrack.finance.api;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.grindtrack.finance.domain.Account;
import dev.grindtrack.finance.domain.AccountType;
import dev.grindtrack.finance.domain.Institution;
import dev.grindtrack.finance.service.FinanceService;
import dev.grindtrack.web.ApiExceptionHandler;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Standalone MockMvc tests for accounts, goals and the dashboard summary.
 *
 * <p>The service is mocked rather than built over mock repositories, which is a deliberate
 * departure from {@code TodoControllerTest}. What needs pinning at this layer is the part with no
 * coverage anywhere else: which bodies are rejected, with what status, and what comes back. {@link
 * FinanceService} has its own tests, and routing through a real one would re-assert those.
 */
class FinanceControllerTest {

  private FinanceService finance;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    finance = mock(FinanceService.class);
    mvc =
        MockMvcBuilders.standaloneSetup(new FinanceController(finance))
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

  // ---------- accounts ----------

  @Test
  void anUnknownInstitutionIsRejectedWithTheAllowedList() throws Exception {
    mvc.perform(
            post("/api/finance/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"Checking\",\"institution\":\"BARCLAYS\",\"accountType\":\"CHECKING\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value(containsString("institution")));
  }

  @Test
  void anUnknownAccountTypeIsRejected() throws Exception {
    mvc.perform(
            post("/api/finance/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"Checking\",\"institution\":\"CHASE\",\"accountType\":\"BROKERAGE\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value(containsString("accountType")));
  }

  @Test
  void aBlankNameIsRejectedBeforeTheServiceIsCalled() throws Exception {
    mvc.perform(
            post("/api/finance/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"  \",\"institution\":\"CHASE\",\"accountType\":\"CREDIT_CARD\"}"))
        .andExpect(status().isBadRequest());

    verify(finance, never()).createAccount(any(), any(), any(), any(), anyBoolean(), anyInt());
  }

  @Test
  void recordingABalanceWithoutAnAmountIsRejected() throws Exception {
    mvc.perform(
            patch("/api/finance/accounts/1/balance")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"asOf\":\"2026-08-20\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value(containsString("balance")));
  }

  @Test
  void accountCreationPassesEveryParsedFieldToTheService() throws Exception {
    when(finance.createAccount(any(), any(), any(), any(), anyBoolean(), anyInt()))
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

  // ---------- goals ----------

  @Test
  void aGoalWithoutAPositiveTargetIsRejected() throws Exception {
    mvc.perform(
            post("/api/finance/goals")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"House\",\"targetAmount\":0}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value(containsString("targetAmount")));
  }

  // ---------- summary ----------

  @Test
  void theSummaryReadsTheSavingsTotalOnceRatherThanPerGoal() throws Exception {
    // Re-querying the same SUM per goal is what this endpoint used to do; the shape of the fix is
    // worth pinning so it does not quietly come back.
    mvc.perform(get("/api/finance/summary")).andExpect(status().isOk());

    verify(finance, times(1)).savingsBalance();
  }
}
