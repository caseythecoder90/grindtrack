package dev.grindtrack.auth.api;

import dev.grindtrack.auth.api.AuthDtos.AuthError;
import dev.grindtrack.auth.api.AuthDtos.AuthResponse;
import dev.grindtrack.auth.api.AuthDtos.DeviceTrust;
import dev.grindtrack.auth.api.AuthDtos.LoginRequest;
import dev.grindtrack.auth.api.AuthDtos.LogoutResponse;
import dev.grindtrack.auth.api.AuthDtos.SessionResponse;
import dev.grindtrack.auth.domain.User;
import dev.grindtrack.auth.security.Cookies;
import dev.grindtrack.auth.security.JwtAuthFilter;
import dev.grindtrack.auth.service.AuthService;
import dev.grindtrack.auth.service.JwtService;
import dev.grindtrack.auth.service.LoginRateLimiter;
import dev.grindtrack.auth.service.TrustedDeviceService;
import dev.grindtrack.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private static final String REFRESH_COOKIE = "gt_refresh";
  private static final String REFRESH_PATH = "/api/auth";

  /** Scoped to /api/auth like the refresh cookie: nothing outside signing in has any use for it. */
  private static final String DEVICE_COOKIE = "gt_device";

  private final AuthService authService;
  private final JwtService jwtService;
  private final LoginRateLimiter rateLimiter;
  private final TrustedDeviceService trustedDevices;
  private final AppProperties props;

  public AuthController(
      AuthService authService,
      JwtService jwtService,
      LoginRateLimiter rateLimiter,
      TrustedDeviceService trustedDevices,
      AppProperties props) {
    this.authService = authService;
    this.jwtService = jwtService;
    this.rateLimiter = rateLimiter;
    this.trustedDevices = trustedDevices;
    this.props = props;
  }

  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(
      @RequestBody LoginRequest body, HttpServletRequest request) {
    if (!rateLimiter.allow(clientIp(request))) {
      return ResponseEntity.status(429).body(new AuthError("Too many attempts. Wait 5 minutes."));
    }
    String deviceToken = Cookies.value(request, DEVICE_COOKIE);
    Long trustedFor = trustedDevices.trustedUserFor(deviceToken).orElse(null);
    return authService
        .authenticate(body.username(), body.password(), body.otp(), trustedFor)
        .map(user -> signedIn(user, body.trustDevice(), deviceToken, trustedFor))
        .orElseGet(() -> unauthorized("Invalid username, password, or code."));
  }

  /**
   * The success path, plus whatever this sign-in changed about the device's trust.
   *
   * <p>Three cases, and only the first sets a new device cookie: a browser asking to be remembered,
   * a browser that already was (slide its expiry), and one that is neither.
   */
  private ResponseEntity<AuthResponse> signedIn(
      User user, boolean trustDevice, String deviceToken, Long trustedFor) {
    boolean alreadyTrusted = trustedFor != null && trustedFor.equals(user.getId());
    if (alreadyTrusted) {
      trustedDevices.touch(deviceToken);
    }
    ResponseEntity.BodyBuilder ok = ResponseEntity.ok();
    // Asking to be trusted when this browser already is would leave the old row orphaned and
    // un-revokable, so the existing trust is renewed instead.
    if (trustDevice && !alreadyTrusted) {
      ok.header(HttpHeaders.SET_COOKIE, deviceCookie(trustedDevices.trust(user)).toString());
    }
    String accessToken = jwtService.issueAccessToken(user.getUsername());
    return ok.header(HttpHeaders.SET_COOKIE, accessCookie(accessToken).toString())
        .header(
            HttpHeaders.SET_COOKIE, refreshCookie(authService.issueRefreshToken(user)).toString())
        .body(new SessionResponse(user.getUsername()));
  }

  /**
   * Does this browser still need an authenticator code?
   *
   * <p>Called by the login form before anything is typed, so it can drop the field rather than ask
   * for something it does not need. Deliberately says nothing about who: an answer of "yes" plus
   * the wrong password is still just a wrong password.
   */
  @GetMapping("/device")
  public DeviceTrust device(HttpServletRequest request, Principal principal) {
    boolean trusted =
        trustedDevices.trustedUserFor(Cookies.value(request, DEVICE_COOKIE)).isPresent();
    return new DeviceTrust(trusted, 0);
  }

  /**
   * Forget every remembered device, this one included.
   *
   * <p>The answer to a lost phone. Authenticated, because it is a change to the account rather than
   * to this browser, and the next sign-in anywhere will want the authenticator again.
   */
  @PostMapping("/devices/forget")
  public ResponseEntity<AuthResponse> forgetDevices(Principal principal) {
    int forgotten =
        authService
            .findByUsername(principal.getName())
            .map(u -> trustedDevices.forgetAll(u.getId()))
            .orElse(0);
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, expiredCookie(DEVICE_COOKIE, REFRESH_PATH).toString())
        .body(new DeviceTrust(false, forgotten));
  }

  @PostMapping("/refresh")
  public ResponseEntity<AuthResponse> refresh(HttpServletRequest request) {
    String presented = Cookies.value(request, REFRESH_COOKIE);
    if (presented == null) {
      return unauthorized("No refresh token.");
    }
    return authService
        .renew(presented)
        .map(renewed -> sessionResponse(renewed.user(), renewed.sessionToken()))
        .orElseGet(() -> unauthorized("Refresh token invalid."));
  }

  @PostMapping("/logout")
  public ResponseEntity<AuthResponse> logout(HttpServletRequest request) {
    String presented = Cookies.value(request, REFRESH_COOKIE);
    if (presented != null) {
      authService.revoke(presented);
    }
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, expiredCookie(JwtAuthFilter.ACCESS_COOKIE, "/").toString())
        .header(HttpHeaders.SET_COOKIE, expiredCookie(REFRESH_COOKIE, REFRESH_PATH).toString())
        .body(new LogoutResponse("logged out"));
  }

  @GetMapping("/me")
  public SessionResponse me(Principal principal) {
    return new SessionResponse(principal.getName());
  }

  /** 200 with fresh access + refresh cookies — the shared success shape of login and refresh. */
  private ResponseEntity<AuthResponse> sessionResponse(User user, String refreshToken) {
    String accessToken = jwtService.issueAccessToken(user.getUsername());
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, accessCookie(accessToken).toString())
        .header(HttpHeaders.SET_COOKIE, refreshCookie(refreshToken).toString())
        .body(new SessionResponse(user.getUsername()));
  }

  private static ResponseEntity<AuthResponse> unauthorized(String message) {
    return ResponseEntity.status(401).body(new AuthError(message));
  }

  private ResponseCookie accessCookie(String token) {
    return authCookie(
        JwtAuthFilter.ACCESS_COOKIE, token, "/", Duration.ofMinutes(props.accessTokenMinutes()));
  }

  private ResponseCookie deviceCookie(String token) {
    return authCookie(
        DEVICE_COOKIE, token, REFRESH_PATH, Duration.ofDays(props.trustedDeviceDays()));
  }

  private ResponseCookie refreshCookie(String token) {
    return authCookie(
        REFRESH_COOKIE, token, REFRESH_PATH, Duration.ofDays(props.refreshTokenDays()));
  }

  private ResponseCookie expiredCookie(String name, String path) {
    return authCookie(name, "", path, Duration.ZERO);
  }

  private ResponseCookie authCookie(String name, String value, String path, Duration maxAge) {
    return ResponseCookie.from(name, value)
        .httpOnly(true)
        .secure(props.cookieSecure())
        .sameSite("Strict")
        .path(path)
        .maxAge(maxAge)
        .build();
  }

  private static String clientIp(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    return forwarded != null ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
  }
}
