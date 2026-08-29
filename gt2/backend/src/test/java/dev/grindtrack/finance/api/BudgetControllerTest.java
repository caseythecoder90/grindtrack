package dev.grindtrack.finance.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.grindtrack.finance.domain.Budget;
import dev.grindtrack.finance.domain.BudgetExtra;
import dev.grindtrack.finance.domain.BudgetSettings;
import dev.grindtrack.finance.service.BudgetService;
import dev.grindtrack.finance.service.BudgetService.MonthView;
import dev.grindtrack.web.ApiExceptionHandler;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** The budget HTTP surface: month parsing, one-off validation, and clearing the income override. */
class BudgetControllerTest {

  private BudgetService budget;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    budget = mock(BudgetService.class);
    mvc =
        MockMvcBuilders.standaloneSetup(new BudgetController(budget))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
    when(budget.month(any())).thenReturn(view());
    when(budget.extrasFrom(any())).thenReturn(List.of());
    when(budget.list(anyBoolean())).thenReturn(List.of());
  }

  private static MonthView view() {
    return new MonthView(
        "2026-09",
        "September 2026",
        1,
        30,
        false,
        new BigDecimal("8000"),
        true,
        BigDecimal.ZERO,
        new BigDecimal("5720"),
        BigDecimal.ZERO,
        new BigDecimal("5720"),
        new BigDecimal("2280"),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        List.of(),
        List.of(),
        List.of());
  }

  // ---------- month parsing ----------

  @Test
  void aMonthMustBeYearAndMonthNotADate() throws Exception {
    mvc.perform(get("/api/finance/budget/month").param("month", "2026-09-01"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("yyyy-MM")));
  }

  @Test
  void noMonthMeansThisMonth() throws Exception {
    mvc.perform(get("/api/finance/budget/month")).andExpect(status().isOk());

    verify(budget).month(YearMonth.now());
  }

  @Test
  void anExplicitMonthIsPassedThrough() throws Exception {
    mvc.perform(get("/api/finance/budget/month").param("month", "2026-09"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.monthLabel").value("September 2026"));

    verify(budget).month(YearMonth.of(2026, 9));
  }

  // ---------- one-offs ----------

  @Test
  void aOneOffNeedsALabelSoTheNumberExplainsItselfLater() throws Exception {
    mvc.perform(
            post("/api/finance/budget/extras")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"month\":\"2026-09\",\"label\":\"  \",\"amount\":-400}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("label")));

    verify(budget, never()).addExtra(any(), anyString(), any(), any(), any());
  }

  @Test
  void aServiceInvariantOnOneOffsReachesTheCallerAsA400() throws Exception {
    when(budget.addExtra(any(), anyString(), any(), any(), any()))
        .thenThrow(new IllegalArgumentException("an amount is required"));

    mvc.perform(
            post("/api/finance/budget/extras")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"month\":\"2026-09\",\"label\":\"Nothing\",\"amount\":0}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void aOneOffIsStoredAgainstTheMonthItWasGiven() throws Exception {
    BudgetExtra extra =
        new BudgetExtra(LocalDate.of(2026, 9, 1), "Flights", new BigDecimal("-400"), "Travel");
    when(budget.addExtra(any(), anyString(), any(), any(), any())).thenReturn(extra);

    mvc.perform(
            post("/api/finance/budget/extras")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"month\":\"2026-09\",\"label\":\"Flights\",\"amount\":-400,\"category\":\"Travel\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.month").value("2026-09"))
        .andExpect(jsonPath("$.category").value("Travel"));

    verify(budget)
        .addExtra(
            eq(YearMonth.of(2026, 9)),
            eq("Flights"),
            eq(new BigDecimal("-400")),
            eq("Travel"),
            any());
  }

  // ---------- income ----------

  @Test
  void aBlankIncomeClearsTheOverrideAndGoesBackToEstimating() throws Exception {
    // Null is meaningful here: it means "work it out from real deposits", not "zero".
    BudgetSettings cleared = BudgetSettings.initial();
    when(budget.setExpectedIncome(null)).thenReturn(cleared);

    mvc.perform(
            put("/api/finance/budget/income")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedMonthlyIncome\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estimated").value(true));
  }

  @Test
  void aDeclaredIncomeIsReportedAsNotEstimated() throws Exception {
    BudgetSettings set = BudgetSettings.initial();
    set.setExpectedMonthlyIncome(new BigDecimal("8600"));
    when(budget.setExpectedIncome(any())).thenReturn(set);

    mvc.perform(
            put("/api/finance/budget/income")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedMonthlyIncome\":8600}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estimated").value(false))
        .andExpect(jsonPath("$.expectedMonthlyIncome").value("8600"));
  }

  // ---------- lines ----------

  @Test
  void aDuplicateCategoryIsRefusedByTheServiceAndSurfacedAsA400() throws Exception {
    when(budget.create(anyString(), any(), any(), anyInt()))
        .thenThrow(new IllegalArgumentException("Groceries already has a budget line"));

    mvc.perform(
            post("/api/finance/budget/lines")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"Groceries\",\"monthlyAmount\":600}"))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.error")
                .value(org.hamcrest.Matchers.containsString("already has a budget")));
  }

  @Test
  void aBudgetLineComesBackWithItsAmount() throws Exception {
    when(budget.create(anyString(), any(), any(), anyInt()))
        .thenReturn(new Budget("Groceries", new BigDecimal("600")));

    mvc.perform(
            post("/api/finance/budget/lines")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"Groceries\",\"monthlyAmount\":600}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.category").value("Groceries"))
        .andExpect(jsonPath("$.monthlyAmount").value(600));
  }
}
