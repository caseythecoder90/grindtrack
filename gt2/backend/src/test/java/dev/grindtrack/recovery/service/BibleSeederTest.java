package dev.grindtrack.recovery.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class BibleSeederTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void aLineOfTheResourceBecomesARow() throws Exception {
    Object[] row =
        BibleSeeder.parse(
            mapper.readTree(
                "[\"PSA\", 23, 1, 1, \"Yahweh is my shepherd:\\nI shall lack nothing.\"]"));

    assertThat(row)
        .containsExactly("PSA", 19, 23, 1, true, "Yahweh is my shepherd:\nI shall lack nothing.");
  }

  @Test
  void aBookOutsideTheCanonIsSkipped() throws Exception {
    assertThat(BibleSeeder.parse(mapper.readTree("[\"TOB\", 1, 1, 0, \"x\"]"))).isNull();
  }
}
