package dev.grindtrack.web;

/**
 * A malformed request. The message reaches the user, so it says what to fix.
 *
 * <p>One type for the whole app. Six controllers previously declared their own private {@code
 * BadRequest} class with an identical {@code @ExceptionHandler} beside it — about fifty lines of
 * duplication that could not be removed precisely because each type was private to its own file.
 *
 * <p>This is for <em>shape</em> failures only: an unparseable date, an enum constant that does not
 * exist, a string over its column length. A rule about the domain — a category that already has a
 * budget line, a moment dated in the future — belongs in the service and is thrown from there. See
 * {@code docs/architecture-conventions.md}.
 */
public class BadRequestException extends RuntimeException {

  public BadRequestException(String message) {
    super(message);
  }
}
