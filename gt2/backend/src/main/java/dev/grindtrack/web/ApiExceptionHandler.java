package dev.grindtrack.web;

import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * The one place an exception becomes a status code.
 *
 * <p>Replaces six per-controller handlers that produced the same {@code {"error": "..."}} body.
 *
 * <p><strong>Named types only.</strong> There is deliberately no {@code @ExceptionHandler(
 * Exception.class)}: catching everything turns an unexpected failure into a tidy JSON 400 and
 * throws away the stack trace that would have told you what actually broke. Anything not listed
 * here stays a 500 and gets logged.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(BadRequestException.class)
  ResponseEntity<Map<String, String>> onBadRequest(BadRequestException e) {
    return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
  }

  /**
   * Services throw this for invariant failures — a duplicate budget category, an uncompilable
   * regex, a moment dated in the future — so that the rule holds however the call arrives rather
   * than only over HTTP.
   *
   * <p>The known cost: {@code IllegalArgumentException} is also thrown by plenty of JDK code, so a
   * genuine bug deep in a call stack can surface here as a clean 400 instead of a 500. Accepted at
   * this size because the alternative is a bespoke exception hierarchy threaded through every
   * service. Revisit if a 400 ever turns out to have been a null-shaped bug in disguise; the fix is
   * a ValidationException in a neutral package that services throw explicitly.
   */
  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<Map<String, String>> onInvalid(IllegalArgumentException e) {
    return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
  }

  @ExceptionHandler(NoSuchElementException.class)
  ResponseEntity<Map<String, String>> onNotFound(NoSuchElementException e) {
    return ResponseEntity.status(404).body(Map.of("error", "not found: " + e.getMessage()));
  }
}
