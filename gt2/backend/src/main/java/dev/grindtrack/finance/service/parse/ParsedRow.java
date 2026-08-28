package dev.grindtrack.finance.service.parse;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One statement line, normalized.
 *
 * <p>By the time a row reaches here the three sign conventions have been reconciled: {@code amount}
 * is negative when money left the account, whatever the source did.
 *
 * @param externalReference a bank-assigned unique id when the format supplies one. Only Bank of
 *     America does; it beats a computed hash, so it becomes the fingerprint directly.
 */
public record ParsedRow(
    LocalDate postedDate,
    LocalDate transactionDate,
    BigDecimal amount,
    String description,
    String issuerCategory,
    String externalReference) {

  public static ParsedRow of(LocalDate posted, BigDecimal amount, String description) {
    return new ParsedRow(posted, null, amount, description, null, null);
  }

  public ParsedRow withTransactionDate(LocalDate date) {
    return new ParsedRow(postedDate, date, amount, description, issuerCategory, externalReference);
  }

  public ParsedRow withIssuerCategory(String category) {
    return new ParsedRow(
        postedDate,
        transactionDate,
        amount,
        description,
        category == null || category.isBlank() ? null : category.trim(),
        externalReference);
  }

  public ParsedRow withExternalReference(String reference) {
    return new ParsedRow(
        postedDate,
        transactionDate,
        amount,
        description,
        issuerCategory,
        reference == null || reference.isBlank() ? null : reference.trim());
  }
}
