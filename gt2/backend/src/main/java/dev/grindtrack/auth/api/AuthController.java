package dev.grindtrack.auth.api;

import dev.grindtrack.auth.domain.User;
import dev.grindtrack.auth.security.Cookies;
import dev.grindtrack.auth.security.JwtAuthFilter;
import dev.grindtrack.auth.service.AuthService;
import dev.grindtrack.auth.service.JwtService;
import dev.grindtrack.auth.service.LoginRateLimiter;
import dev.grindtrack.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import java.security.Principal;
import java.time.Duration;
import java.util.Map;
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

  private final AuthService authService;
  private final JwtService jwtService;
  private final LoginRateLimiter rateLimiter;
  private final AppProperties props;

  public AuthController(
      AuthService authService,
      JwtService jwtService,
      LoginRateLimiter rateLimiter,
      AppProperties props) {
    this.authService = authService;
    this.jwtService = jwtService;
    this.rateLimiter = rateLimiter;
    this.props = props;
  }

  @PostMapping("/login")
  public ResponseEntity<?> login(@RequestBody LoginRequest body, HttpServletRequest request) {
    if (!rateLimiter.allow(clientIp(request))) {
      return ResponseEntity.status(429).body(Map.of("error", "Too many attempts. Wait 5 minutes."));
    }
    return authService
        .authenticate(body.username(), body.password(), body.otp())
        .map(user -> sessionResponse(user, authService.issueRefreshToken(user)))
        .orElseGet(() -> unauthorized("Invalid username, password, or code."));
  }

  @PostMapping("/refresh")
  public ResponseEntity<?> refresh(HttpServletRequest request) {
    String presented = Cookies.value(request, REFRESH_COOKIE);
    if (presented == null) {
      return unauthorized("No refresh token.");
    }
    return authService
        .rotate(presented)
        .map(rotated -> sessionResponse(rotated.user(), rotated.newRefreshToken()))
        .orElseGet(() -> unauthorized("Refresh token invalid."));
  }

  @PostMapping("/logout")
  public ResponseEntity<?> logout(HttpServletRequest request) {
    String presented = Cookies.value(request, REFRESH_COOKIE);
    if (presented != null) {
      authService.revoke(presented);
    }
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, expiredCookie(JwtAuthFilter.ACCESS_COOKIE, "/").toString())
        .header(HttpHeaders.SET_COOKIE, expiredCookie(REFRESH_COOKIE, REFRESH_PATH).toString())
        .body(Map.of("status", "logged out"));
  }

  @GetMapping("/me")
  public Map<String, String> me(Principal principal) {
    return Map.of("username", principal.getName());
  }

  /** 200 with fresh access + refresh cookies — the shared success shape of login and refresh. */
  private ResponseEntity<Object> sessionResponse(User user, String refreshToken) {
    String accessToken = jwtService.issueAccessToken(user.getUsername());
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, accessCookie(accessToken).toString())
        .header(HttpHeaders.SET_COOKIE, refreshCookie(refreshToken).toString())
        .body(Map.of("username", user.getUsername()));
  }

  private static ResponseEntity<Object> unauthorized(String message) {
    return ResponseEntity.status(401).body(Map.of("error", message));
  }

  private ResponseCookie accessCookie(String token) {
    return authCookie(
        JwtAuthFilter.ACCESS_COOKIE, token, "/", Duration.ofMinutes(props.accessTokenMinutes()));
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

  public record LoginRequest(
      @NotBlank String username, @NotBlank String password, @NotBlank String otp) {}
}
