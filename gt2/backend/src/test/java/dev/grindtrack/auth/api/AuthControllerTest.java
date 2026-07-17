package dev.grindtrack.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.grindtrack.auth.domain.User;
import dev.grindtrack.auth.service.AuthService;
import dev.grindtrack.auth.service.AuthService.RotatedTokens;
import dev.grindtrack.auth.service.JwtService;
import dev.grindtrack.auth.service.LoginRateLimiter;
import dev.grindtrack.config.AppProperties;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

  @Mock private AuthService authService;
  @Mock private JwtService jwtService;
  @Mock private LoginRateLimiter rateLimiter;

  private AuthController controller;

  @BeforeEach
  void setUp() {
    AppProperties props = new AppProperties("secret", 15, 30, true, null, null);
    controller = new AuthController(authService, jwtService, rateLimiter, props);
  }

  private static MockHttpServletRequest requestFrom(String ip) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr(ip);
    return request;
  }

  private static List<String> setCookies(ResponseEntity<?> response) {
    return response.getHeaders().get(HttpHeaders.SET_COOKIE);
  }

  private User userNamed(String username) {
    User user = mock(User.class);
    when(user.getUsername()).thenReturn(username);
    return user;
  }

  @Test
  void loginSetsAccessAndRefreshCookiesOnSuccess() {
    when(rateLimiter.allow("1.2.3.4")).thenReturn(true);
    User user = userNamed("casey");
    when(authService.authenticate("casey", "pw", "123456")).thenReturn(Optional.of(user));
    when(authService.issueRefreshToken(user)).thenReturn("refresh-token");
    when(jwtService.issueAccessToken("casey")).thenReturn("access.jwt");

    ResponseEntity<?> response =
        controller.login(
            new AuthController.LoginRequest("casey", "pw", "123456"), requestFrom("1.2.3.4"));

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).isEqualTo(Map.of("username", "casey"));
    List<String> cookies = setCookies(response);
    assertThat(cookies).hasSize(2);
    assertThat(cookies.get(0))
        .contains("gt_access=access.jwt")
        .contains("Path=/")
        .contains("Max-Age=900") // 15 minutes
        .contains("HttpOnly")
        .contains("Secure")
        .contains("SameSite=Strict");
    assertThat(cookies.get(1))
        .contains("gt_refresh=refresh-token")
        .contains("Path=/api/auth")
        .contains("Max-Age=2592000") // 30 days
        .contains("HttpOnly")
        .contains("Secure")
        .contains("SameSite=Strict");
  }

  @Test
  void loginReturns401WithoutCookiesOnBadCredentials() {
    when(rateLimiter.allow("1.2.3.4")).thenReturn(true);
    when(authService.authenticate("casey", "bad", "000000")).thenReturn(Optional.empty());

    ResponseEntity<?> response =
        controller.login(
            new AuthController.LoginRequest("casey", "bad", "000000"), requestFrom("1.2.3.4"));

    assertThat(response.getStatusCode().value()).isEqualTo(401);
    assertThat(setCookies(response)).isNull();
    verifyNoInteractions(jwtService);
  }

  @Test
  void loginReturns429WhenRateLimitedWithoutTouchingAuth() {
    when(rateLimiter.allow("1.2.3.4")).thenReturn(false);

    ResponseEntity<?> response =
        controller.login(
            new AuthController.LoginRequest("casey", "pw", "123456"), requestFrom("1.2.3.4"));

    assertThat(response.getStatusCode().value()).isEqualTo(429);
    verifyNoInteractions(authService, jwtService);
  }

  @Test
  void loginRateLimitsByFirstForwardedForAddress() {
    MockHttpServletRequest request = requestFrom("10.0.0.1");
    request.addHeader("X-Forwarded-For", "9.9.9.9, 10.0.0.1");
    when(rateLimiter.allow("9.9.9.9")).thenReturn(false);

    ResponseEntity<?> response =
        controller.login(new AuthController.LoginRequest("casey", "pw", "123456"), request);

    assertThat(response.getStatusCode().value()).isEqualTo(429);
    verify(rateLimiter).allow("9.9.9.9");
  }

  @Test
  void refreshWithoutCookieReturns401() {
    ResponseEntity<?> response = controller.refresh(requestFrom("1.2.3.4"));

    assertThat(response.getStatusCode().value()).isEqualTo(401);
    verifyNoInteractions(authService);
  }

  @Test
  void refreshRotatesAndSetsFreshCookies() {
    MockHttpServletRequest request = requestFrom("1.2.3.4");
    request.setCookies(new Cookie("gt_refresh", "old-token"));
    User user = userNamed("casey");
    when(authService.rotate("old-token"))
        .thenReturn(Optional.of(new RotatedTokens(user, "new-token")));
    when(jwtService.issueAccessToken("casey")).thenReturn("fresh.jwt");

    ResponseEntity<?> response = controller.refresh(request);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).isEqualTo(Map.of("username", "casey"));
    List<String> cookies = setCookies(response);
    assertThat(cookies).hasSize(2);
    assertThat(cookies.get(0)).contains("gt_access=fresh.jwt");
    assertThat(cookies.get(1)).contains("gt_refresh=new-token").contains("Path=/api/auth");
  }

  @Test
  void refreshWithInvalidTokenReturns401WithoutCookies() {
    MockHttpServletRequest request = requestFrom("1.2.3.4");
    request.setCookies(new Cookie("gt_refresh", "bad-token"));
    when(authService.rotate("bad-token")).thenReturn(Optional.empty());

    ResponseEntity<?> response = controller.refresh(request);

    assertThat(response.getStatusCode().value()).isEqualTo(401);
    assertThat(setCookies(response)).isNull();
  }

  @Test
  void logoutRevokesPresentedTokenAndExpiresBothCookies() {
    MockHttpServletRequest request = requestFrom("1.2.3.4");
    request.setCookies(new Cookie("gt_refresh", "old-token"));

    ResponseEntity<?> response = controller.logout(request);

    verify(authService).revoke("old-token");
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    List<String> cookies = setCookies(response);
    assertThat(cookies).hasSize(2);
    assertThat(cookies.get(0)).startsWith("gt_access=;").contains("Max-Age=0").contains("Path=/");
    assertThat(cookies.get(1))
        .startsWith("gt_refresh=;")
        .contains("Max-Age=0")
        .contains("Path=/api/auth");
  }

  @Test
  void logoutWithoutCookieStillSucceeds() {
    ResponseEntity<?> response = controller.logout(requestFrom("1.2.3.4"));

    verify(authService, never()).revoke(any());
    assertThat(response.getStatusCode().value()).isEqualTo(200);
  }

  @Test
  void meEchoesThePrincipalName() {
    assertThat(controller.me(() -> "casey")).isEqualTo(Map.of("username", "casey"));
  }
}
