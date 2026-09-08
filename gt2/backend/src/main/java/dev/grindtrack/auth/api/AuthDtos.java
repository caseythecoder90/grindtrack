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

  /**
   * Username and password are always required: this app has no password-only path, by design.
   *
   * <p>{@code otp} is not {@code @NotBlank} because a browser that has already proved the second
   * factor may omit it -- the check lives in {@code AuthService.authenticate}, which needs the
   * device cookie to decide and so cannot be expressed as a field constraint. A blank code on an
   * untrusted browser still fails, it just fails as bad credentials rather than as a 400.
   *
   * <p>{@code trustDevice} asks the server to remember this browser. Absent means false, so an old
   * client that does not send it keeps the old behaviour.
   */
  public record LoginRequest(
      @NotBlank String username, @NotBlank String password, String otp, boolean trustDevice) {}

  /**
   * What an auth endpoint answers with.
   *
   * <p>Login and refresh return one of three bodies depending on the status, which is why these
   * methods alone still hand back a {@code ResponseEntity} — they set cookies, and the status is
   * part of the contract rather than an error. A sealed interface is what lets them say {@code
   * ResponseEntity<AuthResponse>} instead of {@code ResponseEntity<?>}: the set of possible bodies
   * is closed and the compiler knows it.
   */
  public sealed interface AuthResponse
      permits SessionResponse, AuthError, LogoutResponse, DeviceTrust {}

  /**
   * The body of a successful login or refresh.
   *
   * <p>Deliberately just the username. The tokens are set as HttpOnly cookies and never appear in a
   * response body, which is the whole point of the cookie scheme — a body that carried them would
   * be readable by any script on the page.
   */
  public record SessionResponse(String username) implements AuthResponse {}

  /** {@code {"error": "..."}} — the same shape {@code ApiExceptionHandler} produces. */
  public record AuthError(String error) implements AuthResponse {}

  /** {@code {"status": "logged out"}} — an acknowledgement, not a session. */
  public record LogoutResponse(String status) implements AuthResponse {}

  /**
   * Whether this browser can skip the authenticator code, and how many devices are remembered.
   *
   * <p>Answered before anyone has signed in, which is safe: it says nothing an attacker holding the
   * device cookie does not already have, and it still takes the password to get in. {@code count}
   * is only meaningful once signed in — it is zero to anyone else.
   */
  public record DeviceTrust(boolean trusted, int count) implements AuthResponse {}
}
