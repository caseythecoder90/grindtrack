package dev.grindtrack.push.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.grindtrack.auth.domain.Role;
import dev.grindtrack.config.PushProperties;
import dev.grindtrack.push.domain.PushSubscription;
import dev.grindtrack.push.domain.PushSubscriptionRepository;
import dev.grindtrack.web.BadRequestException;
import dev.grindtrack.web.ServiceOffException;
import java.io.IOException;
import java.security.interfaces.ECPublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Off is a state; a gone subscription cleans itself up; a bad answer keeps the row; and what goes
 * over the wire is an encrypted body under a VAPID header, never the sentence itself.
 */
class PushServiceTest {

  private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
  private static final String ENDPOINT = "https://web.push.apple.com/QGb5zW0Z9nJ3fXkqLm2P";

  /** A transport that answers what it is told and remembers what it was handed. */
  private static final class FakeTransport implements PushTransport {
    int status = 201;
    String answer = "";
    IOException failure;
    final List<String> endpoints = new ArrayList<>();
    final List<Map<String, String>> headers = new ArrayList<>();
    final List<byte[]> bodies = new ArrayList<>();

    @Override
    public Reply send(String endpoint, Map<String, String> headers, byte[] body)
        throws IOException {
      endpoints.add(endpoint);
      this.headers.add(headers);
      bodies.add(body);
      if (failure != null) {
        throw failure;
      }
      return new Reply(status, answer);
    }
  }

  private PushSubscriptionRepository repo;
  private FakeTransport transport;
  private PushService service;
  private String p256dh;
  private String auth;

