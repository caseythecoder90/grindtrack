package dev.grindtrack.recovery.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.grindtrack.recovery.domain.JournalEntry;
import dev.grindtrack.recovery.domain.PersonRole;
import dev.grindtrack.recovery.domain.TextSlot;
import dev.grindtrack.recovery.service.ImportReport;
import dev.grindtrack.recovery.service.RecoveryService;
import dev.grindtrack.web.ApiExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
  void importIsADryRunUnlessToldOtherwiseAndEveryFileIsPassedAlong() throws Exception {
    when(recovery.importFiles(eq(TextSlot.BIG_BOOK), any(), any(), anyBoolean()))
        .thenReturn(
            new ImportReport(
                true,
                "big_book",
                "Big Book",
                1,
                1,
                1,
                List.of(new ImportReport.Chapter(1, "How It Works", 1, 1, "58", "58")),
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
                        "files", "chapt5.pdf", "application/pdf", "%PDF-1".getBytes()))
                .file(
                    new MockMultipartFile(
                        "files", "chapt6.pdf", "application/pdf", "%PDF-2".getBytes())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dryRun").value(true))
        .andExpect(jsonPath("$.chapters[0].firstPage").value("58"));

    ArgumentCaptor<List<RecoveryService.Upload>> uploads = ArgumentCaptor.captor();
    verify(recovery).importFiles(eq(TextSlot.BIG_BOOK), eq(null), uploads.capture(), eq(true));
    assertThat(uploads.getValue())
        .extracting(RecoveryService.Upload::name)
        .containsExactly("chapt5.pdf", "chapt6.pdf");
  }

  @Test
  void anUnknownSlotAndAnEmptyFileAreRefused() throws Exception {
    mvc.perform(
            multipart("/api/recovery/import/cookbook")
                .file(new MockMultipartFile("files", "x.txt", "text/plain", "x".getBytes())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("slot must be big_book, reflection or meditation"));

    mvc.perform(
            multipart("/api/recovery/import/reflection")
                .file(new MockMultipartFile("files", "x.txt", "text/plain", new byte[0])))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("Choose at least one file."));
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

    when(recovery.updateSettings(3, null)).thenReturn(new RecoveryService.Settings(3, 10));
    mvc.perform(
            put("/api/recovery/settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pagesPerDay\": 3}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pagesPerDay").value(3));
    mvc.perform(
            put("/api/recovery/settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pagesPerDay\": 0}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("pagesPerDay must be between 1 and 50"));
  }

  @Test
  void aPersonNeedsANameAndAKnownRole() throws Exception {
    mvc.perform(
            post("/api/recovery/people")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Mike\", \"role\": \"boss\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("role must be sponsor, prospect or friend"));

    when(recovery.addPerson("Mike", PersonRole.SPONSOR, 7, "Georgia"))
        .thenReturn(
            new RecoveryService.PersonView(
                1L, "Mike", "sponsor", 7, "Georgia", null, null, "2026-09-20", 0, "ok", 0));
    mvc.perform(
            post("/api/recovery/people")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \" Mike \", \"role\": \"sponsor\", \"note\": \"Georgia\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Mike"))
        .andExpect(jsonPath("$.state").value("ok"));
  }

  @Test
  void searchNeedsAWordAndAPageThatIsNotThereIs404() throws Exception {
    mvc.perform(get("/api/recovery/book/search?q=a"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("search needs a word"));

    when(recovery.search("half measures"))
        .thenReturn(
            List.of(new RecoveryService.Hit(400, 11, "How It Works", "59", "…half measures…")));
    mvc.perform(get("/api/recovery/book/search").param("q", "half measures"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].pageLabel").value("59"));

    when(recovery.page("999")).thenReturn(java.util.Optional.empty());
    mvc.perform(get("/api/recovery/book/page/999")).andExpect(status().isNotFound());
  }

  @Test
  void deletingAnEntryThatIsNotThereIs404() throws Exception {
    when(recovery.deleteJournal(5L)).thenReturn(false);

    mvc.perform(delete("/api/recovery/journal/5")).andExpect(status().isNotFound());
  }
}
