package dev.grindtrack.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Which weeks get written down, and — the part this exists for — which do not.
 *
 * <p>This used to read the week out of the model's request instead of the tool's answer, so a
 * refused call recorded a draft that was never made. The turn then died on that value long after
 * its answer had been streamed and read, which is the worst moment in the whole turn to fail in:
 * everything has been paid for, the person has the answer in front of them, and the screen shows an
 * error and hands their question back.
 *
 * <p>The distinction is simply that a draft answers in JSON and a refusal answers in English.
 */
class DraftedWeekTest {

  @Test
  void aDraftRecordsItsWeek() {
    String result =
        "{\"weekStart\":\"2026-09-14\",\"rationale\":\"four mornings\",\"blocks\":[],"
            + "\"status\":\"drafted and waiting\"}";

    assertThat(AnthropicChatModel.draftedDate(result, "weekStart")).isEqualTo("2026-09-14");
  }

  /** The model has to work out which day is a Monday, so it will sometimes get it wrong. */
  @Test
  void aRefusalRecordsNothing() {
    assertThat(
            AnthropicChatModel.draftedDate(
                "weekStart must be a Monday; 2026-09-16 is a WEDNESDAY", "weekStart"))
        .isNull();
  }

  @Test
  void anUnreadableDateRecordsNothing() {
    assertThat(
            AnthropicChatModel.draftedDate(
                "a drafted block has an unreadable date: next monday", "weekStart"))
        .isNull();
  }

  /** JSON that parses but is not a draft is still not a draft. */
  @Test
  void jsonWithoutAWeekRecordsNothing() {
    assertThat(AnthropicChatModel.draftedDate("{\"error\":\"the assistant is off\"}", "weekStart"))
        .isNull();
    assertThat(AnthropicChatModel.draftedDate("{\"weekStart\":null}", "weekStart")).isNull();
    assertThat(AnthropicChatModel.draftedDate("{\"weekStart\":\"not a date\"}", "weekStart"))
        .isNull();
    assertThat(AnthropicChatModel.draftedDate("{\"weekStart\":20260914}", "weekStart")).isNull();
  }
}
