package dev.grindtrack.finance.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.grindtrack.finance.domain.ImportBatch;
import dev.grindtrack.finance.service.StatementImportService;
import dev.grindtrack.finance.service.StatementImportService.ImportResult;
import dev.grindtrack.finance.service.parse.StatementParseException;
import dev.grindtrack.web.ApiExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * The upload endpoint.
 *
 * <p>Most of what matters here is refusal: an empty file, one too large to be a statement, and a
 * parse failure all have to come back as a 400 carrying a message written for a person. The parsers
 * and the import service are tested elsewhere; this pins the HTTP edge around them.
 */
class StatementImportControllerTest {

  private StatementImportService imports;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    imports = mock(StatementImportService.class);
    mvc =
        MockMvcBuilders.standaloneSetup(new StatementImportController(imports))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
  }

  private static MockMultipartFile file(String name, byte[] content) {
    return new MockMultipartFile("file", name, "text/csv", content);
  }

  private static ImportResult result() {
    return new ImportResult(
        7L, "Chase card", 68, 66, 1, 0, 1, 12, "2026-01-02", "2026-08-23", null, List.of(), false);
  }

  @Test
  void anEmptyFileIsRefusedWithAReadableMessage() throws Exception {
    mvc.perform(
            multipart("/api/finance/imports")
                .file(file("empty.csv", new byte[0]))
                .param("accountId", "1"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("empty")));

    verify(imports, never()).importStatement(anyLong(), anyString(), anyString(), anyBoolean());
  }

  @Test
  void aFileTooLargeToBeAStatementIsRefusedBeforeItIsRead() throws Exception {
    // Nothing a bank exports is anywhere near this; the largest real one is 34 KB.
    byte[] huge = new byte[6 * 1024 * 1024];
    java.util.Arrays.fill(huge, (byte) 'a');

    mvc.perform(
            multipart("/api/finance/imports").file(file("huge.csv", huge)).param("accountId", "1"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("larger than")));

    verify(imports, never()).importStatement(anyLong(), anyString(), anyString(), anyBoolean());
  }

  @Test
  void anUnrecognizedFormatComesBackAsA400NotA500() throws Exception {
    when(imports.importStatement(anyLong(), anyString(), anyString(), anyBoolean()))
        .thenThrow(new StatementParseException("Unrecognized statement format."));

    mvc.perform(
            multipart("/api/finance/imports")
                .file(file("mystery.csv", "foo,bar\n1,2\n".getBytes()))
                .param("accountId", "1"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Unrecognized")));
  }

  @Test
  void aWrongAccountUploadIsRefusedWithTheMismatchSpelledOut() throws Exception {
    // The guard that matters most: it has to say which card, not just "invalid".
    when(imports.importStatement(anyLong(), anyString(), anyString(), anyBoolean()))
        .thenThrow(
            new StatementParseException(
                "This file is for card 7575 but \"Quicksilver\" ends in 6768."));

    mvc.perform(
            multipart("/api/finance/imports")
                .file(file("savor.csv", "a,b\n1,2\n".getBytes()))
                .param("accountId", "1"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("7575")))
        .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("6768")));
  }

  @Test
  void aSuccessfulImportReturnsCountsThatAddUp() throws Exception {
    when(imports.importStatement(eq(1L), anyString(), anyString(), eq(false))).thenReturn(result());

    mvc.perform(
            multipart("/api/finance/imports")
                .file(file("chase.csv", "a,b\n1,2\n".getBytes()))
                .param("accountId", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rowsInFile").value(68))
        .andExpect(jsonPath("$.imported").value(66))
        .andExpect(jsonPath("$.duplicates").value(1))
        .andExpect(jsonPath("$.skipped").value(1))
        .andExpect(jsonPath("$.categorized").value(12))
        .andExpect(jsonPath("$.dryRun").value(false));
  }

  @Test
  void dryRunIsPassedThroughRatherThanIgnored() throws Exception {
    when(imports.importStatement(anyLong(), anyString(), anyString(), anyBoolean()))
        .thenReturn(result());

    mvc.perform(
            multipart("/api/finance/imports")
                .file(file("chase.csv", "a,b\n1,2\n".getBytes()))
                .param("accountId", "1")
                .param("dryRun", "true"))
        .andExpect(status().isOk());

    verify(imports).importStatement(eq(1L), eq("chase.csv"), anyString(), eq(true));
  }

  @Test
  void historyRendersTheFormatLabelNotTheStoredEnumName() throws Exception {
    // The batch persists WELLS_FARGO; a screen must never show that.
    ImportBatch batch = new ImportBatch(1L, "wf.csv", "WELLS_FARGO");
    batch.recordCounts(153, 153, 0, 0, 0);
    when(imports.history()).thenReturn(List.of(batch));

    mvc.perform(get("/api/finance/imports"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].sourceFormat").value("Wells Fargo card"));
  }

  @Test
  void undoReportsHowManyRowsItRemoved() throws Exception {
    when(imports.undo(7L)).thenReturn(153L);

    mvc.perform(delete("/api/finance/imports/7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.transactionsRemoved").value(153));
  }

  @Test
  void undoingABatchThatIsNotThereAnswers404() throws Exception {
    when(imports.undo(any())).thenThrow(new java.util.NoSuchElementException("batch 99"));

    mvc.perform(delete("/api/finance/imports/99")).andExpect(status().isNotFound());
  }
}
