package dev.grindtrack.finance.api;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.grindtrack.finance.domain.CategoryRule;
import dev.grindtrack.finance.domain.MatchType;
import dev.grindtrack.finance.service.CategoryRuleService;
import dev.grindtrack.web.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Standalone MockMvc tests for the category-rule endpoints. */
class CategoryRuleControllerTest {

  private CategoryRuleService rules;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    rules = mock(CategoryRuleService.class);
    mvc =
        MockMvcBuilders.standaloneSetup(new CategoryRuleController(rules))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
  }

  @Test
  void anUnknownMatchTypeIsRejected() throws Exception {
    mvc.perform(
            post("/api/finance/rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pattern\":\"X\",\"matchType\":\"FUZZY\",\"category\":\"Y\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value(containsString("matchType")));
  }

  @Test
  void anAbsentMatchTypeMeansContains() throws Exception {
    when(rules.create(any(), any(), any(), anyInt()))
        .thenReturn(new CategoryRule("WHOLEFDS", MatchType.CONTAINS, "Groceries", 100));

    mvc.perform(
            post("/api/finance/rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pattern\":\"WHOLEFDS\",\"category\":\"Groceries\"}"))
        .andExpect(status().isOk());

    verify(rules).create("WHOLEFDS", MatchType.CONTAINS, "Groceries", 100);
  }

  @Test
  void aServiceRejectionReachesTheCallerAsA400WithItsMessage() throws Exception {
    // An uncompilable regex is an invariant the service owns; the message has to survive the trip.
    when(rules.create(any(), any(), any(), anyInt()))
        .thenThrow(new IllegalArgumentException("that is not a valid regular expression"));

    mvc.perform(
            post("/api/finance/rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pattern\":\"[unclosed\",\"matchType\":\"REGEX\",\"category\":\"Y\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value(containsString("regular expression")));
  }
}
