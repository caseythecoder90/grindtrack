package dev.grindtrack.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HexFormat;

/**
 * One line on one statement.
 *
 * <p>{@code amount} is always signed the same way regardless of which bank it came from: negative
 * is money leaving the account, positive is money arriving. Importers normalize into that from
 * three different conventions — Capital One's separate Debit/Credit columns, its deposit accounts'
 * Debit/Credit <em>type</em> flag with an unsigned amount, and the already-signed Amount that
 * Chase, Bank of America and Wells Fargo export.
 *
 * <p>{@code rawDescription} is never edited. It is the audit trail, and it is the input to merchant
 * normalization, which will keep improving and will want re-running over old rows.
 */
@Entity
@Table(name = "finance_transactions")
public class Transaction {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "account_id", nullable = false)
  private Long accountId;

  @Column(name = "posted_date", nullable = false)
  private LocalDate postedDate;

  @Column(name = "transaction_date")
  private LocalDate transactionDate;

  @Column(nullable = false)
  private BigDecimal amount;

  @Column(name = "raw_description", nullable = false)
  private String rawDescription;

  @Column private String merchant;

  @Enumerated(EnumType.STRING)
  @Column(name = "txn_type", nullable = false)
  private TxnType txnType = TxnType.SPEND;

  @Column private String category;

  @Column(name = "issuer_category")
  private String issuerCategory;

  @Enumerated(EnumType.STRING)
  @Column(name = "category_source", nullable = false)
  private CategorySource categorySource = CategorySource.UNCATEGORIZED;

  @Column(nullable = false)
  private boolean pending = false;

  @Column(nullable = false)
  private String fingerprint;

  @Column(nullable = false)
  private String notes = "";

  /** Null for rows entered by hand; set when a row came from an uploaded statement. */
  @Column(name = "import_batch_id")
  private Long importBatchId;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt = OffsetDateTime.now();

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt = OffsetDateTime.now();

  protected Transaction() {}

  public Transaction(
      Long accountId, LocalDate postedDate, BigDecimal amount, String rawDescription) {
    this.accountId = accountId;
    this.postedDate = postedDate;
    this.amount = amount;
    this.rawDescription = rawDescription;
    this.fingerprint = fingerprintOf(accountId, postedDate, amount, rawDescription);
  }

  /**
   * Stable identity for a statement line, so re-importing an overlapping date range is a no-op.
   *
   * <p>Deliberately built from the four fields every institution supplies and none of them mutate
   * after posting. Bank of America also gives a genuine unique reference number — when one is
   * available {@link #useExternalReference} replaces this hash with it, since a bank-assigned id
   * beats anything computed.
   */
  public static String fingerprintOf(
      Long accountId, LocalDate postedDate, BigDecimal amount, String rawDescription) {
    String canonical =
        accountId
            + "|"
            + postedDate
            + "|"
            + amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
            + "|"
            + rawDescription.trim().toUpperCase().replaceAll("\\s+", " ");
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required by the JDK spec", e);
    }
  }

  /** Prefers a bank-assigned reference (Bank of America) over the computed hash. */
  public void useExternalReference(String reference) {
    if (reference != null && !reference.isBlank()) {
      this.fingerprint = reference.trim();
    }
  }

  public Long getId() {
    return id;
  }

  public Long getAccountId() {
    return accountId;
  }

  public LocalDate getPostedDate() {
    return postedDate;
  }

  public LocalDate getTransactionDate() {
    return transactionDate;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getRawDescription() {
    return rawDescription;
  }

  public String getMerchant() {
    return merchant;
  }

  public TxnType getTxnType() {
    return txnType;
  }

  public String getCategory() {
    return category;
  }

  public String getIssuerCategory() {
    return issuerCategory;
  }

  public CategorySource getCategorySource() {
    return categorySource;
  }

  public boolean isPending() {
    return pending;
  }

  public String getFingerprint() {
    return fingerprint;
  }

  public String getNotes() {
    return notes;
  }

  public Long getImportBatchId() {
    return importBatchId;
  }

  /** Links this row to the upload it arrived in, so the whole batch can be undone together. */
  public void attachToBatch(Long batchId) {
    this.importBatchId = batchId;
  }

  /** Fields an importer owns. Never touches category — see {@link #categorizeByRule}. */
  public void applyImportedDetail(
      LocalDate transactionDate,
      String merchant,
      String issuerCategory,
      TxnType txnType,
      boolean pending) {
    this.transactionDate = transactionDate;
    this.merchant = merchant;
    this.issuerCategory = issuerCategory;
    this.txnType = txnType == null ? TxnType.SPEND : txnType;
    this.pending = pending;
    this.updatedAt = OffsetDateTime.now();
  }

  /**
   * Applies an automatic categorization, but only where a person has not already decided. This is
   * the guard that keeps hand-corrections from being reverted by the next import.
   *
   * @return true if the category was actually changed
   */
  public boolean categorizeByRule(String category) {
    if (!categorySource.isOverwritable()) {
      return false;
    }
    this.category = category;
    this.categorySource = category == null ? CategorySource.UNCATEGORIZED : CategorySource.RULE;
    this.updatedAt = OffsetDateTime.now();
    return true;
  }

  /** A human decision. Sticks until another human changes it. */
  public void categorizeManually(String category) {
    this.category = category;
    this.categorySource =
        category == null || category.isBlank()
            ? CategorySource.UNCATEGORIZED
            : CategorySource.MANUAL;
    this.updatedAt = OffsetDateTime.now();
  }

  /** Reclassifies what kind of movement this is — the transfer/payment correction. */
  public void reclassify(TxnType txnType) {
    this.txnType = txnType;
    this.updatedAt = OffsetDateTime.now();
  }

  public void update(
      LocalDate postedDate,
      LocalDate transactionDate,
      BigDecimal amount,
      String rawDescription,
      String merchant,
      TxnType txnType,
      String notes) {
    this.postedDate = postedDate;
    this.transactionDate = transactionDate;
    this.amount = amount;
    this.rawDescription = rawDescription;
    this.merchant = merchant;
    this.txnType = txnType == null ? TxnType.SPEND : txnType;
    this.notes = notes == null ? "" : notes;
    this.updatedAt = OffsetDateTime.now();
  }
}
