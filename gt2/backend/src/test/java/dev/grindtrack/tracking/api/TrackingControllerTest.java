package dev.grindtrack.tracking.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.grindtrack.tracking.domain.DailyLog;
import dev.grindtrack.tracking.domain.DailyLogRepository;
import dev.grindtrack.tracking.domain.WeeklyReview;
import dev.grindtrack.tracking.domain.WeeklyReviewRepository;
import dev.grindtrack.tracking.service.StatsService;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Standalone MockMvc tests pinning the parse-or-400 behavior and error bodies. */
class TrackingControllerTest {

  private DailyLogRepository dailyLogs;
  private WeeklyReviewRepository weeklyReviews;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    dailyLogs = mock(DailyLogRepository.class);
    weeklyReviews = mock(WeeklyReviewRepository.class);
    mvc =
        MockMvcBuilders.standaloneSetup(
                new TrackingController(dailyLogs, weeklyReviews, mock(StatsService.class)))
            .build();
  }

  @Test
  void rangeRejectsMalformedDates() throws Exception {
    mvc.perform(get("/api/days").param("from", "nope").param("to", "2026-01-05"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("from and to must be YYYY-MM-DD"));
  }

  @Test
  void dayRejectsAMalformedDate() throws Exception {
    mvc.perform(get("/api/days/nope"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("invalid date"));
  }

  @Test
  void deleteDayRejectsAMalformedDate() throws Exception {
    mvc.perform(delete("/api/days/nope"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("invalid date"));
  }

  @Test
  void upsertDayRejectsHoursOverTwentyFour() throws Exception {
    mvc.perform(
            put("/api/days/2026-01-05")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hours\": 25}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("hours must be 0-24"));
  }

  @Test
  void upsertDayRejectsEnergyOutOfRange() throws Exception {
    mvc.perform(
            put("/api/days/2026-01-05")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hours\": 1, \"energy\": 6}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("energy must be 1-5"));
  }

  @Test
  void upsertDaySavesAndEchoesTheDate() throws Exception {
    mvc.perform(
            put("/api/days/2026-01-05")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hours\": 2.5, \"categories\": [\"java\"], \"focus\": \"jpa\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.saved").value("2026-01-05"));
    verify(dailyLogs).save(any(DailyLog.class));
  }

  @Test
  void weekRejectsAMalformedDate() throws Exception {
    mvc.perform(get("/api/weeks/nope"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("invalid date"));
  }

  @Test
  void upsertWeekSnapsAnyDayOfTheWeekToItsMonday() throws Exception {
    // 2026-01-07 is a Wednesday; its week's Monday is 2026-01-05.
    mvc.perform(
            put("/api/weeks/2026-01-07")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"summary\": \"good week\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.saved").value("2026-01-05"));
    verify(weeklyReviews).findById(LocalDate.of(2026, 1, 5));
    verify(weeklyReviews).save(any(WeeklyReview.class));
  }
}
