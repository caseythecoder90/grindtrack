package dev.grindtrack.plan.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.grindtrack.plan.domain.PlanItem;
import dev.grindtrack.plan.domain.PlanQuarter;
import dev.grindtrack.plan.domain.PlanReference;
import dev.grindtrack.plan.service.PlanService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Standalone MockMvc tests pinning import validation outcomes and error bodies. */
class PlanControllerTest {

  private PlanService planService;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    planService = mock(PlanService.class);
    mvc = MockMvcBuilders.standaloneSetup(new PlanController(planService)).build();
  }

  private ResultActions postImport(String json) throws Exception {
    return mvc.perform(
        post("/api/plan/import").contentType(MediaType.APPLICATION_JSON).content(json));
  }

  @Test
  void importRejectsAnEmptyItemList() throws Exception {
    postImport("{\"items\": []}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("items must not be empty"));
  }

  @Test
  void importRejectsAnUnknownItemType() throws Exception {
    postImport("{\"items\": [{\"type\": \"quest\", \"title\": \"T\"}]}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error", startsWith("item type must be one of")));
  }

  @Test
  void importRejectsAMissingTitle() throws Exception {
    postImport("{\"items\": [{\"type\": \"cert\", \"title\": \"  \"}]}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("every item needs a title (max 300 chars)"));
  }

  @Test
  void importRejectsAnUnknownStatus() throws Exception {
    postImport("{\"items\": [{\"type\": \"cert\", \"title\": \"T\", \"status\": \"weird\"}]}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("bad status 'weird' on: T"));
  }

  @Test
  void importRejectsAMalformedTargetDate() throws Exception {
    postImport("{\"items\": [{\"type\": \"cert\", \"title\": \"T\", \"targetDate\": \"soon\"}]}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("bad targetDate on: T"));
  }

  @Test
  void importRejectsAQuarterOutOfRange() throws Exception {
    postImport(
            "{\"items\": [{\"type\": \"cert\", \"title\": \"T\"}],"
                + " \"quarters\": [{\"qtr\": 17, \"windowLabel\": \"W\"}]}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("quarters need qtr 1-16 and a windowLabel"));
  }

  @Test
  void importRejectsAReferenceMissingRequiredFields() throws Exception {
    postImport(
            "{\"items\": [{\"type\": \"cert\", \"title\": \"T\"}],"
                + " \"reference\": [{\"sheet\": \"overview\"}]}")
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.error").value("reference sheets need sheet, title, and contentJson"));
  }

  @Test
  void importAppliesDefaultsAndReportsCounts() throws Exception {
    when(planService.importPlan(anyList(), anyList(), anyList())).thenReturn(1);

    postImport(
            "{\"items\": [{\"type\": \"cert\", \"title\": \"  AWS SAA  \"}],"
                + " \"quarters\": [{\"qtr\": 5, \"windowLabel\": \"W\"}],"
                + " \"reference\": [{\"sheet\": \"s\", \"title\": \"t\", \"contentJson\": \"{}\"}]}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").value(1))
        .andExpect(jsonPath("$.quarters").value(1))
        .andExpect(jsonPath("$.reference").value(1));

    ArgumentCaptor<List<PlanItem>> itemsCaptor = ArgumentCaptor.captor();
    ArgumentCaptor<List<PlanQuarter>> quartersCaptor = ArgumentCaptor.captor();
    ArgumentCaptor<List<PlanReference>> referencesCaptor = ArgumentCaptor.captor();
    verify(planService)
        .importPlan(itemsCaptor.capture(), quartersCaptor.capture(), referencesCaptor.capture());

    PlanItem item = itemsCaptor.getValue().getFirst();
    assertThat(item.getTitle()).isEqualTo("AWS SAA");
    assertThat(item.getStatus()).isEqualTo("not_started");
    assertThat(item.getSortOrder()).isZero();
    // qtr 5 with no explicit yearNum falls in year 2.
    assertThat(quartersCaptor.getValue().getFirst().getYearNum()).isEqualTo(2);
    // References without a sortOrder take their position in the list.
    assertThat(referencesCaptor.getValue().getFirst().getSortOrder()).isZero();
  }

  @Test
  void updateRejectsAnUnknownStatus() throws Exception {
    mvc.perform(
            patch("/api/plan/items/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"weird\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error", startsWith("status must be one of")));
  }

  @Test
  void updateReturns404ForAnUnknownItem() throws Exception {
    when(planService.update(99L, "done", null)).thenReturn(Optional.empty());
    mvc.perform(
            patch("/api/plan/items/99")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"done\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("no such item"));
  }
}
