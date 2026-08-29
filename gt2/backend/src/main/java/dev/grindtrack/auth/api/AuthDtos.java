package dev.grindtrack.auth.api;

import jakarta.validation.constraints.NotBlank;

/**
 * Request/response shapes for the auth API.
 *
 * <p>Small, but it lives here for the same reason as every other {@code <Feature>Dtos}: a reader
 * looking for what {@code POST /api/auth/login} accepts should find it by opening the file named
 * after the shapes, not by scrolling to the bottom of the controller.
 */
public final class AuthDtos {

  private AuthDtos() {}

  /** All three are required together: this app has no password-only path, by design. */
  public record LoginRequest(
      @NotBlank String username, @NotBlank String password, @NotBlank String otp) {}

  /**
   * The body of a successful login or refresh.
   *
   * <p>Deliberately just the username. The tokens are set as HttpOnly cookies and never appear in a
   * response body, which is the whole point of the cookie scheme — a body that carried them would
   * be readable by any script on the page.
   */
  public record SessionResponse(String username) {}

  /** {@code {"error": "..."}} — the same shape {@code ApiExceptionHandler} produces. */
  public record AuthError(String error) {}

  /** {@code {"status": "logged out"}} — an acknowledgement, not a session. */
  public record LogoutResponse(String status) {}
}
