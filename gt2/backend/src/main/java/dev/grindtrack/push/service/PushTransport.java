package dev.grindtrack.push.service;

import java.io.IOException;
import java.util.Map;

/**
 * The one outbound call: an encrypted body to a push service, with its headers.
 *
 * <p>An interface so the service can be tested against what it hands over and how it treats the
 * status that comes back, without a network. Production is {@link HttpPushTransport}.
 */
public interface PushTransport {

  /**
   * @return the HTTP status the push service answered with
   * @throws IOException when it did not answer: connection refused, timed out, reset
   */
  int send(String endpoint, Map<String, String> headers, byte[] body) throws IOException;
}