  @BeforeEach
  void setUp() {
    repo = mock(PushSubscriptionRepository.class);
    // Saving assigns an id, as the database would; the service reads it back into the answer.
    when(repo.save(any()))
        .thenAnswer(
            inv -> {
              PushSubscription row = inv.getArgument(0);
              if (row.getId() == null) {
                ReflectionTestUtils.setField(row, "id", 4L);
              }
              return row;
            });
    transport = new FakeTransport();
    service =
        new PushService(
            VapidTest.freshProperties("mailto:casey@example.com"),
            repo,
            transport,
            new ObjectMapper());
    // What a browser hands over when it subscribes: its own P-256 point and a 16-byte secret.
    p256dh = B64.encodeToString(Vapid.raw((ECPublicKey) PayloadCipher.freshKeyPair().getPublic()));
    auth = B64.encodeToString(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16});
  }

  private PushSubscription subscribed() {
    return new PushSubscription(1L, ENDPOINT, p256dh, auth, "iPhone · Safari");
  }

  @Test
  void offIsAStateNotAnError() {
    PushService off =
        new PushService(new PushProperties("", "", null), repo, transport, new ObjectMapper());

    assertThat(off.configured()).isFalse();
    assertThat(off.status(1L).publicKey()).isNull();
    assertThat(off.send(PushService.Notification.weeklyReview()))
        .isEqualTo(new PushService.Outcome(0, 0, 0));
    assertThat(transport.endpoints).isEmpty();
    assertThatThrownBy(() -> off.subscribe(1L, ENDPOINT, p256dh, auth, null))
        .isInstanceOf(ServiceOffException.class)
        .hasMessageContaining("PUSH_VAPID_PUBLIC_KEY");
  }

  @Test
  void subscribingStoresOneRowPerEndpointAndResubscribingReplacesItsKeys() {
    when(repo.findByEndpoint(ENDPOINT)).thenReturn(Optional.empty());
    when(repo.countByUserId(1L)).thenReturn(1L);

    PushService.Subscribed first = service.subscribe(1L, ENDPOINT, p256dh, auth, "iPhone");
    assertThat(first.id()).isEqualTo(4);
    assertThat(first.devices()).isEqualTo(1);
    ArgumentCaptor<PushSubscription> saved = ArgumentCaptor.forClass(PushSubscription.class);
    verify(repo).save(saved.capture());
    assertThat(saved.getValue().getEndpoint()).isEqualTo(ENDPOINT);
    assertThat(saved.getValue().getUserAgent()).isEqualTo("iPhone");

    PushSubscription existing = saved.getValue();
    when(repo.findByEndpoint(ENDPOINT)).thenReturn(Optional.of(existing));
    String newAuth = B64.encodeToString(new byte[16]);
    service.subscribe(1L, ENDPOINT, p256dh, newAuth, "iPhone again");
    assertThat(existing.getAuth()).isEqualTo(newAuth);
    assertThat(existing.getUserAgent()).isEqualTo("iPhone again");
  }

  @Test
  void keysThatAreNotWhatABrowserProducesAreA400WithASentence() {
    assertThatThrownBy(() -> service.subscribe(1L, ENDPOINT, "AAAA", auth, null))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("p256dh");
    assertThatThrownBy(() -> service.subscribe(1L, ENDPOINT, p256dh, "short", null))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("auth");
    assertThatThrownBy(() -> service.subscribe(1L, "http://plain.example/x", p256dh, auth, null))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("https");
    verify(repo, never()).save(any());
  }

  @Test
  void aSendIsAnEncryptedBodyUnderAVapidHeaderWithTheNotificationsTtl() {
    PushSubscription row = subscribed();
    when(repo.findAllByRole(Role.OWNER)).thenReturn(List.of(row));

    PushService.Outcome outcome =
        service.send(PushService.Notification.morningBrief("Two blocks against CKA."));

    assertThat(outcome).isEqualTo(new PushService.Outcome(1, 0, 0));
    assertThat(transport.endpoints).containsExactly(ENDPOINT);
    Map<String, String> headers = transport.headers.get(0);
    assertThat(headers.get("Authorization")).startsWith("vapid t=").contains(", k=");
    assertThat(headers.get("Content-Encoding")).isEqualTo("aes128gcm");
    assertThat(headers.get("TTL")).isEqualTo("21600");
    byte[] body = transport.bodies.get(0);
    // salt, then the record size the header promises, then our 65-byte key
    assertThat(body[18]).isEqualTo((byte) 0x10);
    assertThat(body[20]).isEqualTo((byte) 65);
    assertThat(new String(body, java.nio.charset.StandardCharsets.ISO_8859_1))
        .doesNotContain("CKA");
    assertThat(row.getLastSentAt()).isNotNull();
    verify(repo).save(row);
  }

  @Test
  void aGoneSubscriptionIsDeletedAndABadAnswerKeepsIt() {
    PushSubscription gone = subscribed();
    when(repo.findAllByRole(Role.OWNER)).thenReturn(List.of(gone));
    transport.status = 410;

    assertThat(service.send(PushService.Notification.weeklyReview()))
        .isEqualTo(new PushService.Outcome(0, 1, 0));
    verify(repo).delete(gone);

    PushSubscription kept = subscribed();
    when(repo.findAllByRole(Role.OWNER)).thenReturn(List.of(kept));
    transport.status = 403;
    transport.answer = "{\"reason\":\"VapidPkHashMismatch\"}";
    PushService.Outcome refused = service.send(PushService.Notification.weeklyReview());
    assertThat(refused.failed()).isEqualTo(1);
    assertThat(refused.reason())
        .isEqualTo(
            "the push service answered 403 from https://web.push.apple.com:"
                + " {\"reason\":\"VapidPkHashMismatch\"}");
    verify(repo, never()).delete(kept);
    assertThat(kept.getLastSentAt()).isNull();

    transport.failure = new IOException("connection reset");
    PushService.Outcome unreachable = service.send(PushService.Notification.weeklyReview());
    assertThat(unreachable.failed()).isEqualTo(1);
    assertThat(unreachable.reason())
        .isEqualTo("could not reach https://web.push.apple.com: connection reset");
    verify(repo, never()).delete(kept);
  }

  @Test
  void theTestGoesToTheDeviceThatAskedForIt() {
    PushSubscription mine = subscribed();
    when(repo.findByEndpoint(ENDPOINT)).thenReturn(Optional.of(mine));

    assertThat(service.sendTo(1L, ENDPOINT, PushService.Notification.test()))
        .isEqualTo(new PushService.Outcome(1, 0, 0));
    assertThat(transport.endpoints).containsExactly(ENDPOINT);

    when(repo.findByEndpoint("https://web.push.apple.com/other")).thenReturn(Optional.empty());
    assertThatThrownBy(
            () ->
                service.sendTo(
                    1L, "https://web.push.apple.com/other", PushService.Notification.test()))
        .isInstanceOf(NoSuchElementException.class);
  }

  @Test
  void unsubscribingSomethingAlreadyGoneIsA404() {
    when(repo.findByIdAndUserId(9L, 1L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.unsubscribe(1L, 9L))
        .isInstanceOf(NoSuchElementException.class);
  }

  /** The endpoint is the browser's, not the account's: whoever subscribes it last owns the row. */
  @Test
  void aBrowserThatSubscribesAsTheOtherAccountTakesItsRowAlong() {
    PushSubscription theirs = subscribed();
    when(repo.findByEndpoint(ENDPOINT)).thenReturn(Optional.of(theirs));
    when(repo.countByUserId(2L)).thenReturn(1L);

    service.subscribe(2L, ENDPOINT, p256dh, auth, "iPhone");

    assertThat(theirs.getUserId()).isEqualTo(2L);
    verify(repo).save(theirs);
  }

  @Test
  void theTestCannotReachAnotherAccountsDevice() {
    PushSubscription theirs = subscribed(); // account 1's
    when(repo.findByEndpoint(ENDPOINT)).thenReturn(Optional.of(theirs));

    assertThatThrownBy(() -> service.sendTo(2L, ENDPOINT, PushService.Notification.test()))
        .isInstanceOf(NoSuchElementException.class);
    assertThat(transport.endpoints).isEmpty();
  }

  @Test
  void theTestWithNoEndpointGoesToThatAccountsDevicesAndNotTheOwners() {
    when(repo.findAllByUserIdOrderByCreatedAtAsc(2L)).thenReturn(List.of());

    assertThat(service.sendTo(2L, PushService.Notification.test()))
        .isEqualTo(new PushService.Outcome(0, 0, 0));
    assertThat(transport.endpoints).isEmpty();
    verify(repo, never()).findAllByRole(any());
  }

  /** Endpoints are capabilities: the log shows the push service and a tail, never the whole URL. */
  @Test
  void logsNeverCarryAWholeEndpoint() {
    assertThat(PushService.abbreviate(ENDPOINT)).isEqualTo("https://web.push.apple.com/…fXkqLm2P");
    assertThat(PushService.abbreviate(ENDPOINT)).doesNotContain("QGb5zW0Z");
  }
}
