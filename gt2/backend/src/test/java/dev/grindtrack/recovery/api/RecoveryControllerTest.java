package dev.grindtrack.recovery.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.grindtrack.recovery.domain.JournalEntry;
import dev.grindtrack.recovery.domain.TextSlot;
import dev.grindtrack.recovery.service.ImportReport;
import dev.grindtrack.recovery.service.RecoveryService;
import dev.grindtrack.web.ApiExceptionHandler;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Standalone MockMvc: the shapes accepted and refused at the edge. */
class RecoveryControllerTest {

  private RecoveryService recovery;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    recovery = mock(RecoveryService.class);
    mvc =
        MockMvcBuilders.standaloneSetup(new RecoveryController(recovery))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
  }

  @Test
  void importIsADryRunUnlessToldOtherwiseAndTheFileIsPassedAsText() throws Exception {
    when(recovery.importText(
            eq(TextSlot.BIG_BOOK), any(), eq("HOW IT WORKS\n\nRarely."), anyBoolean()))
        .thenReturn(
            new ImportReport(
                true,
                "big_book",
                "Big Book",
                1,
                1,
                List.of(new ImportReport.Chapter(1, "How It Works", 1, 1)),
                0,
                0,
                List.of(),
                "Rarely.",
                List.of(),
                false));

    mvc.perform(
            multipart("/api/recovery/import/big_book")
                .file(
                    new MockMultipartFile(
                        "file",
                        "book.txt",
                        "text/plain",
                        "HOW IT WORKS\n\nRarely.".getBytes(StandardCharsets.UTF_8))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dryRun").value(true))
        .andExpect(jsonPath("$.chapters[0].title").value("How It Works"));

    verify(recovery).importText(TextSlot.BIG_BOOK, null, "HOW IT WORKS\n\nRarely.", true);
  }

  @Test
  void anUnknownSlotAndAnEmptyFileAreRefused() throws Exception {
    mvc.perform(
            multipart("/api/recovery/import/cookbook")
                .file(new MockMultipartFile("file", "x.txt", "text/plain", "x".getBytes())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("slot must be big_book, reflection or meditation"));

    mvc.perform(
            multipart("/api/recovery/import/reflection")
                .file(new MockMultipartFile("file", "x.txt", "text/plain", new byte[0])))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("That file is empty."));
  }

  @Test
  void aJournalEntryNeedsWords() throws Exception {
    mvc.perform(
            post("/api/recovery/journal")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\": \"   \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("a journal entry needs some words (max 20000 chars)"));

    when(recovery.addJournal("Slept badly.", true))
        .thenReturn(new JournalEntry("Slept badly.", true));
    mvc.perform(
            post("/api/recovery/journal")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\": \" Slept badly. \", \"spoken\": true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.body").value("Slept badly."))
        .andExpect(jsonPath("$.spoken").value(true));
  }

  @Test
  void minutesAreBounded() throws Exception {
    mvc.perform(
            post("/api/recovery/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"minutes\": 0}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("minutes must be between 1 and 180"));
    mvc.perform(
            post("/api/recovery/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"completed\": true}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("minutes is required"));

    when(recovery.updateSettings(12, null)).thenReturn(new RecoveryService.Settings(12, 10));
    mvc.perform(
            put("/api/recovery/settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"readMinutes\": 12}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.readMinutes").value(12));
  }

  @Test
  void deletingAnEntryThatIsNotThereIs404() throws Exception {
    when(recovery.deleteJournal(5L)).thenReturn(false);

    mvc.perform(delete("/api/recovery/journal/5")).andExpect(status().isNotFound());
  }
}
