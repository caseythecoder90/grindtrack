package dev.grindtrack.finance.service.parse;

import dev.grindtrack.web.BadRequestException;

/**
 * A file that could not be read. The message is shown to the user, so it explains what to do.
 *
 * <p>A {@link BadRequestException} because that is exactly what an unreadable upload is: the
 * request's shape is wrong, and the answer is a 400 with a sentence the uploader can act on. It was
 * a bare {@code RuntimeException} with an {@code @ExceptionHandler} of its own bolted to the import
 * controller — the seventh copy of the per-controller handler the shared advice exists to replace.
 */
public class StatementParseException extends BadRequestException {

  public StatementParseException(String message) {
    super(message);
  }
}
