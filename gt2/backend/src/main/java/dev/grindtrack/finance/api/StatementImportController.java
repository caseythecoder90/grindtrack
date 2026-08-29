package dev.grindtrack.finance.api;

import dev.grindtrack.finance.api.StatementImportDtos.ImportBatchResponse;
import dev.grindtrack.finance.api.StatementImportDtos.UndoResponse;
import dev.grindtrack.finance.service.StatementImportService;
import dev.grindtrack.finance.service.StatementImportService.ImportResult;
import dev.grindtrack.finance.service.parse.StatementParseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Statement upload.
 *
 * <p>Files are read straight out of the request into memory and never written to disk. The parsed
 * rows are the product; keeping the original around would put real bank data on a server for no
 * benefit.
 *
 * <p>There is no {@code @ExceptionHandler} here: {@link StatementParseException} is a {@code
 * BadRequestException}, so the one shared advice turns it into the same 400 body every other
 * malformed request gets.
 */
@RestController
@RequestMapping("/api/finance/imports")
public class StatementImportController {

  /**
   * Comfortably above the largest real export seen (~40 KB) without inviting an upload of a DVD.
   */
  private static final long MAX_BYTES = 5L * 1024 * 1024;

  private static final String DEFAULT_FILENAME = "statement.csv";

  private final StatementImportService imports;

  public StatementImportController(StatementImportService imports) {
    this.imports = imports;
  }

  @GetMapping
  public List<ImportBatchResponse> history() {
    return imports.history().stream().map(ImportBatchResponse::from).toList();
  }

  /**
   * @param dryRun parse and report without writing anything — worth doing the first time each
   *     bank's export is tried, since the counts alone reveal a wrong-account upload
   */
  @PostMapping
  public ImportResult upload(
      @RequestParam Long accountId,
      @RequestParam(defaultValue = "false") boolean dryRun,
      @RequestParam("file") MultipartFile file)
      throws IOException {

    if (file.isEmpty()) {
      throw new StatementParseException("That file is empty.");
    }
    if (file.getSize() > MAX_BYTES) {
      throw new StatementParseException(
          "That file is larger than 5 MB — is it really a statement?");
    }

    String filename = file.getOriginalFilename();
    String content = new String(file.getBytes(), StandardCharsets.UTF_8);
    return imports.importStatement(
        accountId, filename == null ? DEFAULT_FILENAME : filename, content, dryRun);
  }

  @DeleteMapping("/{id}")
  public UndoResponse undo(@PathVariable Long id) {
    return new UndoResponse(id, imports.undo(id));
  }
}
