package dev.grindtrack.chat.service;

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
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Photos and video: into the bucket, out by a signed link, gone with their message. And the sticker
 * tray: pictures kept to send again.
 *
 * <p>An upload is its own request, before the message: the phone sends the file (and the small
 * poster it made) and gets back an id, then sends the message with that id. Two requests rather
 * than one, so a failed message send after a good upload is retried without uploading again, and so
 * the words never wait on a hundred megabytes.
 *
 * <p>Nothing in the bucket is public. A page shows a picture through {@code GET
 * /api/chat/media/{id}}, which answers a 302 to a link signed for ten minutes; the browser follows
 * it, the pod never carries the bytes back, and a link that has expired is just a fresh 302 the
 * next time the image is asked for.
 *
 * <p>A sticker is any picture in the room that either of you kept — the tray is shared, because the
 * room is. Sending one is a message with that picture's id and no upload; unsending the message
 * leaves the sticker; taking it out of the tray removes it from the bucket only once no message
 * shows it.
 */
@Service
public class MediaService {

  private static final Logger log = LoggerFactory.getLogger(MediaService.class);

  static final Map<String, String> IMAGE_TYPES =
      Map.of("image/jpeg", ".jpg", "image/png", ".png", "image/webp", ".webp", "image/gif", ".gif");
  static final Map<String, String> VIDEO_TYPES =
      Map.of("video/mp4", ".mp4", "video/quicktime", ".mov", "video/webm", ".webm");
  static final long MAX_POSTER_BYTES = 2L * 1024 * 1024;
  static final long DEFAULT_MAX_BYTES = 100L * 1024 * 1024;
  static final Duration LINK_TTL = Duration.ofMinutes(10);
  static final String OFF =
      "media is off — set MEDIA_S3_ENDPOINT, MEDIA_S3_BUCKET, MEDIA_S3_ACCESS_KEY and"
          + " MEDIA_S3_SECRET_KEY on the deployment";

  private final ChatMediaRepository media;
  private final ChatMessageRepository messages;
  private final MediaStore store;
  private final MediaProperties props;

  public MediaService(
      ChatMediaRepository media,
      ChatMessageRepository messages,
      MediaStore store,
      MediaProperties props) {
    this.media = media;
    this.messages = messages;
    this.store = store;
    this.props = props;
  }

  /**
   * Whether there is a bucket, whether it answers, and how big one upload may be — what the attach
   * button reads, and what the go-live checklist reads as proof. {@code bucket} is {@code "ok"}
   * only after a {@code HeadBucket} with the keys; configuration alone proved nothing the first
   * time.
   */
  public Status status() {
    if (!store.configured()) {
      return new Status(false, maxBytes(), null);
    }
    return new Status(true, maxBytes(), store.check().orElse("ok"));
  }

  /** One line at boot with the same answer, so the runbook's grep has something to find. */
  @EventListener(ApplicationReadyEvent.class)
  public void logBucket() {
    if (!store.configured()) {
      log.info("Media: off — no bucket configured");
      return;
    }
    store
        .check()
        .ifPresentOrElse(
            problem ->
                log.warn("Media: bucket {} at {} — {}", props.bucket(), props.endpoint(), problem),
            () -> log.info("Media: bucket {} at {} answers", props.bucket(), props.endpoint()));
  }

  /**
   * The upload: the file to the bucket under a fresh key, the poster beside it, a row for both. The
   * poster is best effort: a refused thumbnail is a warning in the log and a row without one — the
   * thread shows the picture itself — not a lost photo. The first evening, the bucket took two
   * pictures and refused their thumbnails, which failed both uploads and left two orphan objects
   * behind.
   *
   * @param poster a JPEG under two megabytes, or null; the thumbnail or the video's frame
   * @throws BadRequestException for a type the chat does not take, or a file over the limit
   * @throws ServiceOffException with no bucket configured
   * @throws UpstreamException when the bucket refuses the bytes
   */
  public MediaView upload(
      SignedIn me,
      MultipartFile file,
      MultipartFile poster,
      Integer width,
      Integer height,
      Integer durationMs) {
    if (!store.configured()) {
      throw new ServiceOffException(OFF);
    }
    if (file == null || file.isEmpty()) {
      throw new BadRequestException("a photo or a video is needed");
    }
    String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
    MediaKind kind = kindOf(contentType);
    if (file.getSize() > maxBytes()) {
      throw new BadRequestException(
          "that is larger than the " + (maxBytes() / (1024 * 1024)) + " MB the chat takes");
    }
    boolean hasPoster = poster != null && !poster.isEmpty();
    if (hasPoster
        && (!"image/jpeg".equalsIgnoreCase(poster.getContentType())
            || poster.getSize() > MAX_POSTER_BYTES)) {
      throw new BadRequestException("the poster must be a JPEG under 2 MB");
    }
    String stem = "chat/" + Year.now() + "/" + UUID.randomUUID();
    String key = stem + extensionFor(kind, contentType);
    String posterKey = hasPoster ? stem + "-poster.jpg" : null;
    try {
      put(key, contentType, file);
    } catch (IOException e) {
      log.warn("Media upload by account {} failed: {}", me.id(), e.getMessage());
      throw new UpstreamException("could not store that: " + e.getMessage());
    }
    if (hasPoster) {
      try {
        put(posterKey, "image/jpeg", poster);
      } catch (IOException e) {
        log.warn(
            "Media poster for account {}'s upload was refused; keeping the picture without one: {}",
            me.id(),
            e.getMessage());
        posterKey = null;
      }
    }
    ChatMedia row =
        media.save(
            new ChatMedia(
                me.id(),
                kind,
                key,
                posterKey,
                contentType,
                file.getSize(),
                width,
                height,
                durationMs));
    log.info(
        "Media {} uploaded by account {}: {} {} bytes", row.getId(), me.id(), kind, file.getSize());
    return MediaView.of(row);
  }

