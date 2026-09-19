package dev.grindtrack.chat.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;

/**
 * The bucket, as the chat needs it: put a thing, get a short-lived link to it, delete it.
 *
 * <p>An interface so the service is tested against a map and the real one, {@link S3MediaStore}, is
 * exercised by the first upload against the real bucket, which is the only test of an object store
 * that means anything.
 */
public interface MediaStore {

  boolean configured();

  /**
   * @param size known up front, so the upload is one request with a content length rather than a
   *     chunked stream some S3 services refuse
   */
  void put(String key, String contentType, InputStream body, long size) throws IOException;

  /** A signed link the browser can follow without credentials, for {@code ttl}. */
  URI presignGet(String key, Duration ttl);

  void delete(String key);
}
