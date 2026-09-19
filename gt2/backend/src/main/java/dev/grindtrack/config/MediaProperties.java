package dev.grindtrack.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * The {@code grindtrack.media.*} block: where the chat's photos and video live.
 *
 * <p>An S3-compatible bucket — Hetzner Object Storage in the cluster's own location — reached with
 * the AWS SDK, because Hetzner has no SDK of its own and does not need one: the S3 API is the one
 * every object store speaks. Absent means off, like the assistant without its key: the status
 * endpoint says so, the attach button is not rendered, and an upload answers 503 with a sentence.
 *
 * @param endpoint {@code https://nbg1.your-objectstorage.com}; the bucket is addressed as a
 *     subdomain of it
 * @param region the location name, which is what the S3 signature is made for; Hetzner accepts it
 * @param bucket private; nothing in it is reachable without a signed link
 * @param accessKey from the Hetzner console, via the Kubernetes secret
 * @param secretKey likewise
 * @param maxSize one photo or clip; the multipart limits in the same file are set to match
 */
@ConfigurationProperties(prefix = "grindtrack.media")
public record MediaProperties(
    String endpoint,
    String region,
    String bucket,
    String accessKey,
    String secretKey,
    DataSize maxSize) {

  public boolean configured() {
    return present(endpoint) && present(bucket) && present(accessKey) && present(secretKey);
  }

  private static boolean present(String value) {
    return value != null && !value.isBlank();
  }
}
