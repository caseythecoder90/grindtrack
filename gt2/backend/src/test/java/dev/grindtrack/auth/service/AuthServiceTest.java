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
import java.util.UUID;
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
  private static final UUID FAMILY = UUID.fromString("00000000-0000-0000-0000-00000000f0f0");

  @Mock private UserRepository users;
  @Mock private RefreshTokenRepository refreshTokens;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private TotpService totpService;

  private AuthService service;

  @BeforeEach
  void setUp() {
    AppProperties props = new AppProperties("secret", 15, 30, 24, 30, true, null, null);
    service = new AuthService(users, refreshTokens, passwordEncoder, totpService, props);
  }

  /** A live token in FAMILY issued {@code hoursAgo}, so a test can put it either side of the interval. */
  private static RefreshToken aged(String token, int hoursAgo) {
    return new RefreshToken(
        USER_ID,
        FAMILY,
        AuthService.sha256(token),
        OffsetDateTime.now().minusHours(hoursAgo),
        OffsetDateTime.now().plusDays(30));
  }

  /** A live token in FAMILY, issued now. */
  private static RefreshToken inFamily(String token) {
    return aged(token, 0);
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
  void aTrustedDeviceSkipsTheCodeButNeverThePassword() {
    User user = new User("casey", "hash", "SECRET");
    when(users.findByUsername("casey")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

    // The device belongs to this user and the code is absent -- and it still fails, because the
    // password did. A device token is not a credential.
    assertThat(service.authenticate("casey", "wrong", "", USER_ID)).isEmpty();
    verify(totpService, never()).verify(any(), any());
  }

  @Test
  void aTrustedDeviceLetsACorrectPasswordInWithoutACode() {
    User user = mock(User.class);
    when(user.getId()).thenReturn(USER_ID);
    when(user.getPasswordHash()).thenReturn("hash");
    when(users.findByUsername("casey")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("pw", "hash")).thenReturn(true);

    assertThat(service.authenticate("casey", "pw", "", USER_ID)).contains(user);
    verify(totpService, never()).verify(any(), any());
  }

  @Test
  void aDeviceTrustedByADifferentUserDoesNotWaiveAnything() {
    // The check that must not be a boolean. A device trusted for user 99 offers user 7 nothing,
    // so the code is still demanded -- and here it is wrong.
    User user = mock(User.class);
    when(user.getId()).thenReturn(USER_ID);
    when(user.getPasswordHash()).thenReturn("hash");
    when(user.getTotpSecret()).thenReturn("SECRET");
    when(users.findByUsername("casey")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("pw", "hash")).thenReturn(true);
    when(totpService.verify("SECRET", "000000")).thenReturn(false);

    assertThat(service.authenticate("casey", "pw", "000000", 99L)).isEmpty();
  }

  @Test
  void anUntrustedBrowserStillNeedsTheCode() {
    User user = new User("casey", "hash", "SECRET");
    when(users.findByUsername("casey")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("pw", "hash")).thenReturn(true);
    when(totpService.verify("SECRET", "")).thenReturn(false);

    assertThat(service.authenticate("casey", "pw", "", null)).isEmpty();
  }

  @Test
  void authenticateRejectsUnknownUser() {
    when(users.findByUsername("nobody")).thenReturn(Optional.empty());

    assertThat(service.authenticate("nobody", "pw", "123456")).isEmpty();
  }

  @Test
  void issueRefreshTokenStoresOnlyTheSha256HashAtTheHeadOfANewFamily() {
    String token = service.issueRefreshToken(userWithId());

    ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
    verify(refreshTokens).save(saved.capture());
    assertThat(token).matches("[A-Za-z0-9_-]{43}"); // 32 random bytes, base64url, no padding
    assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
    assertThat(saved.getValue().getFamilyId()).isNotNull();
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
  void everyLoginStartsItsOwnFamily() {
    // Two devices signing in are two families. That independence is the whole fix: nothing one
    // device does with its cookie can ever be read as evidence against the other.
    User user = userWithId();
    service.issueRefreshToken(user);
    service.issueRefreshToken(user);

    ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
    verify(refreshTokens, times(2)).save(saved.capture());
    assertThat(saved.getAllValues().get(0).getFamilyId())
        .isNotEqualTo(saved.getAllValues().get(1).getFamilyId());
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
    verify(refreshTokens, never()).findByFamilyIdAndRevokedFalse(any());
  }

  @Test
  void renewingATokenPastTheRotationIntervalRotatesItAndIssuesASuccessorInTheSameFamily() {
    String presented = "presented-token";
    RefreshToken stored = aged(presented, 48);
    when(refreshTokens.findByTokenHash(AuthService.sha256(presented)))
        .thenReturn(Optional.of(stored));
    User user = mock(User.class);
    when(users.findById(USER_ID)).thenReturn(Optional.of(user));

    Optional<AuthService.RenewedSession> rotated = service.renew(presented);

    assertThat(rotated).isPresent();
    assertThat(rotated.get().user()).isSameAs(user);
    assertThat(stored.isRevoked()).isTrue();
    assertThat(stored.getRotatedAt())
        .isBetween(OffsetDateTime.now().minusMinutes(1), OffsetDateTime.now());

    ArgumentCaptor<RefreshToken> saves = ArgumentCaptor.forClass(RefreshToken.class);
    verify(refreshTokens, times(2)).save(saves.capture());
    assertThat(saves.getAllValues().get(0)).isSameAs(stored);
    RefreshToken successor = saves.getAllValues().get(1);
    assertThat(successor.isRevoked()).isFalse();
    assertThat(successor.getUserId()).isEqualTo(USER_ID);
    assertThat(successor.getFamilyId()).isEqualTo(FAMILY);
    assertThatStoredHashMatches(successor, rotated.get().sessionToken());
  }

  @Test
  void presentingATokenRevokedWithoutRotationIsRefusedAndRevokesNothing() {
    // A logged-out cookie, or one whose family was already revoked. It has no successor, so it is
    // evidence of nothing, and it must not end anyone else's session: this is the case that had
    // one stale browser signing every other device out each time it opened the app.
    String presented = "logged-out-token";
    RefreshToken stored = inFamily(presented);
    stored.revoke();
    when(refreshTokens.findByTokenHash(AuthService.sha256(presented)))
        .thenReturn(Optional.of(stored));

    assertThat(service.renew(presented)).isEmpty();
    assertThat(stored.getRotatedAt()).isNull();
    verify(refreshTokens, never()).findByUserIdAndRevokedFalse(anyLong());
    verify(refreshTokens, never()).findByFamilyIdAndRevokedFalse(any());
    verify(refreshTokens, never()).save(any());
    verify(refreshTokens, never()).saveAll(any());
    verify(users, never()).findById(anyLong());
  }

  @Test
  void presentingATokenRotatedInsideTheGraceWindowIssuesASiblingRatherThanRevokingAnything() {
    // The loser of a rotation race: two windows sharing a cookie jar, or a client that never
    // received the response to the rotation it won. Both present a token rotated recently.
    String presented = "raced-token";
    RefreshToken stored = inFamily(presented);
    stored.markRotated(OffsetDateTime.now().minusMinutes(90));
    when(refreshTokens.findByTokenHash(AuthService.sha256(presented)))
        .thenReturn(Optional.of(stored));
    User user = mock(User.class);
    when(users.findById(USER_ID)).thenReturn(Optional.of(user));

    Optional<AuthService.RenewedSession> rotated = service.renew(presented);

    assertThat(rotated).isPresent();
    assertThat(rotated.get().user()).isSameAs(user);
    ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
    verify(refreshTokens).save(saved.capture());
    assertThat(saved.getValue().isRevoked()).isFalse();
    assertThat(saved.getValue().getFamilyId()).isEqualTo(FAMILY);
    assertThatStoredHashMatches(saved.getValue(), rotated.get().sessionToken());
    verify(refreshTokens, never()).findByFamilyIdAndRevokedFalse(any());
    verify(refreshTokens, never()).saveAll(any());
  }

  @Test
  void presentingATokenRotatedLongAgoRevokesItsFamilyAndNoOtherSession() {
    // Reuse. Whoever is replaying this token shares a family with the honest holder of its
    // successor, and that family dies. The other device -- a different login, a different family --
    // is never consulted, let alone revoked.
    String presented = "replayed-token";
    RefreshToken stored = inFamily(presented);
    stored.markRotated(OffsetDateTime.now().minusDays(3));
    when(refreshTokens.findByTokenHash(AuthService.sha256(presented)))
        .thenReturn(Optional.of(stored));
    RefreshToken successor = inFamily("successor");
    when(refreshTokens.findByFamilyIdAndRevokedFalse(FAMILY)).thenReturn(List.of(successor));

    assertThat(service.renew(presented)).isEmpty();
    assertThat(successor.isRevoked()).isTrue();
    verify(refreshTokens).saveAll(List.of(successor));
    verify(refreshTokens, never()).findByUserIdAndRevokedFalse(anyLong());
    verify(refreshTokens, never()).save(any());
    verify(users, never()).findById(anyLong());
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
    verify(refreshTokens, never()).findByFamilyIdAndRevokedFalse(any());
  }

  @Test
  void renewRejectsAnUnknownToken() {
    when(refreshTokens.findByTokenHash(any())).thenReturn(Optional.empty());

    assertThat(service.renew("no-such-token")).isEmpty();
    verify(refreshTokens, never()).save(any());
  }

  @Test
  void revokeMarksThePresentedTokenRevokedAndNothingElse() {
    String presented = "some-token";
    RefreshToken stored = inFamily(presented);
    when(refreshTokens.findByTokenHash(AuthService.sha256(presented)))
        .thenReturn(Optional.of(stored));

    service.revoke(presented);

    assertThat(stored.isRevoked()).isTrue();
    assertThat(stored.getRotatedAt()).isNull();
    verify(refreshTokens).save(stored);
    verify(refreshTokens, never()).saveAll(any());
  }

  @Test
  void revokeOfUnknownTokenIsANoOp() {
    when(refreshTokens.findByTokenHash(any())).thenReturn(Optional.empty());

    service.revoke("no-such-token");

    verify(refreshTokens, never()).save(any());
  }

  @Test
  void revokeAllForUserEndsEveryLiveSessionAcrossFamiliesAndReportsHowMany() {
    // "Log out everywhere": the one deliberate path across families.
    RefreshToken phone = inFamily("phone");
    RefreshToken laptop =
        new RefreshToken(
            USER_ID,
            UUID.randomUUID(),
            AuthService.sha256("laptop"),
            OffsetDateTime.now(),
            OffsetDateTime.now().plusDays(30));
    when(refreshTokens.findByUserIdAndRevokedFalse(USER_ID)).thenReturn(List.of(phone, laptop));

    assertThat(service.revokeAllForUser(USER_ID)).isEqualTo(2);
    assertThat(phone.isRevoked()).isTrue();
    assertThat(laptop.isRevoked()).isTrue();
    verify(refreshTokens).saveAll(List.of(phone, laptop));
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
