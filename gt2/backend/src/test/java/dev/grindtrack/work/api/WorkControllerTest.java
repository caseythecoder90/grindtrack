package dev.grindtrack.work.api;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.grindtrack.web.ApiExceptionHandler;
import dev.grindtrack.work.domain.WorkLog;
import dev.grindtrack.work.domain.WorkLogRepository;
import dev.grindtrack.work.domain.WorkSkill;
import dev.grindtrack.work.domain.WorkSkillRepository;
import dev.grindtrack.work.service.WorkService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Standalone MockMvc tests pinning work-tracking validation and CRUD outcomes. */
class WorkControllerTest {

  private WorkLogRepository workLogs;
  private WorkSkillRepository workSkills;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    workLogs = mock(WorkLogRepository.class);
    workSkills = mock(WorkSkillRepository.class);
    mvc =
        MockMvcBuilders.standaloneSetup(new WorkController(new WorkService(workLogs, workSkills)))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
  }

  @Test
  void upsertDaySavesAValidLog() throws Exception {
    when(workLogs.findById(any())).thenReturn(Optional.empty());
    mvc.perform(
            put("/api/work/days/2026-07-27")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"hours\": 8, \"categories\": [\"Feature dev\"], \"did\": \"shipped X\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.saved").value("2026-07-27"));
    verify(workLogs).save(any(WorkLog.class));
  }

  @Test
  void upsertDayRejectsHoursOutOfRange() throws Exception {
    mvc.perform(
            put("/api/work/days/2026-07-27")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hours\": 30}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("hours must be 0-24"));
  }

  @Test
  void upsertDayRejectsAMalformedDate() throws Exception {
    mvc.perform(
            put("/api/work/days/soon")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hours\": 8}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("invalid date"));
  }

  @Test
  void createSkillRequiresAName() throws Exception {
    mvc.perform(
            post("/api/work/skills")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"   \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error", startsWith("a skill needs a name")));
  }

  @Test
  void createSkillSavesAndReturnsIt() throws Exception {
    when(workSkills.count()).thenReturn(2L);
    when(workSkills.save(any(WorkSkill.class)))
        .thenAnswer(inv -> inv.getArgument(0, WorkSkill.class));
    mvc.perform(
            post("/api/work/skills")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Splunk\", \"category\": \"Observability\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Splunk"))
        .andExpect(jsonPath("$.status").value("not_started"))
        .andExpect(jsonPath("$.sortOrder").value(2));
  }

  @Test
  void updateSkillRejectsAnUnknownStatus() throws Exception {
    mvc.perform(
            patch("/api/work/skills/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"expert\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error", startsWith("status must be one of")));
  }

  @Test
  void updateSkillReturns404WhenMissing() throws Exception {
    when(workSkills.findById(99L)).thenReturn(Optional.empty());
    mvc.perform(
            patch("/api/work/skills/99")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"in_progress\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("not found: skill 99"));
  }

  @Test
  void updateSkillAppliesStatusAndNotes() throws Exception {
    WorkSkill skill = new WorkSkill("Grafana", "Observability", "", 0);
    when(workSkills.findById(1L)).thenReturn(Optional.of(skill));
    when(workSkills.save(any(WorkSkill.class)))
        .thenAnswer(inv -> inv.getArgument(0, WorkSkill.class));
    mvc.perform(
            patch("/api/work/skills/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"in_progress\", \"notes\": \"built first dashboard\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("in_progress"))
        .andExpect(jsonPath("$.notes").value("built first dashboard"));
  }

  @Test
  void deleteSkillReturnsTheId() throws Exception {
    mvc.perform(delete("/api/work/skills/5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deleted").value(5));
    verify(workSkills).deleteById(5L);
  }
}
