package dev.grindtrack.auth.service;

import dev.grindtrack.auth.domain.Role;
import dev.grindtrack.auth.domain.User;
import dev.grindtrack.auth.domain.UserRepository;
import dev.grindtrack.web.BadRequestException;
import dev.grindtrack.web.ConflictException;
import java.util.List;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The accounts: the owner making a partner's, and minding it afterwards.
 *
 * <p>Only partners are made here. The owner's account comes from {@code UserBootstrap} on the first
 * boot and the database allows exactly one, so there is no "create owner" and nothing here will
 * touch the owner's row: a password reset that could reach the owner's own account is a way to be
 * locked out of it by a mistyped id. Every method that names an account checks it is a partner
 * first.
 */
@Service
public class UserService {

  private static final Logger log = LoggerFactory.getLogger(UserService.class);

  /** Long enough to be a passphrase, short enough to type on a phone once in ninety days. */
  static final int MIN_PASSWORD_LENGTH = 12;

  static final int MAX_USERNAME_LENGTH = 64;

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final TotpService totpService;

  public UserService(
      UserRepository users, PasswordEncoder passwordEncoder, TotpService totpService) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.totpService = totpService;
  }

  @Transactional(readOnly = true)
  public List<User> list() {
    return users.findAllByOrderByIdAsc();
  }

  /**
   * A new partner, with the authenticator secret that the caller shows once and forgets.
   *
   * @throws ConflictException when the name is taken
   * @throws BadRequestException when the name or the password will not do
   */
  @Transactional
  public Created createPartner(String username, String password) {
    String name = requireUsername(username);
    requirePassword(password);
    users
        .findByUsername(name)
        .ifPresent(
            taken -> {
              throw new ConflictException("there is already an account called " + name);
            });
    String secret = totpService.generateSecret();
    User saved = users.save(new User(name, passwordEncoder.encode(password), secret, Role.PARTNER));
    log.info("Partner account '{}' created", name);
    return new Created(saved, secret, totpService.provisioningUri(name, secret));
  }

  /**
   * A partner, by id.
   *
   * @throws NoSuchElementException when there is no such account — a 404
   * @throws BadRequestException when the id is the owner's: not managed from here, by design
   */
  @Transactional(readOnly = true)
  public User partner(long id) {
    User user =
        users.findById(id).orElseThrow(() -> new NoSuchElementException("no account " + id));
    if (user.getRole() != Role.PARTNER) {
      throw new BadRequestException("the owner's account is not managed from here");
    }
    return user;
  }

  /** A forgotten password, replaced. Sessions are left alone; the caller ends those if it wants. */
  @Transactional
  public void resetPassword(long id, String password) {
    requirePassword(password);
    User user = partner(id);
    user.replacePasswordHash(passwordEncoder.encode(password));
    users.save(user);
    log.info("Password reset for partner account '{}'", user.getUsername());
  }

  private static String requireUsername(String username) {
    String name = username == null ? "" : username.trim();
    if (name.isEmpty()) {
      throw new BadRequestException("a username is needed");
    }
    if (name.length() > MAX_USERNAME_LENGTH) {
      throw new BadRequestException("a username is at most " + MAX_USERNAME_LENGTH + " characters");
    }
    if (name.chars().anyMatch(Character::isWhitespace)) {
      throw new BadRequestException("a username has no spaces");
    }
    return name;
  }

  private static void requirePassword(String password) {
    if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
      throw new BadRequestException(
          "a password is at least " + MIN_PASSWORD_LENGTH + " characters");
    }
  }

  /** The row, and the two forms of the secret an authenticator app takes. */
  public record Created(User user, String totpSecret, String otpauthUri) {}
}
