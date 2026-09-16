package dev.grindtrack.push.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;

/** {@link PushTransport} over the JDK's HTTP client. Ten seconds each way; no retries. */
@Component
public class HttpPushTransport implements PushTransport {

  private static final Duration TIMEOUT = Duration.ofSeconds(10);

  private final HttpClient client =
      HttpClient.newBuilder()
          .connectTimeout(TIMEOUT)
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  /** Enough of an answer to read in a log; Apple's reasons are one short JSON line. */
  private static final int BODY_CHARS = 300;

  @Override
  public Reply send(String endpoint, Map<String, String> headers, byte[] body) throws IOException {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create(endpoint))
            .timeout(TIMEOUT)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body));
    headers.forEach(request::header);
    try {
      HttpResponse<String> response =
          client.send(request.build(), HttpResponse.BodyHandlers.ofString());
      String answer = response.body() == null ? "" : response.body().strip();
      if (answer.length() > BODY_CHARS) {
        answer = answer.substring(0, BODY_CHARS) + "…";
      }
      return new Reply(response.statusCode(), answer);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("interrupted while sending a push", e);
    }
  }
}
