package dev.grindtrack.config;

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
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private static final String[] PUBLIC_PATHS = {
    "/",
    "/index.html",
    "/assets/**",
    "/vite.svg",
    "/favicon.ico",
    "/favicon.svg",
    "/api/public/**",
    "/api/auth/login",
    "/api/auth/refresh",
    "/api/auth/logout",
  };

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter)
      throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth -> auth.requestMatchers(PUBLIC_PATHS).permitAll().anyRequest().authenticated())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                    (req, res, ex) -> res.setStatus(HttpStatus.UNAUTHORIZED.value())))
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}
