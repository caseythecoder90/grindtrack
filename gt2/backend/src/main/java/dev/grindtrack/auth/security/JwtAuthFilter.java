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

/** Reads the access-token cookie on every request and populates the SecurityContext. */
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

  private static void setAuthenticatedUser(String username) {
    var auth =
        new UsernamePasswordAuthenticationToken(
            username, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    SecurityContextHolder.getContext().setAuthentication(auth);
  }
}
