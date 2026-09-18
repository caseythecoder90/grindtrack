package dev.grindtrack.auth.api;

import dev.grindtrack.auth.api.AuthDtos.CreateUserRequest;
import dev.grindtrack.auth.api.AuthDtos.LogoutAllResponse;
import dev.grindtrack.auth.api.AuthDtos.PartnerCreated;
import dev.grindtrack.auth.api.AuthDtos.PasswordRequest;
import dev.grindtrack.auth.api.AuthDtos.UserSummary;
import dev.grindtrack.auth.domain.User;
import dev.grindtrack.auth.service.AuthService;
import dev.grindtrack.auth.service.TrustedDeviceService;
import dev.grindtrack.auth.service.UserService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The owner's view of who can sign in.
 *
 * <p>Under {@code /api/auth} but not in the shared list, so a partner cannot reach it: the security
 * config names the three session endpoints a partner needs one by one rather than opening the
 * prefix. There is no delete. A partner's messages, once the chat exists, are a record that should
 * outlive the account's access; ending their sessions is the way to shut a partner out, and it is
 * reversible.
 */
@RestController
@RequestMapping("/api/auth/users")
public class UsersController {

  private final UserService userService;
  private final AuthService authService;
  private final TrustedDeviceService trustedDevices;

  public UsersController(
      UserService userService, AuthService authService, TrustedDeviceService trustedDevices) {
    this.userService = userService;
    this.authService = authService;
    this.trustedDevices = trustedDevices;
  }

  @GetMapping
  public List<UserSummary> list() {
    return userService.list().stream().map(UsersController::summary).toList();
  }

  /** The secret in the answer is shown once and never sent again; see {@link PartnerCreated}. */
  @PostMapping
  public PartnerCreated create(@RequestBody CreateUserRequest body) {
    UserService.Created created = userService.createPartner(body.username(), body.password());
    User user = created.user();
    return new PartnerCreated(
        user.getId(),
        user.getUsername(),
        user.getRole(),
        created.totpSecret(),
        created.otpauthUri());
  }

  @PutMapping("/{id}/password")
  public UserSummary resetPassword(@PathVariable long id, @RequestBody PasswordRequest body) {
    userService.resetPassword(id, body.password());
    return summary(userService.partner(id));
  }

  /**
   * Sign a partner out everywhere and forget their devices, so the next sign-in wants the password
   * and the code again. The owner has two buttons for the same two things ({@code
   * /api/auth/logout-all} and {@code /devices/forget}); for a partner they are one.
   */
  @PostMapping("/{id}/logout-all")
  public LogoutAllResponse logoutAll(@PathVariable long id) {
    User user = userService.partner(id);
    int ended = authService.revokeAllForUser(user.getId());
    trustedDevices.forgetAll(user.getId());
    return new LogoutAllResponse("logged out everywhere", ended);
  }

  private static UserSummary summary(User user) {
    return new UserSummary(
        user.getId(),
        user.getUsername(),
        user.getRole(),
        user.getCreatedAt() == null ? null : user.getCreatedAt().toString());
  }
}
