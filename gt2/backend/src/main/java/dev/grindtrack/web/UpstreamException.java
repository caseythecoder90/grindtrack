package dev.grindtrack.web;

/**
 * A dependency this app calls over the network failed — not the caller's fault and not a bug here,
 * so neither a 400 nor a 500. Maps to 502 in {@link ApiExceptionHandler}.
 *
 * <p>Exists for the assistant's model calls, which are the app's first outbound dependency. The
 * message is shown to the user, so it should say what to do ("try again"), not dump a stack.
 */
public class UpstreamException extends RuntimeException {
  public UpstreamException(String message) {
    super(message);
  }

  public UpstreamException(String message, Throwable cause) {
    super(message, cause);
  }
}
