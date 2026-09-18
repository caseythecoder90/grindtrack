package dev.grindtrack.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.grindtrack.auth.domain.Role;
import dev.grindtrack.auth.service.JwtService;
import jakarta.servlet.http.Cookie;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** The cookie becomes a principal with exactly the role the token names, or nothing at all. */
class JwtAuthFilterTest {

  private JwtService jwt;
  private JwtAuthFilter filter;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.clearContext();
    jwt = mock(JwtService.class);
    filter = new JwtAuthFilter(jwt);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private void run(Cookie... cookies) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    if (cookies.length > 0) {
      request.setCookies(cookies);
    }
    filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
  }

  @Test
  void grantsTheRoleInTheTokenAndNothingElse() throws Exception {
    when(jwt.validate("good")).thenReturn(Optional.of(new SignedIn(2L, "wife", Role.PARTNER)));

    run(new Cookie(JwtAuthFilter.ACCESS_COOKIE, "good"));

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    assertThat(auth.getPrincipal()).isEqualTo(new SignedIn(2L, "wife", Role.PARTNER));
    assertThat(auth.getName()).isEqualTo("wife");
    assertThat(auth.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("ROLE_PARTNER");
    assertThat(SignedIn.of(auth)).isEqualTo(new SignedIn(2L, "wife", Role.PARTNER));
  }

  @Test
  void theOwnerGetsTheOwnersAuthority() throws Exception {
    when(jwt.validate("good")).thenReturn(Optional.of(new SignedIn(1L, "casey", Role.OWNER)));

    run(new Cookie(JwtAuthFilter.ACCESS_COOKIE, "good"));

    assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("ROLE_OWNER");
  }

  @Test
  void noCookieOrABadTokenLeavesTheContextEmpty() throws Exception {
    run();
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

    when(jwt.validate("bad")).thenReturn(Optional.empty());
    run(new Cookie(JwtAuthFilter.ACCESS_COOKIE, "bad"));
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  /** A principal that is not ours is a request that skipped the filter: a bug, not a 401. */
  @Test
  void somethingOtherThanOurPrincipalIsRefusedLoudly() {
    assertThatThrownBy(() -> SignedIn.of(() -> "casey")).isInstanceOf(IllegalStateException.class);
  }
}
