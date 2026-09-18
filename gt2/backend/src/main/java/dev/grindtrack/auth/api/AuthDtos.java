package dev.grindtrack.auth.api;

import dev.grindtrack.auth.domain.Role;
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
      permits SessionResponse, AuthError, LogoutResponse, LogoutAllResponse, DeviceTrust {}

  /**
   * The body of a successful login or refresh, and of {@code /me}.
   *
   * <p>The username and the role, nothing else. The tokens are set as HttpOnly cookies and never
   * appear in a response body, which is the whole point of the cookie scheme — a body that carried
   * them would be readable by any script on the page. The role is what the page renders by: the
   * owner's tabs, or a partner's one screen.
   */
  public record SessionResponse(String username, Role role) implements AuthResponse {}

  /** {@code {"error": "..."}} — the same shape {@code ApiExceptionHandler} produces. */
  public record AuthError(String error) implements AuthResponse {}

  /** {@code {"status": "logged out"}} — an acknowledgement, not a session. */
  public record LogoutResponse(String status) implements AuthResponse {}

  /**
   * {@code {"status": "logged out everywhere", "sessionsEnded": n}} -- how many live sessions the
   * click ended, this one included, because the value of the button is knowing it did something.
   */
  public record LogoutAllResponse(String status, int sessionsEnded) implements AuthResponse {}

  /**
   * Whether this browser can skip the authenticator code, and how many devices are remembered.
   *
   * <p>Answered before anyone has signed in, which is safe: it says nothing an attacker holding the
   * device cookie does not already have, and it still takes the password to get in. {@code count}
   * is only meaningful once signed in — it is zero to anyone else.
   */
  public record DeviceTrust(boolean trusted, int count) implements AuthResponse {}

  // ---- Accounts: the owner making a partner's, and minding it ----

  /** A partner to create. The rules on both fields are in {@code UserService}. */
  public record CreateUserRequest(@NotBlank String username, @NotBlank String password) {}

  /** A new password for a partner who has forgotten theirs. */
  public record PasswordRequest(@NotBlank String password) {}

  /** One account as the list shows it. Never the hash, never the secret. */
  public record UserSummary(long id, String username, Role role, String createdAt) {}

  /**
   * A partner just created, with the authenticator secret — the one time it is ever sent.
   *
   * <p>The owner reads it off the screen into the partner's authenticator app, the way the owner's
   * own secret came off the bootstrap log once. It is not stored anywhere it can be read back; a
   * lost authenticator means a new account.
   */
  public record PartnerCreated(
      long id, String username, Role role, String totpSecret, String otpauthUri) {}
}
