package dev.grindtrack.auth.security;

import dev.grindtrack.auth.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads the access-token cookie on every request and populates the SecurityContext.
 *
 * <p>The authority granted is the account's role, {@code ROLE_OWNER} or {@code ROLE_PARTNER},
 * straight from the token's claim: the role is fixed for the life of an account, so there is
 * nothing to look up. The principal is the {@link SignedIn} record, which is how a controller
 * learns whose request this is.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

  public static final String ACCESS_COOKIE = "gt_access";

  private final JwtService jwtService;

  public JwtAuthFilter(JwtService jwtService) {
    this.jwtService = jwtService;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String accessToken = Cookies.value(request, ACCESS_COOKIE);
    if (accessToken != null) {
      jwtService.validate(accessToken).ifPresent(JwtAuthFilter::setAuthenticatedUser);
    }
    chain.doFilter(request, response);
  }

  private static void setAuthenticatedUser(SignedIn who) {
    var auth =
        new UsernamePasswordAuthenticationToken(
            who, null, List.of(new SimpleGrantedAuthority(who.authority())));
    SecurityContextHolder.getContext().setAuthentication(auth);
  }
}
