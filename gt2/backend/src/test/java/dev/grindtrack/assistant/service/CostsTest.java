package dev.grindtrack.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Each call priced at the rate of the model that made it. */
class CostsTest {

  @Test
  void theRateFollowsTheModelName() {
    assertThat(Costs.rate("claude-opus-5-5")).isEqualTo(Costs.OPUS_5_5);
    assertThat(Costs.rate("claude-opus-5")).isEqualTo(Costs.OPUS_5);
    assertThat(Costs.rate("claude-sonnet-5")).isEqualTo(Costs.SONNET_5);
    assertThat(Costs.rate("claude-haiku-4-5")).isEqualTo(Costs.HAIKU_4_5);
    // An id nobody has heard of is priced as the dearest it could be, never as free.
    assertThat(Costs.rate("claude-something-new")).isEqualTo(Costs.OPUS_5);
    assertThat(Costs.rate(null)).isEqualTo(Costs.OPUS_5);
  }

  @Test
  void aMillionTokensEachWayCostsTheListPrice() {
    assertThat(Costs.usd("claude-opus-5-5", 1_000_000, 1_000_000, 0, 0)).isEqualTo(24.0);
    assertThat(Costs.usd("claude-opus-5", 1_000_000, 1_000_000, 0, 0)).isEqualTo(30.0);
    assertThat(Costs.usd("claude-sonnet-5", 1_000_000, 1_000_000, 0, 0)).isEqualTo(12.0);
    // Cache: a write is a quarter over the input rate; a read on Opus 5.5 is a twentieth of it.
    assertThat(Costs.usd("claude-opus-5-5", 0, 0, 1_000_000, 1_000_000)).isEqualTo(5.2);
    assertThat(Costs.cacheSaving("claude-opus-5-5", 1_000_000, 1_000_000)).isEqualTo(2.8);
  }
}
