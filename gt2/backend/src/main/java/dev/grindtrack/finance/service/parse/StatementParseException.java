package dev.grindtrack.finance.service.parse;

/** A file that could not be read. The message is shown to the user, so it explains what to do. */
public class StatementParseException extends RuntimeException {

  public StatementParseException(String message) {
    super(message);
  }
}
