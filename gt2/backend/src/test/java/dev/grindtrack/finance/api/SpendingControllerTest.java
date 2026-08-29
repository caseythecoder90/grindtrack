package dev.grindtrack.finance.api;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.grindtrack.finance.service.FinanceService;
import dev.grindtrack.finance.service.FinanceService.SpendSummary;
import dev.grindtrack.finance.service.RecurringDetector;
import dev.grindtrack.web.ApiExceptionHandler;
import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Standalone MockMvc tests for the read-only spending analyses. */
class SpendingControllerTest {

  private FinanceService finance;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    finance = mock(FinanceService.class);
    mvc =
        MockMvcBuilders.standaloneSetup(
                new SpendingController(finance, mock(RecurringDetector.class)))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
  }

  @Test
  void aBackwardsDateRangeIsRejected() throws Exception {
    mvc.perform(get("/api/finance/spending").param("from", "2026-08-20").param("to", "2026-08-01"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value(containsString("on or before")));
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
            new SpendSummary(
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

  @Test
  void aMissingAccountAnswers404() throws Exception {
    when(finance.spendBetween(any(), any())).thenThrow(new NoSuchElementException("account 99"));

    mvc.perform(get("/api/finance/spending"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value(containsString("not found")));
  }
}
