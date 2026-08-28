package dev.grindtrack.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * One uploaded statement file.
 *
 * <p>Exists so an import can be explained afterwards — "40 rows in the file, 12 new, 27 already
 * present, 1 still pending" — and undone as a unit when a file goes into the wrong account, which
 * with three Capital One cards is a live possibility.
 *
 * <p>The file itself is never stored. It is parsed in memory and discarded: this repo is public,
 * the deployed instance holds real money data, and a statement sitting on disk is a liability with
 * no upside once its rows are in the database.
 */
@Entity
@Table(name = "finance_import_batches")
public class ImportBatch {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "account_id", nullable = false)
  private Long accountId;

  @Column(nullable = false)
  private String filename;

  @Column(name = "source_format", nullable = false)
  private String sourceFormat;

  @Column(name = "rows_in_file", nullable = false)
  private int rowsInFile;

  @Column(name = "rows_imported", nullable = false)
  private int rowsImported;

  @Column(name = "rows_duplicate", nullable = false)
  private int rowsDuplicate;

  @Column(name = "rows_pending", nullable = false)
  private int rowsPending;

  @Column(name = "rows_skipped", nullable = false)
  private int rowsSkipped;

  @Column(name = "period_start")
  private LocalDate periodStart;

  @Column(name = "period_end")
  private LocalDate periodEnd;

  /**
   * What the account balance was before this import overwrote it, so undo can put it back.
   *
   * <p>{@code balanceOverwritten} carries the distinction the two nullable columns cannot: an
   * import that never touched the balance versus one that set it from a statement which itself had
   * no date.
   */
  @Column(name = "previous_balance")
  private BigDecimal previousBalance;

  @Column(name = "previous_balance_as_of")
  private LocalDate previousBalanceAsOf;

  @Column(name = "balance_overwritten", nullable = false)
  private boolean balanceOverwritten = false;

  @Column(name = "imported_at", nullable = false)
  private OffsetDateTime importedAt = OffsetDateTime.now();

  protected ImportBatch() {}

  public ImportBatch(Long accountId, String filename, String sourceFormat) {
    this.accountId = accountId;
    this.filename = filename;
    this.sourceFormat = sourceFormat;
  }

  public void recordCounts(int inFile, int imported, int duplicate, int pending, int skipped) {
    this.rowsInFile = inFile;
    this.rowsImported = imported;
    this.rowsDuplicate = duplicate;
    this.rowsPending = pending;
    this.rowsSkipped = skipped;
  }

  public void recordPeriod(LocalDate start, LocalDate end) {
    this.periodStart = start;
    this.periodEnd = end;
  }

  /** Captures the balance about to be overwritten. Call before applying the statement's figure. */
  public void snapshotBalance(BigDecimal balance, LocalDate asOf) {
    this.previousBalance = balance;
    this.previousBalanceAsOf = asOf;
    this.balanceOverwritten = true;
  }

  public boolean isBalanceOverwritten() {
    return balanceOverwritten;
  }

  public BigDecimal getPreviousBalance() {
    return previousBalance;
  }

  public LocalDate getPreviousBalanceAsOf() {
    return previousBalanceAsOf;
  }

  public Long getId() {
    return id;
  }

  public Long getAccountId() {
    return accountId;
  }

  public String getFilename() {
    return filename;
  }

  public String getSourceFormat() {
    return sourceFormat;
  }

  public int getRowsInFile() {
    return rowsInFile;
  }

  public int getRowsImported() {
    return rowsImported;
  }

  public int getRowsDuplicate() {
    return rowsDuplicate;
  }

  public int getRowsPending() {
    return rowsPending;
  }

  public int getRowsSkipped() {
    return rowsSkipped;
  }

  public LocalDate getPeriodStart() {
    return periodStart;
  }

  public LocalDate getPeriodEnd() {
    return periodEnd;
  }

  public OffsetDateTime getImportedAt() {
    return importedAt;
  }
}
