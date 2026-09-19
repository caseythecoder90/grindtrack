package dev.grindtrack.config;

import dev.grindtrack.auth.domain.Role;
import dev.grindtrack.auth.security.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless security: no sessions, no CSRF tokens. CSRF is mitigated by SameSite=Strict cookies
 * (documented in docs/auth.md); auth state travels only in httpOnly cookies validated by {@link
 * JwtAuthFilter}.
 *
 * <p>Three tiers, by URL. Public paths need no cookie. {@link #SHARED_PATHS} are open to both
 * roles: the session endpoints an account needs to stay signed in, notifications, and the chat.
 * Everything else is the owner's, which is the rule that keeps a partner out of the tracker, the
 * money, the journal and the assistant — and, because it is the default, out of whatever is added
 * next until it is named here. {@code SecurityConfigTest} walks every controller mapping to prove
 * it.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  static final String[] PUBLIC_PATHS = {
    "/",
    "/index.html",
    "/assets/**",
    "/vite.svg",
    "/favicon.ico",
    "/favicon.svg",
    // PWA shell. All of these are fetched before anyone logs in — the worker registers on
    // the landing page, and the install prompt reads the manifest and icons while logged out.
    "/manifest.webmanifest",
    "/sw.js",
    "/apple-touch-icon.png",
    "/icon-*.png",
    "/api/public/**",
    "/api/auth/login",
    "/api/auth/refresh",
    "/api/auth/logout",
    // Asked by the login form before anyone has signed in, to decide whether to show the
    // authenticator field. It reports only whether this browser holds a live device cookie --
    // never who, and never anything that gets you in without the password.
    "/api/auth/device",
  };

  /**
   * What a partner may reach, beyond the public paths. Named one by one on purpose: {@code
   * /api/auth/**} would have handed them the account management under {@code /api/auth/users}.
   */
  static final String[] SHARED_PATHS = {
    "/api/auth/me",
    "/api/auth/logout-all",
    "/api/auth/devices/forget",
    "/api/push/**",
    // The room, and its socket: the one place in the app a partner writes to.
    "/api/chat/**",
  };

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter)
      throws Exception {
    String owner = Role.OWNER.name();
    String partner = Role.PARTNER.name();
    return http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(PUBLIC_PATHS)
                    .permitAll()
                    .requestMatchers(SHARED_PATHS)
                    .hasAnyRole(owner, partner)
                    .anyRequest()
                    .hasRole(owner))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (req, res, ex) -> res.setStatus(HttpStatus.UNAUTHORIZED.value()))
                    // Signed in, but not theirs. Bare like the 401: a body would only say what
                    // the status already does.
                    .accessDeniedHandler(
                        (req, res, ex) -> res.setStatus(HttpStatus.FORBIDDEN.value())))
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}
