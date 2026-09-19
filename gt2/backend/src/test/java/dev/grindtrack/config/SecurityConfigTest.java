package dev.grindtrack.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.grindtrack.auth.api.AuthController;
import dev.grindtrack.auth.domain.Role;
import dev.grindtrack.auth.domain.User;
import dev.grindtrack.auth.security.JwtAuthFilter;
import dev.grindtrack.auth.service.AuthService;
import dev.grindtrack.auth.service.JwtService;
import dev.grindtrack.auth.service.LoginRateLimiter;
import dev.grindtrack.auth.service.TrustedDeviceService;
import jakarta.servlet.http.Cookie;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * The rule that keeps a partner out of everything but their own few paths, proved against every
 * endpoint the app has rather than the ones somebody remembered to list.
 *
 * <p>Authorisation is by URL and runs before dispatch, so the slice needs only one controller to be
 * real: for every other mapping the answer to a partner is 403 whether or not a handler is loaded,
 * and to the owner it is anything but 403. The mappings come from scanning the classpath for
 * {@code @RestController}s, so an endpoint added next month is covered the day it is written, and
 * the only way to open it to a partner is to name it in {@code SecurityConfig.SHARED_PATHS}.
 */
@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtService.class})
@EnableConfigurationProperties(AppProperties.class)
class SecurityConfigTest {

  private static final AntPathMatcher PATHS = new AntPathMatcher();

  @Autowired private MockMvc mvc;
  @Autowired private JwtService jwt;

  @MockitoBean private AuthService authService;
  @MockitoBean private LoginRateLimiter rateLimiter;
  @MockitoBean private TrustedDeviceService trustedDevices;

  /** One HTTP method and one concrete path, as a controller maps it. */
  record Mapping(HttpMethod method, String path, String handler) {
    boolean matchesAny(String[] patterns) {
      return Arrays.stream(patterns).anyMatch(p -> PATHS.match(p, path));
    }

    @Override
    public String toString() {
      return method + " " + path + " (" + handler + ")";
    }
  }

  private Cookie cookieFor(Role role) {
    User user = new User(role == Role.OWNER ? "casey" : "wife", "hash", "SECRET", role);
    ReflectionTestUtils.setField(user, "id", role == Role.OWNER ? 1L : 2L);
    return new Cookie(JwtAuthFilter.ACCESS_COOKIE, jwt.issueAccessToken(user));
  }

  private int statusFor(Mapping m, Cookie cookie) throws Exception {
    MockHttpServletRequestBuilder req = request(m.method(), m.path());
    if (cookie != null) {
      req.cookie(cookie);
    }
    return mvc.perform(req).andReturn().getResponse().getStatus();
  }

  @Test
  void thereIsSomethingToWalk() {
    List<Mapping> all = mappings();
    assertThat(all).hasSizeGreaterThan(50);
    assertThat(all).anyMatch(m -> m.path().equals("/api/auth/users"));
    assertThat(all).anyMatch(m -> m.path().startsWith("/api/recovery/"));
    assertThat(all).anyMatch(m -> m.path().startsWith("/api/push/"));
  }

  @Test
  void aPartnerReachesTheSharedPathsAndIsRefusedEverywhereElse() throws Exception {
    Cookie partner = cookieFor(Role.PARTNER);
    List<String> wrong = new ArrayList<>();
    for (Mapping m : mappings()) {
      if (m.matchesAny(SecurityConfig.PUBLIC_PATHS)) {
        continue;
      }
      int status = statusFor(m, partner);
      boolean shared = m.matchesAny(SecurityConfig.SHARED_PATHS);
      // A shared path answers as itself: 200 from the one real controller, 404 from the slice for
      // the rest. Anything else is 403 and only 403 — never 401, which would mean the cookie was
      // not read, and never a handler's own answer, which would mean the partner got in.
      boolean ok = shared ? status != 403 && status != 401 : status == 403;
      if (!ok) {
        wrong.add(m + " -> " + status);
      }
    }
    assertThat(wrong).as("partner authorisation, per mapping").isEmpty();
  }

  /**
   * Never a 403 anywhere, and never a 401 from the filter chain. A public endpoint may answer its
   * own 401 — refresh with no refresh cookie does — and that is the handler speaking, not the role.
   */
  @Test
  void theOwnerIsNeverRefusedByRole() throws Exception {
    Cookie owner = cookieFor(Role.OWNER);
    List<String> refused = new ArrayList<>();
    for (Mapping m : mappings()) {
      int status = statusFor(m, owner);
      boolean isPublic = m.matchesAny(SecurityConfig.PUBLIC_PATHS);
      if (status == 403 || (status == 401 && !isPublic)) {
        refused.add(m + " -> " + status);
      }
    }
    assertThat(refused).as("owner authorisation, per mapping").isEmpty();
  }

  /**
   * Public paths may answer 401 on their own (refresh with no cookie does); the rest may not answer
   * anything else.
   */
  @Test
  void withoutACookieEverythingButThePublicPathsIsA401() throws Exception {
    List<String> wrong = new ArrayList<>();
    for (Mapping m : mappings()) {
      if (m.matchesAny(SecurityConfig.PUBLIC_PATHS)) {
        continue;
      }
      int status = statusFor(m, null);
      if (status != 401) {
        wrong.add(m + " -> " + status);
      }
    }
    assertThat(wrong).as("anonymous, per mapping").isEmpty();
  }

  @Test
  void aPublicPathAnswersWithoutACookie() throws Exception {
    mvc.perform(get("/api/auth/device")).andExpect(status().isOk());
  }

  /** Every path a {@code @RestController} in the app maps, path variables filled with {@code 1}. */
  static List<Mapping> mappings() {
    var scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
    List<Mapping> out = new ArrayList<>();
    for (BeanDefinition bd : scanner.findCandidateComponents("dev.grindtrack")) {
      Class<?> type;
      try {
        type = Class.forName(bd.getBeanClassName());
      } catch (ClassNotFoundException e) {
        throw new IllegalStateException(e);
      }
      RequestMapping onClass =
          AnnotatedElementUtils.findMergedAnnotation(type, RequestMapping.class);
      String[] prefixes =
          onClass == null || onClass.path().length == 0 ? new String[] {""} : onClass.path();
      for (Method method : type.getMethods()) {
        RequestMapping onMethod =
            AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
        if (onMethod == null) {
          continue;
        }
        String[] subs = onMethod.path().length == 0 ? new String[] {""} : onMethod.path();
        RequestMethod[] verbs =
            onMethod.method().length == 0
                ? new RequestMethod[] {RequestMethod.GET}
                : onMethod.method();
        for (String prefix : prefixes) {
          for (String sub : subs) {
            String path = (prefix + sub).replaceAll("\\{[^}]+}", "1");
            for (RequestMethod verb : verbs) {
              out.add(
                  new Mapping(
                      HttpMethod.valueOf(verb.name()),
                      path,
                      type.getSimpleName() + "." + method.getName()));
            }
          }
        }
      }
    }
    return out;
  }
}
