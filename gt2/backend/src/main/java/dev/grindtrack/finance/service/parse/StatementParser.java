package dev.grindtrack.finance.service.parse;

import java.util.List;

/**
 * Reads one bank's export format.
 *
 * <p>Implementations declare which header they recognize rather than being chosen by hand, so the
 * upload screen never asks "which bank is this file from" — a question the file already answers,
 * and one that is easy to get wrong when three of the accounts are at the same bank.
 */
public interface StatementParser {

  StatementFormat format();

  /**
   * @param header the first non-blank row of the file, trimmed
   * @return true if this parser owns the file
   */
  boolean canParse(List<String> header);

  /**
   * @param rows every row including the header
   * @throws StatementParseException if the file matched the header but the body is unusable
   */
  ParsedStatement parse(List<List<String>> rows);
}
