package dev.grindtrack.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.grindtrack.auth.domain.RefreshToken;
import dev.grindtrack.auth.domain.RefreshTokenRepository;
import dev.grindtrack.auth.domain.User;
import dev.grindtrack.auth.domain.UserRepository;
import dev.grindtrack.config.AppProperties;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  private static final long USER_ID = 7L;

  @Mock private UserRepository users;
  @Mock private RefreshTokenRepository refreshTokens;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private TotpService totpService;

  private AuthService service;

  @BeforeEach
  void setUp() {
    AppProperties props = new AppProperties("secret", 15, 30, 24, true, null, null);
    service = new AuthService(users, refreshTokens, passwordEncoder, totpService, props);
  }

  /** A live token issued {@code hoursAgo} ago, so a test can put it either side of the interval. */
  private static RefreshToken aged(String token, int hoursAgo) {
    return new RefreshToken(
        USER_ID,
        AuthService.sha256(token),
        OffsetDateTime.now().minusHours(hoursAgo),
        OffsetDateTime.now().plusDays(30));
  }

  private User userWithId() {
    User user = mock(User.class);
    when(user.getId()).thenReturn(USER_ID);
    return user;
  }

  @Test
  void authenticateSucceedsWithValidPasswordAndOtp() {
    User user = new User("casey", "hash", "SECRET");
    when(users.findByUsername("casey")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("pw", "hash")).thenReturn(true);
    when(totpService.verify("SECRET", "123456")).thenReturn(true);

    assertThat(service.authenticate("casey", "pw", "123456")).contains(user);
  }

  @Test
  void authenticateRejectsWrongPasswordWithoutConsultingTotp() {
    when(users.findByUsername("casey"))
        .thenReturn(Optional.of(new User("casey", "hash", "SECRET")));
    when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

    assertThat(service.authenticate("casey", "wrong", "123456")).isEmpty();
    verify(totpService, never()).verify(any(), any());
  }

  @Test
  void authenticateRejectsBadOtp() {
    when(users.findByUsername("casey"))
        .thenReturn(Optional.of(new User("casey", "hash", "SECRET")));
    when(passwordEncoder.matches("pw", "hash")).thenReturn(true);
    when(totpService.verify("SECRET", "000000")).thenReturn(false);

    assertThat(service.authenticate("casey", "pw", "000000")).isEmpty();
  }

  @Test
  void authenticateRejectsUnknownUser() {
    when(users.findByUsername("nobody")).thenReturn(Optional.empty());

    assertThat(service.authenticate("nobody", "pw", "123456")).isEmpty();
  }

  @Test
  void issueRefreshTokenStoresOnlyTheSha256Hash() {
    String token = service.issueRefreshToken(userWithId());

    ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
    verify(refreshTokens).save(saved.capture());
    assertThat(token).matches("[A-Za-z0-9_-]{43}"); // 32 random bytes, base64url, no padding
    assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
    assertThat(saved.getValue().isRevoked()).isFalse();
    assertThat(saved.getValue().getExpiresAt())
        .isBetween(OffsetDateTime.now().plusDays(29), OffsetDateTime.now().plusDays(31));
    assertThatStoredHashMatches(saved.getValue(), token);
  }

  @Test
  void issueRefreshTokenNeverRepeatsTokens() {
    User user = userWithId();
    assertThat(service.issueRefreshToken(user)).isNotEqualTo(service.issueRefreshToken(user));
  }

  @Test
  void renewingAFreshTokenHandsTheSameTokenBackAndSlidesItsExpiry() {
    // The whole point of the change: a renewal inside the rotation interval must not spend the
    // session token. Spending it on every access cookie is what turned a dropped response into a
    // password-and-TOTP login on every device.
    String presented = "fresh-token";
    RefreshToken stored = aged(presented, 1);
    OffsetDateTime originalExpiry = stored.getExpiresAt();
    when(refreshTokens.findByTokenHash(AuthService.sha256(presented)))
        .thenReturn(Optional.of(stored));
    // Not userWithId(): a renewal never issues a token, so getId() is never called and a strict
    // stub for it would fail. That is the assertion, in a way.
    User user = mock(User.class);
    when(users.findById(USER_ID)).thenReturn(Optional.of(user));

    Optional<AuthService.RenewedSession> renewed = service.renew(presented);

    assertThat(renewed).isPresent();
    assertThat(renewed.get().user()).isSameAs(user);
    assertThat(renewed.get().sessionToken()).isEqualTo(presented);
    assertThat(stored.isRevoked()).isFalse();
    assertThat(stored.getRotatedAt()).isNull();
    assertThat(stored.getExpiresAt()).isAfter(originalExpiry);
    verify(refreshTokens).save(stored);
  }

  @Test
  void renewingRepeatedlyNeverExhaustsASessionInsideTheRotationInterval() {
    // A day of ordinary use: dozens of access cookies, no rotations, one live token throughout.
    String presented = "fresh-token";
    RefreshToken stored = aged(presented, 1);
    when(refreshTokens.findByTokenHash(AuthService.sha256(presented)))
        .thenReturn(Optional.of(stored));
    when(users.findById(USER_ID)).thenReturn(Optional.of(mock(User.class)));

    for (int i = 0; i < 40; i++) {
      assertThat(service.renew(presented))
          .hasValueSatisfying(
              r -> {
                assertThat(r.sessionToken()).isEqualTo(presented);
              });
    }
    assertThat(stored.isRevoked()).isFalse();
    verify(refreshTokens, never()).findByUserIdAndRevokedFalse(anyLong());
  }

  @Test
  void renewingATokenPastTheRotationIntervalRevokesItAndIssuesAReplacement() {
    String presented = "presented-token";
    RefreshToken stored = aged(presented, 48);
    when(refreshTokens.findByTokenHash(AuthService.sha256(presented)))
        .thenReturn(Optional.of(stored));
    User user = userWithId();
    when(users.findById(USER_ID)).thenReturn(Optional.of(user));

    Optional<AuthService.RenewedSession> rotated = service.renew(presented);

    assertThat(rotated).isPresent();
    assertThat(rotated.get().user()).isSameAs(user);
    assertThat(stored.isRevoked()).isTrue();

    ArgumentCaptor<RefreshToken> saves = ArgumentCaptor.forClass(RefreshToken.class);
    verify(refreshTokens, times(2)).save(saves.capture());
    assertThat(saves.getAllValues().get(0)).isSameAs(stored);
    RefreshToken replacement = saves.getAllValues().get(1);
    assertThat(replacement.isRevoked()).isFalse();
    assertThatStoredHashMatches(replacement, rotated.get().sessionToken());
  }

  @Test
  void presentingATokenRevokedWithoutRotationRevokesEveryLiveTokenForTheUser() {
    String presented = "stolen-token";
    RefreshToken stored =
        new RefreshToken(USER_ID, AuthService.sha256(presented), OffsetDateTime.now().plusDays(5));
    stored.revoke();
    when(refreshTokens.findByTokenHash(AuthService.sha256(presented)))
        .thenReturn(Optional.of(stored));
    RefreshToken live1 = new RefreshToken(USER_ID, "h1", OffsetDateTime.now().plusDays(5));
    RefreshToken live2 = new RefreshToken(USER_ID, "h2", OffsetDateTime.now().plusDays(5));
    when(refreshTokens.findByUserIdAndRevokedFalse(USER_ID)).thenReturn(List.of(live1, live2));

    assertThat(service.renew(presented)).isEmpty();
    assertThat(live1.isRevoked()).isTrue();
    assertThat(live2.isRevoked()).isTrue();
    verify(refreshTokens).saveAll(List.of(live1, live2));
    verify(refreshTokens, never()).save(any());
    verify(users, never()).findById(anyLong());
  }

  @Test
  void presentingATokenRotatedInsideTheGraceWindowIssuesAnotherRatherThanRevokingEverything() {
    // The loser of a rotation race: two windows sharing a cookie jar, or a client that never
    // received the response to the rotation it won. Both present a token rotated seconds ago.
    String presented = "raced-token";
    RefreshToken stored =
        new RefreshToken(USER_ID, AuthService.sha256(presented), OffsetDateTime.now().plusDays(5));
    stored.markRotated(OffsetDateTime.now().minusMinutes(90));
    when(refreshTokens.findByTokenHash(AuthService.sha256(presented)))
        .thenReturn(Optional.of(stored));
    User user = userWithId();
    when(users.findById(USER_ID)).thenReturn(Optional.of(user));

    Optional<AuthService.RenewedSession> rotated = service.renew(presented);

    assertThat(rotated).isPresent();
    assertThat(rotated.get().user()).isSameAs(user);
    ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
    verify(refreshTokens).save(saved.capture());
    assertThat(saved.getValue().isRevoked()).isFalse();
    assertThatStoredHashMatches(saved.getValue(), rotated.get().sessionToken());
    verify(refreshTokens, never()).findByUserIdAndRevokedFalse(anyLong());
    verify(refreshTokens, never()).saveAll(any());
  }

  @Test
  void presentingATokenRotatedLongAgoStillRevokesEveryLiveTokenForTheUser() {
    String presented = "replayed-token";
    RefreshToken stored =
        new RefreshToken(USER_ID, AuthService.sha256(presented), OffsetDateTime.now().plusDays(5));
    stored.markRotated(OffsetDateTime.now().minusDays(3));
    when(refreshTokens.findByTokenHash(AuthService.sha256(presented)))
        .thenReturn(Optional.of(stored));
    RefreshToken live = new RefreshToken(USER_ID, "h1", OffsetDateTime.now().plusDays(5));
    when(refreshTokens.findByUserIdAndRevokedFalse(USER_ID)).thenReturn(List.of(live));

    assertThat(service.renew(presented)).isEmpty();
    assertThat(live.isRevoked()).isTrue();
    verify(refreshTokens).saveAll(List.of(live));
    verify(refreshTokens, never()).save(any());
  }

  @Test
  void aTokenRevokedByLogoutGetsNoGraceHoweverRecentTheLogout() {
    // revoke() records no instant, deliberately: presenting a logged-out token is not a race, so
    // it must trip the cascade even a second later.
    String presented = "logged-out-token";
    RefreshToken stored =
        new RefreshToken(USER_ID, AuthService.sha256(presented), OffsetDateTime.now().plusDays(5));
    stored.revoke();
    when(refreshTokens.findByTokenHash(AuthService.sha256(presented)))
        .thenReturn(Optional.of(stored));
    RefreshToken live = new RefreshToken(USER_ID, "h1", OffsetDateTime.now().plusDays(5));
    when(refreshTokens.findByUserIdAndRevokedFalse(USER_ID)).thenReturn(List.of(live));

    assertThat(service.renew(presented)).isEmpty();
    assertThat(stored.getRotatedAt()).isNull();
    assertThat(live.isRevoked()).isTrue();
    verify(refreshTokens, never()).save(any());
  }

  @Test
  void rotationRecordsWhenTheTokenWasRotatedAwaySoTheGraceWindowCanBeMeasured() {
    String presented = "presented-token";
    RefreshToken stored = aged(presented, 48);
    when(refreshTokens.findByTokenHash(AuthService.sha256(presented)))
        .thenReturn(Optional.of(stored));
    User user = userWithId();
    when(users.findById(USER_ID)).thenReturn(Optional.of(user));

    service.renew(presented);

    assertThat(stored.getRotatedAt())
        .isBetween(OffsetDateTime.now().minusMinutes(1), OffsetDateTime.now());
  }

  @Test
  void renewRejectsAnExpiredTokenWithoutRevokingAnything() {
    String presented = "old-token";
    RefreshToken stored =
        new RefreshToken(
            USER_ID, AuthService.sha256(presented), OffsetDateTime.now().minusSeconds(1));
    when(refreshTokens.findByTokenHash(AuthService.sha256(presented)))
        .thenReturn(Optional.of(stored));

    assertThat(service.renew(presented)).isEmpty();
    assertThat(stored.isRevoked()).isFalse();
    verify(refreshTokens, never()).save(any());
    verify(refreshTokens, never()).findByUserIdAndRevokedFalse(anyLong());
  }

  @Test
  void renewRejectsAnUnknownToken() {
    when(refreshTokens.findByTokenHash(any())).thenReturn(Optional.empty());

    assertThat(service.renew("no-such-token")).isEmpty();
    verify(refreshTokens, never()).save(any());
  }

  @Test
  void revokeMarksThePresentedTokenRevoked() {
    String presented = "some-token";
    RefreshToken stored =
        new RefreshToken(USER_ID, AuthService.sha256(presented), OffsetDateTime.now().plusDays(5));
    when(refreshTokens.findByTokenHash(AuthService.sha256(presented)))
        .thenReturn(Optional.of(stored));

    service.revoke(presented);

    assertThat(stored.isRevoked()).isTrue();
    verify(refreshTokens).save(stored);
  }

  @Test
  void revokeOfUnknownTokenIsANoOp() {
    when(refreshTokens.findByTokenHash(any())).thenReturn(Optional.empty());

    service.revoke("no-such-token");

    verify(refreshTokens, never()).save(any());
  }

  @Test
  void sha256MatchesKnownVector() {
    assertThat(AuthService.sha256("abc"))
        .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
  }

  /** The row must hold the SHA-256 hex of the raw token, never the raw token itself. */
  private static void assertThatStoredHashMatches(RefreshToken stored, String rawToken) {
    assertThat(storedHash(stored)).isEqualTo(AuthService.sha256(rawToken)).isNotEqualTo(rawToken);
  }

  private static String storedHash(RefreshToken token) {
    // RefreshToken deliberately exposes no tokenHash getter; read it reflectively for assertions.
    try {
      var field = RefreshToken.class.getDeclaredField("tokenHash");
      field.setAccessible(true);
      return (String) field.get(token);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError(e);
    }
  }
}
