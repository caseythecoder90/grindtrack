package dev.grindtrack.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.grindtrack.auth.domain.Role;
import dev.grindtrack.auth.security.SignedIn;
import dev.grindtrack.chat.domain.ChatMedia;
import dev.grindtrack.chat.domain.ChatMediaRepository;
import dev.grindtrack.chat.domain.ChatMessageRepository;
import dev.grindtrack.chat.domain.MediaKind;
import dev.grindtrack.config.MediaProperties;
import dev.grindtrack.web.BadRequestException;
import dev.grindtrack.web.ServiceOffException;
import dev.grindtrack.web.UpstreamException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.time.Year;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.unit.DataSize;

/** An upload lands under a fresh key with its poster beside it; the wrong thing is a sentence. */
class MediaServiceTest {

  private static final SignedIn CASEY = new SignedIn(1L, "casey", Role.OWNER);

  /** The bucket as a map. */
  static final class FakeStore implements MediaStore {
    boolean on = true;
    final Map<String, byte[]> objects = new LinkedHashMap<>();
    final List<String> deleted = new ArrayList<>();
    boolean refuse;

    @Override
    public boolean configured() {
      return on;
    }

    @Override
    public void put(String key, String contentType, InputStream body, long size)
        throws IOException {
      if (refuse) {
        throw new IOException("the bucket refused the upload: 403");
      }
      objects.put(key, body.readAllBytes());
    }

    @Override
    public URI presignGet(String key, Duration ttl) {
      return URI.create("https://b.nbg1.your-objectstorage.com/" + key + "?X-Amz-Signature=x");
    }

    @Override
    public void delete(String key) {
      if (refuse) {
        throw new IllegalStateException("bucket down");
      }
      deleted.add(key);
      objects.remove(key);
    }
  }

  private ChatMediaRepository repo;
  private ChatMessageRepository messages;
  private FakeStore store;
  private MediaService service;

  @BeforeEach
  void setUp() {
    repo = mock(ChatMediaRepository.class);
    messages = mock(ChatMessageRepository.class);
    when(repo.save(any()))
        .thenAnswer(
            inv -> {
              ChatMedia row = inv.getArgument(0);
              if (row.getId() == null) {
                ReflectionTestUtils.setField(row, "id", 5L);
              }
              return row;
            });
    store = new FakeStore();
    service =
        new MediaService(
            repo,
            messages,
            store,
            new MediaProperties(
                "https://nbg1.your-objectstorage.com",
                "nbg1",
                "b",
                "k",
                "s",
                DataSize.ofMegabytes(100)));
  }

  private static MockMultipartFile photo(int bytes) {
    return new MockMultipartFile("file", "cat.jpg", "image/jpeg", new byte[bytes]);
  }

  private static MockMultipartFile poster() {
    return new MockMultipartFile("poster", "poster.jpg", "image/jpeg", new byte[64]);
  }

  private static ChatMedia row(long id, MediaKind kind, String key, String posterKey) {
    ChatMedia m =
        new ChatMedia(
            1L,
            kind,
            key,
            posterKey,
            kind == MediaKind.IMAGE ? "image/jpeg" : "video/mp4",
            9,
            1,
            1,
            null);
    ReflectionTestUtils.setField(m, "id", id);
    return m;
  }

  @Test
  void statusSaysWhetherThereIsABucketAndTheLimit() {
    assertThat(service.status()).isEqualTo(new MediaService.Status(true, 100L * 1024 * 1024));
    store.on = false;
    assertThat(service.status().configured()).isFalse();
  }

  @Test
  void aPhotoGoesInUnderAFreshKeyWithItsPosterBesideIt() {
    MediaService.MediaView view = service.upload(CASEY, photo(1000), poster(), 1600, 1200, null);

    assertThat(view.id()).isEqualTo(5L);
    assertThat(view.kind()).isEqualTo(MediaKind.IMAGE);
    assertThat(view.contentType()).isEqualTo("image/jpeg");
    assertThat(view.bytes()).isEqualTo(1000);
    assertThat(view.width()).isEqualTo(1600);
    assertThat(view.hasPoster()).isTrue();
    assertThat(view.sticker()).isFalse();

    assertThat(store.objects).hasSize(2);
    List<String> keys = new ArrayList<>(store.objects.keySet());
    String year = Year.now().toString();
    assertThat(keys.get(0)).startsWith("chat/" + year + "/").endsWith(".jpg");
    assertThat(keys.get(1)).isEqualTo(keys.get(0).replace(".jpg", "-poster.jpg"));

    ArgumentCaptor<ChatMedia> saved = ArgumentCaptor.forClass(ChatMedia.class);
    verify(repo).save(saved.capture());
    assertThat(saved.getValue().getOwnerId()).isEqualTo(1L);
    assertThat(saved.getValue().getObjectKey()).isEqualTo(keys.get(0));
    assertThat(saved.getValue().getPosterKey()).isEqualTo(keys.get(1));
  }

  @Test
  void aClipKeepsItsExtensionAndItsLengthAndNeedsNoPoster() {
    MockMultipartFile clip = new MockMultipartFile("file", "a.mov", "video/quicktime", new byte[9]);

    MediaService.MediaView view = service.upload(CASEY, clip, null, 1920, 1080, 4200);

    assertThat(view.kind()).isEqualTo(MediaKind.VIDEO);
    assertThat(view.durationMs()).isEqualTo(4200);
    assertThat(view.hasPoster()).isFalse();
    assertThat(store.objects.keySet()).singleElement().asString().endsWith(".mov");
  }

