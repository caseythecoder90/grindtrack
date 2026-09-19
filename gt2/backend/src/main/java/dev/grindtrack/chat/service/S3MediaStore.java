package dev.grindtrack.chat.service;

import dev.grindtrack.config.MediaProperties;
import dev.grindtrack.web.ServiceOffException;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * The bucket, through the AWS SDK pointed at Hetzner.
 *
 * <p>Three settings make an S3 client talk to a non-Amazon S3: the endpoint override, the location
 * name as the signing region, and virtual-hosted addressing (the bucket as a subdomain), which is
 * the style Hetzner serves. The fourth is newer: since early 2025 the SDK adds a CRC32 integrity
 * checksum to every upload by default, and some S3-compatible services refuse the request it
 * produces, so both checksum settings are turned down to "when the API requires one". If the first
 * real upload fails with a signature or checksum error, these four lines are where to look.
 *
 * <p>Off is a state: with no bucket configured there is no client, {@link #configured} says so, and
 * every other call answers 503 with the names of the four variables.
 */
@Component
public class S3MediaStore implements MediaStore {

  private final String bucket;
  private final S3Client s3;
  private final S3Presigner presigner;

  public S3MediaStore(MediaProperties props) {
    this.bucket = props.bucket();
    if (!props.configured()) {
      this.s3 = null;
      this.presigner = null;
      return;
    }
    URI endpoint = URI.create(props.endpoint());
    Region region = Region.of(props.region());
    StaticCredentialsProvider credentials =
        StaticCredentialsProvider.create(
            AwsBasicCredentials.create(props.accessKey(), props.secretKey()));
    S3Configuration addressing = S3Configuration.builder().pathStyleAccessEnabled(false).build();
    this.s3 =
        S3Client.builder()
            .endpointOverride(endpoint)
            .region(region)
            .credentialsProvider(credentials)
            .serviceConfiguration(addressing)
            .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
            .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
            .build();
    this.presigner =
        S3Presigner.builder()
            .endpointOverride(endpoint)
            .region(region)
            .credentialsProvider(credentials)
            .serviceConfiguration(addressing)
            .build();
  }

  @Override
  public boolean configured() {
    return s3 != null;
  }

  @Override
  public void put(String key, String contentType, InputStream body, long size) throws IOException {
    requireOn();
    try {
      s3.putObject(
          PutObjectRequest.builder()
              .bucket(bucket)
              .key(key)
              .contentType(contentType)
              .contentLength(size)
              .build(),
          RequestBody.fromInputStream(body, size));
    } catch (SdkException e) {
      throw new IOException("the bucket refused the upload: " + e.getMessage(), e);
    }
  }

  @Override
  public URI presignGet(String key, Duration ttl) {
    requireOn();
    try {
      return presigner
          .presignGetObject(
              GetObjectPresignRequest.builder()
                  .signatureDuration(ttl)
                  .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                  .build())
          .url()
          .toURI();
    } catch (URISyntaxException e) {
      throw new IllegalStateException("the presigned link is not a URI", e);
    }
  }

  @Override
  public void delete(String key) {
    requireOn();
    s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
  }

  private void requireOn() {
    if (s3 == null) {
      throw new ServiceOffException(
          "media is off — set MEDIA_S3_ENDPOINT, MEDIA_S3_BUCKET, MEDIA_S3_ACCESS_KEY and"
              + " MEDIA_S3_SECRET_KEY on the deployment");
    }
  }

  @PreDestroy
  void close() {
    if (s3 != null) {
      s3.close();
      presigner.close();
    }
  }
}
