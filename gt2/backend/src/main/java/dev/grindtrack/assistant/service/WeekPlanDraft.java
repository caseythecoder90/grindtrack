package dev.grindtrack.assistant.service;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * A proposed week of study blocks, and the reasoning behind it.
 *
 * <p>Every block names the plan item it is for, because a morning booked against nothing is how the
 * planned-versus-actual figure stopped meaning anything in the first place. The blocks are a
 * proposal: accepting them is a separate click that writes to the calendar, and until then this
 * lives only in assistant_reports.
 */
public record WeekPlanDraft(
    @JsonPropertyDescription(
            "Two or three sentences on the shape of the week and why it is this shape: what is"
                + " nearest on the plan, what slipped last week, what the hours add up to.")
        String rationale,
    @JsonPropertyDescription("The study blocks to book, in date order. Between three and six.")
        List<Block> blocks) {

  public record Block(
      @JsonPropertyDescription("YYYY-MM-DD, inside the week being planned.") String date,
      @JsonPropertyDescription("24-hour HH:mm. Casey studies before work, so mornings.")
          String startTime,
      @JsonPropertyDescription("24-hour HH:mm, after startTime. Blocks run one to three hours.")
          String endTime,
      @JsonPropertyDescription(
              "The block's title, naming what is being worked on — it becomes the calendar entry.")
          String title,
      @JsonPropertyDescription(
              "The id of the plan item this block is for, from the plan in the context. Use an"
                  + " id that exists; never invent one.")
          Long planItemId) {}
}
