package dev.grindtrack.auth.security;

import dev.grindtrack.auth.domain.Role;
import java.security.Principal;
import org.springframework.security.core.Authentication;

/**
 * The account behind a request: what the access cookie proved.
 *
 * <p>This is the principal {@link JwtAuthFilter} puts in the security context, so a controller that
 * needs to know who is asking reads it from its {@code Principal} argument through {@link #of} and
 * never touches the database for it. The id is what the per-account tables key on; the role is what
 * the security config authorised by; the name is for logs and the session response.
 */
public record SignedIn(long id, String username, Role role) implements Principal {

  @Override
  public String getName() {
    return username;
  }

  /** The authority string Spring Security's {@code hasRole} matches: {@code ROLE_OWNER}. */
  public String authority() {
    return "ROLE_" + role.name();
  }

  /**
   * Unwrap a controller's {@code Principal} argument.
   *
   * <p>Spring hands the whole {@link Authentication}, whose principal is this record; a test may
   * hand the record itself. Anything else is a request that reached an authenticated endpoint
   * without going through the filter, which the security config makes impossible, so it is a bug
   * rather than a 401.
   */
  public static SignedIn of(Principal principal) {
    if (principal instanceof SignedIn signedIn) {
      return signedIn;
    }
    if (principal instanceof Authentication auth && auth.getPrincipal() instanceof SignedIn who) {
      return who;
    }
    throw new IllegalStateException("no signed-in account on this request");
  }
}
