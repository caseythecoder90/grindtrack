package dev.grindtrack.finance.service;

import dev.grindtrack.finance.domain.Account;
import dev.grindtrack.finance.domain.AccountRepository;
import dev.grindtrack.finance.domain.ImportBatch;
import dev.grindtrack.finance.domain.ImportBatchRepository;
import dev.grindtrack.finance.domain.Transaction;
import dev.grindtrack.finance.domain.TransactionRepository;
import dev.grindtrack.finance.domain.TxnType;
import dev.grindtrack.finance.service.parse.Csv;
import dev.grindtrack.finance.service.parse.ParsedRow;
import dev.grindtrack.finance.service.parse.ParsedStatement;
import dev.grindtrack.finance.service.parse.StatementParseException;
import dev.grindtrack.finance.service.parse.StatementParser;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns an uploaded statement into rows in the database.
 *
 * <p>The pipeline is: detect the format from the header, parse, normalize the merchant, classify
 * the transaction type, drop anything already present, save. The file is never written to disk.
 */
@Service
public class StatementImportService {

  /**
   * Aidvantage prepends an HTML doctype to its header line. Everything else about the file is valid
   * CSV, so the pragmatic fix is to strip the tag rather than reject a whole format.
   */
  private static final String DOCTYPE_PREFIX = "<!DOCTYPE";

  private final List<StatementParser> parsers;
  private final AccountRepository accounts;
  private final TransactionRepository transactions;
  private final ImportBatchRepository batches;
  private final MerchantNormalizer merchantNormalizer;
  private final TxnTypeClassifier classifier;

  public StatementImportService(
      List<StatementParser> parsers,
      AccountRepository accounts,
      TransactionRepository transactions,
      ImportBatchRepository batches,
      MerchantNormalizer merchantNormalizer,
      TxnTypeClassifier classifier) {
    this.parsers = parsers;
    this.accounts = accounts;
    this.transactions = transactions;
    this.batches = batches;
    this.merchantNormalizer = merchantNormalizer;
    this.classifier = classifier;
  }

  /** Outcome of an import, whether or not it was actually committed. */
  public record ImportResult(
      Long batchId,
      String format,
      int rowsInFile,
      int imported,
      int duplicates,
      int pending,
      int skipped,
      String periodStart,
      String periodEnd,
      String balanceUpdate,
      List<String> warnings,
      boolean dryRun) {}

  /**
   * Parses and optionally commits an uploaded statement.
   *
   * @param dryRun when true, nothing is written — the same counts come back so the user can see
   *     what an import would do before doing it. Useful the first time each format is tried.
   */
  @Transactional
  public ImportResult importStatement(
      Long accountId, String filename, String content, boolean dryRun) {

    Account account =
        accounts
            .findById(accountId)
            .orElseThrow(() -> new NoSuchElementException("account " + accountId));

    ParsedStatement statement = parse(content);
    List<String> warnings = new ArrayList<>();

    checkCardMatches(account, statement, warnings);

    int duplicates = 0;
    int imported = 0;
    // A single file can contain the same row twice (Capital One's yearly and monthly exports
    // overlap). Track fingerprints within this file as well as against the database.
    Set<String> seenInFile = new HashSet<>();
    List<Transaction> toSave = new ArrayList<>();

    for (ParsedRow row : statement.rows()) {
      Transaction txn =
          new Transaction(accountId, row.postedDate(), row.amount(), row.description());
      txn.useExternalReference(row.externalReference());

      if (!seenInFile.add(txn.getFingerprint())
          || transactions.existsByAccountIdAndFingerprint(accountId, txn.getFingerprint())) {
        duplicates++;
        continue;
      }

      TxnType type = classifier.classify(row.description(), row.amount(), account.getAccountType());
      txn.applyImportedDetail(
          row.transactionDate(),
          merchantNormalizer.normalize(row.description()),
          row.issuerCategory(),
          type,
          false);
      toSave.add(txn);
      imported++;
    }

    String balanceUpdate = null;
    if (statement.closingBalance() != null) {
      balanceUpdate = statement.closingBalance().toPlainString();
    }

    if (dryRun) {
      return new ImportResult(
          null,
          statement.format().label(),
          statement.rows().size() + statement.pendingSkipped(),
          imported,
          duplicates,
          statement.pendingSkipped(),
          0,
          str(statement.periodStart()),
          str(statement.periodEnd()),
          balanceUpdate,
          warnings,
          true);
    }

    ImportBatch batch =
        batches.save(new ImportBatch(accountId, filename, statement.format().name()));
    for (Transaction txn : toSave) {
      txn.attachToBatch(batch.getId());
    }
    transactions.saveAll(toSave);

    batch.recordCounts(
        statement.rows().size() + statement.pendingSkipped(),
        imported,
        duplicates,
        statement.pendingSkipped(),
        0);
    batch.recordPeriod(statement.periodStart(), statement.periodEnd());
    batches.save(batch);

    // A balance the statement asserts beats one typed in weeks ago.
    if (statement.closingBalance() != null) {
      account.recordBalance(
          account.getAccountType().isLiability()
              ? statement.closingBalance().abs().negate()
              : statement.closingBalance(),
          statement.balanceAsOf());
      accounts.save(account);
    }

    return new ImportResult(
        batch.getId(),
        statement.format().label(),
        batch.getRowsInFile(),
        imported,
        duplicates,
        statement.pendingSkipped(),
        0,
        str(statement.periodStart()),
        str(statement.periodEnd()),
        balanceUpdate,
        warnings,
        false);
  }

