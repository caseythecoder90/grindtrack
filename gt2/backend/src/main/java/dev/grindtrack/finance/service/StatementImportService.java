package dev.grindtrack.finance.service;

import dev.grindtrack.finance.domain.Account;
import dev.grindtrack.finance.domain.AccountRepository;
import dev.grindtrack.finance.domain.AccountType;
import dev.grindtrack.finance.domain.ImportBatch;
import dev.grindtrack.finance.domain.ImportBatchRepository;
import dev.grindtrack.finance.domain.Transaction;
import dev.grindtrack.finance.domain.TransactionRepository;
import dev.grindtrack.finance.domain.TxnType;
import dev.grindtrack.finance.service.parse.Csv;
import dev.grindtrack.finance.service.parse.OfxInvestmentParser;
import dev.grindtrack.finance.service.parse.ParsedRow;
import dev.grindtrack.finance.service.parse.ParsedStatement;
import dev.grindtrack.finance.service.parse.StatementFormat;
import dev.grindtrack.finance.service.parse.StatementParseException;
import dev.grindtrack.finance.service.parse.StatementParser;
import java.time.LocalDate;
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
 * the transaction type, apply category rules, drop anything already present, save. The file is
 * never written to disk.
 *
 * <p>Two guards stand between a mis-click and a corrupted ledger, and both refuse rather than warn.
 * A Capital One card file naming a different card than the chosen account is rejected, and so is
 * any file whose format does not belong to the chosen account's type — uploading the student-loan
 * export into checking would otherwise set that account's balance to the loan principal.
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
  private final CategoryRuleService categoryRules;
  private final OfxInvestmentParser ofx;

  public StatementImportService(
      List<StatementParser> parsers,
      AccountRepository accounts,
      TransactionRepository transactions,
      ImportBatchRepository batches,
      MerchantNormalizer merchantNormalizer,
      TxnTypeClassifier classifier,
      CategoryRuleService categoryRules,
      OfxInvestmentParser ofx) {
    this.parsers = parsers;
    this.accounts = accounts;
    this.transactions = transactions;
    this.batches = batches;
    this.merchantNormalizer = merchantNormalizer;
    this.classifier = classifier;
    this.categoryRules = categoryRules;
    this.ofx = ofx;
  }

  /**
   * Outcome of an import, whether or not it was actually committed.
   *
   * <p>The four counts are exhaustive by construction: {@code rowsInFile} equals {@code imported +
   * duplicates + pending + skipped}. That is the point of reporting them together — a file whose
   * numbers do not add up is a file that lost rows, and the screen shows it rather than reporting a
   * confident smaller number.
   */
  public record ImportResult(
      Long batchId,
      String format,
      int rowsInFile,
      int imported,
      int duplicates,
      int pending,
      int skipped,
      int categorized,
      String periodStart,
      String periodEnd,
      String balanceUpdate,
      List<String> warnings,
      boolean dryRun) {}

  /**
   * Parses and optionally commits an uploaded statement.
   *
   * @param dryRun when true, nothing is written — the same counts come back so the user can see
   *     what an import would do before doing it. Worth doing the first time each format is tried.
   */
  @Transactional
  public ImportResult importStatement(
      Long accountId, String filename, String content, boolean dryRun) {

    Account account =
        accounts
            .findById(accountId)
            .orElseThrow(() -> new NoSuchElementException("account " + accountId));

    ParsedStatement statement = parse(content);
    List<String> warnings = new ArrayList<>(statement.notes());

    checkFormatSuitsAccount(account, statement);
    checkCardMatches(account, statement, warnings);

    // One query instead of one per row: a 420-row export was making 420 round trips.
    Set<String> known = new HashSet<>(transactions.findFingerprintsByAccountId(accountId));

    int duplicates = 0;
    List<Transaction> toSave = new ArrayList<>();

    for (ParsedRow row : statement.rows()) {
      Transaction txn =
          new Transaction(accountId, row.postedDate(), row.amount(), row.description());
      txn.useExternalReference(row.externalReference());

      // known covers both the database and rows already accepted from this same file — Capital
      // One's yearly and monthly exports overlap, so a file can contain a row twice.
      if (!known.add(txn.getFingerprint())) {
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
    }

    int categorized = categoryRules.applyTo(toSave, !dryRun);
    int imported = toSave.size();
    String balanceUpdate =
        statement.closingBalance() == null ? null : statement.closingBalance().toPlainString();

    if (dryRun) {
      return result(
          null, statement, imported, duplicates, categorized, balanceUpdate, warnings, true);
    }

    ImportBatch batch =
        batches.save(new ImportBatch(accountId, filename, statement.format().name()));
    for (Transaction txn : toSave) {
      txn.attachToBatch(batch.getId());
    }
    transactions.saveAll(toSave);

    batch.recordCounts(
        statement.dataRowCount(),
        imported,
        duplicates,
        statement.pendingSkipped(),
        statement.unreadableSkipped());
    batch.recordPeriod(statement.periodStart(), statement.periodEnd());

    // A balance the statement asserts beats one typed in weeks ago — but snapshot the old reading
    // first, so undoing this import can put it back.
    if (statement.closingBalance() != null) {
      batch.snapshotBalance(account.getCurrentBalance(), account.getBalanceAsOf());
      account.recordBalance(
          account.getAccountType().isLiability()
              ? statement.closingBalance().abs().negate()
              : statement.closingBalance(),
          statement.balanceAsOf());
      accounts.save(account);
    }
    batches.save(batch);

    return result(
        batch.getId(),
        statement,
        imported,
        duplicates,
        categorized,
        balanceUpdate,
        warnings,
        false);
  }

  /**
   * Removes every row an import created and restores the balance it overwrote, leaving hand-entered
   * rows alone.
   */
  @Transactional
  public long undo(Long batchId) {
    ImportBatch batch =
        batches.findById(batchId).orElseThrow(() -> new NoSuchElementException("batch " + batchId));
    long removed = transactions.deleteByImportBatchId(batch.getId());

    if (batch.isBalanceOverwritten()) {
      accounts
          .findById(batch.getAccountId())
          .ifPresent(
              account -> {
                account.recordBalance(batch.getPreviousBalance(), batch.getPreviousBalanceAsOf());
                accounts.save(account);
              });
    }

    batches.delete(batch);
    return removed;
  }

  public List<ImportBatch> history() {
    return batches.findAllByOrderByImportedAtDesc();
  }

  // ---------- internals ----------

  private ImportResult result(
      Long batchId,
      ParsedStatement statement,
      int imported,
      int duplicates,
      int categorized,
      String balanceUpdate,
      List<String> warnings,
      boolean dryRun) {

    List<String> all = new ArrayList<>(warnings);
    if (statement.unreadableSkipped() > 0) {
      all.add(
          statement.unreadableSkipped()
              + " row(s) could not be read and were skipped. A run of these usually means the bank"
              + " changed its export layout — worth opening the file before trusting these totals.");
    }

    return new ImportResult(
        batchId,
        statement.format().label(),
        statement.dataRowCount(),
        imported,
        duplicates,
        statement.pendingSkipped(),
        statement.unreadableSkipped(),
        categorized,
        str(statement.periodStart()),
        str(statement.periodEnd()),
        balanceUpdate,
        List.copyOf(all),
        dryRun);
  }

  private ParsedStatement parse(String content) {
    // OFX/QFX is SGML rather than a table, so it is checked before anything tries to read it as
    // rows of cells. See OfxInvestmentParser for why it does not share the CSV parser interface.
    if (ofx.canParse(content)) {
      return ofx.parse(content);
    }

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
        "Unrecognized statement format. Expected a CSV export from Capital One, Chase, "
            + "Wells Fargo, Bank of America or Aidvantage, or an OFX/QFX investment file — "
            + "the header was: "
            + String.join(", ", header));
  }

  /**
   * Refuses a file whose format cannot belong to this account's type.
   *
   * <p>The card-number check below only protects Capital One credit exports, because they are the
   * only format that names the card. This covers the other five. The damage it prevents is not
   * hypothetical: the Aidvantage export asserts a balance, so uploading it into checking would
   * replace that account's balance with the student-loan principal and import nothing, and the
   * Capital One deposit export into a card account would import hundreds of rows whose signs mean
   * the opposite of what a card statement means.
   */
  private void checkFormatSuitsAccount(Account account, ParsedStatement statement) {
    List<AccountType> allowed = permittedTypes(statement.format());
    if (allowed.contains(account.getAccountType())) {
      return;
    }
    throw new StatementParseException(
        "That is a "
            + statement.format().label()
            + " export, which belongs to a "
            + describe(allowed)
            + " account — but \""
            + account.getName()
            + "\" is a "
            + account.getAccountType().name().toLowerCase().replace('_', ' ')
            + " account. Pick the matching account.");
  }

  private static List<AccountType> permittedTypes(StatementFormat format) {
    return switch (format) {
      case CAPITAL_ONE_DEPOSIT -> List.of(AccountType.CHECKING, AccountType.SAVINGS);
      case AIDVANTAGE -> List.of(AccountType.LOAN);
      case OFX_INVESTMENT -> List.of(AccountType.RETIREMENT);
      case CAPITAL_ONE_CREDIT, CHASE, BANK_OF_AMERICA, WELLS_FARGO ->
          List.of(AccountType.CREDIT_CARD);
    };
  }

  private static String describe(List<AccountType> types) {
    return String.join(
        " or ", types.stream().map(t -> t.name().toLowerCase().replace('_', ' ')).toList());
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

  private static String str(LocalDate date) {
    return date == null ? null : date.toString();
  }
}
