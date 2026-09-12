package dev.grindtrack.speech.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.grindtrack.config.SpeechProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Off is a state the browser can read before it renders a mic. */
class SpeechControllerTest {

  private static MockMvc mvc(SpeechProperties props) {
    return MockMvcBuilders.standaloneSetup(new SpeechController(props)).build();
  }

  @Test
  void statusNamesTheModelWhenOnAndSaysOffWithoutNamingOne() throws Exception {
    mvc(new SpeechProperties("sk-test", "gpt-4o-mini-transcribe", "en"))
        .perform(get("/api/speech/status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.configured").value(true))
        .andExpect(jsonPath("$.model").value("gpt-4o-mini-transcribe"));

    mvc(new SpeechProperties("", "gpt-4o-mini-transcribe", "en"))
        .perform(get("/api/speech/status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.configured").value(false))
        .andExpect(jsonPath("$.model").doesNotExist());
  }
}