  /** Removes every row an import created, leaving hand-entered rows alone. */
  @Transactional
  public long undo(Long batchId) {
    ImportBatch batch =
        batches.findById(batchId).orElseThrow(() -> new NoSuchElementException("batch " + batchId));
    long removed = transactions.deleteByImportBatchId(batch.getId());
    batches.delete(batch);
    return removed;
  }

  public List<ImportBatch> history() {
    return batches.findAllByOrderByImportedAtDesc();
  }

  // ---------- internals ----------

  private ParsedStatement parse(String content) {
    String cleaned = content.stripLeading();
    if (cleaned.regionMatches(true, 0, DOCTYPE_PREFIX, 0, DOCTYPE_PREFIX.length())) {
      int end = cleaned.indexOf('>');
      if (end >= 0) {
        cleaned = cleaned.substring(end + 1);
      }
    }

    List<List<String>> rows = Csv.parse(cleaned);
    if (rows.isEmpty()) {
      throw new StatementParseException("That file is empty.");
    }

    List<String> header = rows.get(0).stream().map(String::trim).toList();
    for (StatementParser parser : parsers) {
      if (parser.canParse(header)) {
        return parser.parse(rows);
      }
    }
    throw new StatementParseException(
        "Unrecognized statement format. Expected an export from Capital One, Chase, "
            + "Wells Fargo, Bank of America or Aidvantage — the header was: "
            + String.join(", ", header));
  }

  /**
   * Capital One names the card in its credit exports. With three Capital One cards, uploading one
   * card's statement into another card's account is easy to do and tedious to unpick, so a mismatch
   * stops the import rather than warning about it.
   */
  private void checkCardMatches(Account account, ParsedStatement statement, List<String> warnings) {
    if (statement.cardNumbers().isEmpty()) {
      return;
    }
    String last4 = account.getLast4();
    if (last4 == null || last4.isBlank()) {
      warnings.add(
          "This file is for card "
              + String.join(", ", statement.cardNumbers())
              + ", but \""
              + account.getName()
              + "\" has no last 4 recorded. Set it so mismatches can be caught.");
      return;
    }
    boolean matches =
        statement.cardNumbers().stream()
            .anyMatch(card -> card.endsWith(last4) || last4.endsWith(card));
    if (!matches) {
      throw new StatementParseException(
          "This file is for card "
              + String.join(", ", statement.cardNumbers())
              + " but \""
              + account.getName()
              + "\" ends in "
              + last4
              + ". Pick the matching account.");
    }
  }

  private static String str(java.time.LocalDate date) {
    return date == null ? null : date.toString();
  }
}
