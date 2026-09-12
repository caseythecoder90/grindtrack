package dev.grindtrack.speech.api;

import dev.grindtrack.config.SpeechProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Whether the mic button should exist. The socket itself is {@link SpeechSocketHandler}. */
@RestController
@RequestMapping("/api/speech")
public class SpeechController {

  private final SpeechProperties props;

  public SpeechController(SpeechProperties props) {
    this.props = props;
  }

  @GetMapping("/status")
  public Status status() {
    return new Status(props.configured(), props.configured() ? props.model() : null);
  }

  public record Status(boolean configured, String model) {}
}