  @Test
  void theWrongKindOfFileTooBigOrNothingIsA400BeforeTheBucketIsTouched() {
    assertThatThrownBy(
            () ->
                service.upload(
                    CASEY,
                    new MockMultipartFile("file", "x.pdf", "application/pdf", new byte[3]),
                    null,
                    null,
                    null,
                    null))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("JPEG");
    assertThatThrownBy(
            () -> service.upload(CASEY, photo(101 * 1024 * 1024), null, null, null, null))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("100 MB");
    assertThatThrownBy(() -> service.upload(CASEY, photo(0), null, null, null, null))
        .isInstanceOf(BadRequestException.class);
    assertThatThrownBy(
            () ->
                service.upload(
                    CASEY,
                    photo(10),
                    new MockMultipartFile("poster", "p.png", "image/png", new byte[3]),
                    null,
                    null,
                    null))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("poster");
    assertThat(store.objects).isEmpty();
    verify(repo, never()).save(any());
  }

  @Test
  void offIsA503AndABucketThatRefusesIsA502WithNoRow() {
    store.on = false;
    assertThatThrownBy(() -> service.upload(CASEY, photo(10), null, null, null, null))
        .isInstanceOf(ServiceOffException.class)
        .hasMessageContaining("MEDIA_S3_BUCKET");

    store.on = true;
    store.refuse = true;
    assertThatThrownBy(() -> service.upload(CASEY, photo(10), null, null, null, null))
        .isInstanceOf(UpstreamException.class)
        .hasMessageContaining("403");
    verify(repo, never()).save(any());
  }

  @Test
  void theLinkIsSignedForTheObjectOrItsPosterAndFallsBackWithoutOne() {
    when(repo.findById(1L))
        .thenReturn(
            Optional.of(row(1L, MediaKind.IMAGE, "chat/2026/a.jpg", "chat/2026/a-poster.jpg")));
    when(repo.findById(2L))
        .thenReturn(Optional.of(row(2L, MediaKind.VIDEO, "chat/2026/b.mp4", null)));
    when(repo.findById(9L)).thenReturn(Optional.empty());

    assertThat(service.link(1L, false).getPath()).isEqualTo("/chat/2026/a.jpg");
    assertThat(service.link(1L, true).getPath()).isEqualTo("/chat/2026/a-poster.jpg");
    assertThat(service.link(2L, true).getPath()).isEqualTo("/chat/2026/b.mp4");
    assertThatThrownBy(() -> service.link(9L, false)).isInstanceOf(NoSuchElementException.class);
  }

  @Test
  void removingTakesBothObjectsAndTheRowAndShrugsAtABucketError() {
    ChatMedia picture = row(1L, MediaKind.IMAGE, "chat/2026/a.jpg", "chat/2026/a-poster.jpg");

    service.remove(picture);
    assertThat(store.deleted).containsExactly("chat/2026/a.jpg", "chat/2026/a-poster.jpg");
    verify(repo).delete(picture);

    store.refuse = true;
    service.remove(picture);
    verify(repo, org.mockito.Mockito.times(2)).delete(picture);
  }

  @Test
  void aPictureGoesIntoTheTrayAndAClipDoesNot() {
    ChatMedia picture = row(1L, MediaKind.IMAGE, "chat/2026/a.jpg", null);
    when(repo.findById(1L)).thenReturn(Optional.of(picture));
    when(repo.findById(2L))
        .thenReturn(Optional.of(row(2L, MediaKind.VIDEO, "chat/2026/b.mp4", null)));
    when(repo.findAllByStickerTrueOrderByIdDesc()).thenReturn(List.of(picture));

    MediaService.MediaView kept = service.setSticker(1L, true);
    assertThat(kept.sticker()).isTrue();
    assertThat(picture.isSticker()).isTrue();
    verify(repo).save(picture);
    assertThat(service.stickers()).extracting(MediaService.MediaView::id).containsExactly(1L);

    assertThatThrownBy(() -> service.setSticker(2L, true))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("picture");
    assertThatThrownBy(() -> service.setSticker(9L, true))
        .isInstanceOf(NoSuchElementException.class);
  }

  @Test
  void outOfTheTrayAPictureNoMessageShowsIsRemovedAndOneStillShownStays() {
    ChatMedia shown = row(1L, MediaKind.IMAGE, "chat/2026/a.jpg", null);
    shown.setSticker(true);
    ChatMedia orphan = row(2L, MediaKind.IMAGE, "chat/2026/b.jpg", null);
    orphan.setSticker(true);
    when(repo.findById(1L)).thenReturn(Optional.of(shown));
    when(repo.findById(2L)).thenReturn(Optional.of(orphan));
    when(messages.existsByMediaId(1L)).thenReturn(true);
    when(messages.existsByMediaId(2L)).thenReturn(false);

    assertThat(service.setSticker(1L, false).sticker()).isFalse();
    verify(repo, never()).delete(shown);
    assertThat(store.deleted).isEmpty();

    assertThat(service.setSticker(2L, false).sticker()).isFalse();
    verify(repo).delete(orphan);
    assertThat(store.deleted).containsExactly("chat/2026/b.jpg");
  }
}