  private void put(String key, String contentType, MultipartFile part) throws IOException {
    try (InputStream body = part.getInputStream()) {
      store.put(key, contentType, body, part.getSize());
    }
  }

  /**
   * A signed link to the object, or to its poster (the object itself when it has none), good for
   * ten minutes.
   *
   * @throws NoSuchElementException for no such upload — a 404
   */
  public URI link(long id, boolean poster) {
    ChatMedia row = find(id).orElseThrow(() -> new NoSuchElementException("media " + id));
    if (!store.configured()) {
      throw new ServiceOffException(OFF);
    }
    String key = poster && row.getPosterKey() != null ? row.getPosterKey() : row.getObjectKey();
    return store.presignGet(key, LINK_TTL);
  }

  public Optional<ChatMedia> find(long id) {
    return media.findById(id);
  }

  public List<ChatMedia> findAll(Iterable<Long> ids) {
    return media.findAllById(ids);
  }

  /** The tray, newest first. Shared by both, like the room. */
  public List<MediaView> stickers() {
    return media.findAllByStickerTrueOrderByIdDesc().stream().map(MediaView::of).toList();
  }

  /**
   * Into the tray, or out of it. Only a picture can be a sticker. Taken out, a picture no message
   * shows any more is removed from the bucket too; one still on a message stays there.
   *
   * @throws NoSuchElementException for no such upload — a 404
   * @throws BadRequestException for a clip
   */
  public MediaView setSticker(long id, boolean on) {
    ChatMedia row = find(id).orElseThrow(() -> new NoSuchElementException("media " + id));
    if (on && row.getKind() != MediaKind.IMAGE) {
      throw new BadRequestException("only a picture can be a sticker");
    }
    if (row.isSticker() != on) {
      row.setSticker(on);
      row = media.save(row);
    }
    MediaView view = MediaView.of(row);
    if (!on && !messages.existsByMediaId(id)) {
      remove(row);
    }
    return view;
  }

  /**
   * Gone with its message: the objects, then the row. The bucket side is best effort — a stranded
   * object is a log line and a few kilobytes, not an unsend that did not happen.
   */
  public void remove(ChatMedia row) {
    try {
      store.delete(row.getObjectKey());
      if (row.getPosterKey() != null) {
        store.delete(row.getPosterKey());
      }
    } catch (RuntimeException e) {
      log.warn("Could not delete media {} from the bucket: {}", row.getId(), e.getMessage());
    }
    media.delete(row);
  }

  private long maxBytes() {
    return props.maxSize() == null ? DEFAULT_MAX_BYTES : props.maxSize().toBytes();
  }

  static MediaKind kindOf(String contentType) {
    if (IMAGE_TYPES.containsKey(contentType)) {
      return MediaKind.IMAGE;
    }
    if (VIDEO_TYPES.containsKey(contentType)) {
      return MediaKind.VIDEO;
    }
    throw new BadRequestException(
        "the chat takes JPEG, PNG, WebP and GIF photos, and MP4, MOV and WebM video");
  }

  static String extensionFor(MediaKind kind, String contentType) {
    return kind == MediaKind.IMAGE ? IMAGE_TYPES.get(contentType) : VIDEO_TYPES.get(contentType);
  }

  /**
   * @param bucket {@code "ok"} once the bucket answered a {@code HeadBucket}; otherwise what it
   *     said (the error code, the status, where to look); {@code null} when media is off
   */
  public record Status(boolean configured, long maxBytes, String bucket) {}

  /**
   * What a message carries about its upload. No link: the page asks {@code /api/chat/media/{id}}
   * (and {@code /poster}) when it wants the bytes, and gets a fresh signed link each time.
   */
  public record MediaView(
      long id,
      MediaKind kind,
      String contentType,
      long bytes,
      Integer width,
      Integer height,
      Integer durationMs,
      boolean hasPoster,
      boolean sticker) {
    public static MediaView of(ChatMedia m) {
      return new MediaView(
          m.getId(),
          m.getKind(),
          m.getContentType(),
          m.getBytes(),
          m.getWidth(),
          m.getHeight(),
          m.getDurationMs(),
          m.getPosterKey() != null,
          m.isSticker());
    }
  }
}
