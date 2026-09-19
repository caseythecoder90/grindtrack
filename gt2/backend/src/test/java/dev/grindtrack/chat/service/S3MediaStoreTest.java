package dev.grindtrack.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.grindtrack.config.MediaProperties;
import dev.grindtrack.web.ServiceOffException;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

/**
 * Off is a state; and a signed link — pure computation, no network — is made for the bucket as a
 * subdomain of the Hetzner endpoint, with a version-4 signature. That is the one thing about the
 * real store that can be checked without the real bucket.
 */
class S3MediaStoreTest {

  private static MediaProperties props(String endpoint, String bucket, String key) {
    return new MediaProperties(endpoint, "nbg1", bucket, key, "secret", DataSize.ofMegabytes(100));
  }

  @Test
  void withoutABucketEverythingIsA503WithTheFourNames() {
    S3MediaStore off = new S3MediaStore(props("", "", ""));

    assertThat(off.configured()).isFalse();
    assertThatThrownBy(() -> off.put("k", "image/jpeg", new ByteArrayInputStream(new byte[1]), 1))
        .isInstanceOf(ServiceOffException.class)
        .hasMessageContaining("MEDIA_S3_ENDPOINT")
        .hasMessageContaining("MEDIA_S3_SECRET_KEY");
    assertThatThrownBy(() -> off.presignGet("k", Duration.ofMinutes(1)))
        .isInstanceOf(ServiceOffException.class);
    assertThatThrownBy(() -> off.delete("k")).isInstanceOf(ServiceOffException.class);
  }

  @Test
  void aSignedLinkNamesTheBucketAsASubdomainOfTheEndpoint() {
    S3MediaStore store =
        new S3MediaStore(props("https://nbg1.your-objectstorage.com", "grindtrack-media", "AKIA"));

    URI link = store.presignGet("chat/2026/x.jpg", Duration.ofMinutes(10));

    assertThat(store.configured()).isTrue();
    assertThat(link.getScheme()).isEqualTo("https");
    assertThat(link.getHost()).isEqualTo("grindtrack-media.nbg1.your-objectstorage.com");
    assertThat(link.getPath()).isEqualTo("/chat/2026/x.jpg");
    assertThat(link.getQuery())
        .contains("X-Amz-Algorithm=AWS4-HMAC-SHA256")
        .contains("X-Amz-Expires=600")
        .contains("X-Amz-Signature=")
        .contains("nbg1");
    store.close();
  }
}
