package dev.grindtrack.tracking.api;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.grindtrack.tracking.domain.FocusKind;
import dev.grindtrack.tracking.domain.FocusSession;
import dev.grindtrack.tracking.service.FocusService;
import dev.grindtrack.web.ApiExceptionHandler;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * The focus endpoints' shape validation.
 *
 * <p>This surface had no test at all while it was hand-rolling its own null-returning parsers and
 * its own {@code badRequest} helper, which is roughly why it had drifted furthest from the shared
 * {@code web} package. These pin what a malformed session gets back.
 */
class FocusControllerTest {

  private static final LocalDate DATE = LocalDate.of(2026, 8, 26);
  private static final String STARTED_AT = "2026-08-26T19:00:00Z";

  private FocusService focusService;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    focusService = mock(FocusService.class);
    mvc =
        MockMvcBuilders.standaloneSetup(new FocusController(focusService))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    when(focusService.record(any(), any(), anyInt(), anyBoolean(), any()))
        .thenReturn(
            new FocusSession(DATE, OffsetDateTime.parse(STARTED_AT), 25, true, FocusKind.STUDY));
    when(focusService.sessionsOn(any(), any())).thenReturn(List.of());
  }

  private static String body(String date, String startedAt, String minutes, String kind) {
    return "{\"date\":\"%s\",\"startedAt\":\"%s\",\"durationMinutes\":%s,\"completed\":true,\"kind\":\"%s\"}"
        .formatted(date, startedAt, minutes, kind);
  }

  @Test
  void recordsASessionAndEchoesTheLowerCaseKind() throws Exception {
    mvc.perform(
            post("/api/focus/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("2026-08-26", STARTED_AT, "25", "study")))
        .andExpect(status().isOk())
        // The frontend's FocusKind union is "study" | "work"; the enum must not leak as STUDY.
        .andExpect(jsonPath("$.kind").value("study"));

    verify(focusService).record(DATE, OffsetDateTime.parse(STARTED_AT), 25, true, FocusKind.STUDY);
  }

  @Test
  void anAbsentKindMeansStudyRatherThanAnError() throws Exception {
    mvc.perform(
            post("/api/focus/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"date\":\"2026-08-26\",\"startedAt\":\"%s\",\"durationMinutes\":25}"
                        .formatted(STARTED_AT)))
        .andExpect(status().isOk());

    verify(focusService).record(any(), any(), eq(25), eq(false), eq(FocusKind.STUDY));
  }

  @Test
  void anUnknownKindIsRejectedRatherThanLoggedAsStudy() throws Exception {
    // The whole reason FocusKind exists: "personal" used to fall back to study silently.
    mvc.perform(
            post("/api/focus/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("2026-08-26", STARTED_AT, "25", "personal")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value(containsString("kind")));

    verify(focusService, never()).record(any(), any(), anyInt(), anyBoolean(), any());
  }

  @Test
  void aMalformedDateOrTimestampIsRejected() throws Exception {
    mvc.perform(
            post("/api/focus/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("26-08-2026", STARTED_AT, "25", "study")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value(containsString("date")));

    mvc.perform(
            post("/api/focus/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("2026-08-26", "7pm", "25", "study")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value(containsString("startedAt")));
  }

  @Test
  void aDurationOutsideOneDayIsRejected() throws Exception {
    for (String minutes : List.of("0", "-5", "1441", "null")) {
      mvc.perform(
              post("/api/focus/sessions")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body("2026-08-26", STARTED_AT, minutes, "study")))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error").value(containsString("durationMinutes")));
    }
  }

  @Test
  void listingFiltersByKindAndTreatsAnAbsentOneAsBoth() throws Exception {
    mvc.perform(get("/api/focus/sessions").param("date", "2026-08-26").param("kind", "work"))
        .andExpect(status().isOk());
    verify(focusService).sessionsOn(DATE, FocusKind.WORK);

    mvc.perform(get("/api/focus/sessions").param("date", "2026-08-26")).andExpect(status().isOk());
    verify(focusService).sessionsOn(DATE, null);
  }

  @Test
  void listingWithAnUnknownKindIsRejected() throws Exception {
    mvc.perform(get("/api/focus/sessions").param("date", "2026-08-26").param("kind", "personal"))
        .andExpect(status().isBadRequest());
  }
}
