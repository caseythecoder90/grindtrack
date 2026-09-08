package dev.grindtrack.auth.service;

import dev.grindtrack.auth.domain.TrustedDevice;
import dev.grindtrack.auth.domain.TrustedDeviceRepository;
import dev.grindtrack.auth.domain.User;
import dev.grindtrack.config.AppProperties;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Remembering that a browser has already presented the second factor.
 *
 * <p>Exists because a TOTP code on every sign-in is the part people route around. The password is
 * unchanged and still required; only the authenticator step is skipped, and only on a browser that
 * passed it before.
 *
 * <p>Everything here is deliberately conservative about what a device token can do. It is never an
 * identity: {@link #trustedUserFor} answers "which user, if any, does this token belong to", and
 * the caller must compare that against the user the password actually authenticated. A device token
 * presented alongside the wrong password gets nowhere.
 */
@Service
public class TrustedDeviceService {

  private final TrustedDeviceRepository devices;
  private final AppProperties props;
  private final SecureRandom random = new SecureRandom();

  public TrustedDeviceService(TrustedDeviceRepository devices, AppProperties props) {
    this.devices = devices;
    this.props = props;
  }

  /**
   * The user this device token belongs to, if it is live.
   *
   * <p>Returns the id rather than a boolean so the caller is forced to check whose device it is. A
   * boolean here would silently let a token trusted by one account waive the second factor for
   * another — which matters even in a single-user app, because the check should not depend on how
   * many rows happen to be in the users table.
   */
  @Transactional(readOnly = true)
  public Optional<Long> trustedUserFor(String presentedToken) {
    if (presentedToken == null || presentedToken.isBlank()) {
      return Optional.empty();
    }
    return devices
        .findByTokenHash(AuthService.sha256(presentedToken))
        .filter(d -> d.isUsable(OffsetDateTime.now()))
        .map(TrustedDevice::getUserId);
  }

  /**
   * Issues a device token, storing only its hash. The raw value goes in a cookie and nowhere else.
   */
  @Transactional
  public String trust(User user) {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    devices.save(
        new TrustedDevice(
            user.getId(),
            AuthService.sha256(token),
            OffsetDateTime.now().plusDays(props.trustedDeviceDays())));
    return token;
  }

  /** Records a sign-in on this device and slides its expiry. */
  @Transactional
  public void touch(String presentedToken) {
    devices
        .findByTokenHash(AuthService.sha256(presentedToken))
        .ifPresent(
            d -> {
              d.used(
                  OffsetDateTime.now(), OffsetDateTime.now().plusDays(props.trustedDeviceDays()));
              devices.save(d);
            });
  }

  /**
   * Forget every device for this user.
   *
   * <p>The escape hatch a feature like this cannot ship without: a lost phone must be answerable
   * with something better than changing the password and hoping.
   */
  @Transactional
  public int forgetAll(Long userId) {
    List<TrustedDevice> live = devices.findByUserIdAndRevokedFalse(userId);
    live.forEach(TrustedDevice::revoke);
    devices.saveAll(live);
    return live.size();
  }
}
