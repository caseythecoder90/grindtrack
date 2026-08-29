package dev.grindtrack.finance.api;

import dev.grindtrack.finance.domain.ImportBatch;
import dev.grindtrack.finance.service.parse.StatementFormat;

/** Request/response shapes for statement upload. */
public final class StatementImportDtos {

  private StatementImportDtos() {}

  /**
   * One upload, as it turned out.
   *
   * @param rowsDuplicate rows already on file. A high count is the normal result of re-uploading an
   *     overlapping statement, not a failure — which is why it is reported rather than hidden
   * @param rowsPending rows the bank had not settled yet, held back until they do
   */
  public record ImportBatchResponse(
      Long id,
      Long accountId,
      String filename,
      String sourceFormat,
      int rowsInFile,
      int rowsImported,
      int rowsDuplicate,
      int rowsPending,
      String periodStart,
      String periodEnd,
      String importedAt) {

    public static ImportBatchResponse from(ImportBatch b) {
      return new ImportBatchResponse(
          b.getId(),
          b.getAccountId(),
          b.getFilename(),
          StatementFormat.labelOf(b.getSourceFormat()),
          b.getRowsInFile(),
          b.getRowsImported(),
          b.getRowsDuplicate(),
          b.getRowsPending(),
          b.getPeriodStart() == null ? null : b.getPeriodStart().toString(),
          b.getPeriodEnd() == null ? null : b.getPeriodEnd().toString(),
          b.getImportedAt().toString());
    }
  }

  /** What undoing an import removed. The count is the point: it confirms the right batch went. */
  public record UndoResponse(Long undone, long transactionsRemoved) {}
}
