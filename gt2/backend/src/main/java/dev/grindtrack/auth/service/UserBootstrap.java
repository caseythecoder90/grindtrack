package dev.grindtrack.auth.service;

import dev.grindtrack.auth.domain.User;
import dev.grindtrack.auth.domain.UserRepository;
import dev.grindtrack.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * First boot only: if no user exists and bootstrap credentials are configured, create the user and
 * log the TOTP provisioning URI once. Scan/enter it into your authenticator app, then it is never
 * shown again.
 */
@Component
public class UserBootstrap implements CommandLineRunner {

  private static final Logger log = LoggerFactory.getLogger(UserBootstrap.class);

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final TotpService totpService;
  private final AppProperties props;

  public UserBootstrap(
      UserRepository users,
      PasswordEncoder passwordEncoder,
      TotpService totpService,
      AppProperties props) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.totpService = totpService;
    this.props = props;
  }

  @Override
  public void run(String... args) {
    if (users.count() > 0) {
      return;
    }
    String username = props.bootstrapUsername();
    String password = props.bootstrapPassword();
    if (username == null || username.isBlank() || password == null || password.isBlank()) {
      log.warn("No users exist and no bootstrap credentials set - login will be impossible.");
      return;
    }
    String secret = totpService.generateSecret();
    users.save(new User(username, passwordEncoder.encode(password), secret));
    log.info("==========================================================");
    log.info("Bootstrap user '{}' created.", username);
    log.info("TOTP secret (enter manually in your authenticator): {}", secret);
    log.info("Or paste this otpauth URI into a QR generator and scan it:");
    log.info("{}", totpService.provisioningUri(username, secret));
    log.info("This is shown ONCE. It is not retrievable later.");
    log.info("==========================================================");
  }
}
