package dev.grindtrack.web;

/**
 * A feature that is deliberately switched off — configuration absent, not broken. Maps to 503,
 * distinct from {@link UpstreamException}'s 502: a 502 says "try again", this says "an operator has
 * to change something first".
 */
public class ServiceOffException extends RuntimeException {
  public ServiceOffException(String message) {
    super(message);
  }
}
