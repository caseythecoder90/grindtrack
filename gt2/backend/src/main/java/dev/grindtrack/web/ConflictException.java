package dev.grindtrack.web;

/**
 * A request that is well-formed but cannot be applied to the current state — a 409.
 *
 * <p>Only one endpoint needs it today: adding a transaction that is byte-for-byte one already on
 * file. It exists as a type rather than a hand-built {@code ResponseEntity.status(409)} so that the
 * endpoint's signature can be the response it actually returns, and so the message reaching the
 * user is produced by the same advice as every other error body.
 */
public class ConflictException extends RuntimeException {

  public ConflictException(String message) {
    super(message);
  }
}
