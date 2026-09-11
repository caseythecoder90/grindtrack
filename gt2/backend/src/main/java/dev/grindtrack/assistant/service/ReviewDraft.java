package dev.grindtrack.assistant.service;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * A drafted weekly review, field-for-field the shape of the review form on the week tab.
 *
 * <p>That correspondence is the contract: accepting a draft is copying these six fields into the
 * form, so this record must never grow a field the form cannot hold. The model is made to fill
 * exactly this shape by structured output — the schema is derived from this record, and the
 * response parses into it or the call fails. No regex over prose, no "mostly JSON".
 */
public record ReviewDraft(
    @JsonPropertyDescription(
            "Three to five sentences on what actually happened this week, written in first person,"
                + " grounded in the logged hours and sessions. Lead with the number that matters"
                + " most this week.")
        String summary,
    @JsonPropertyDescription(
            "What went well, concretely: named plan items, streaks kept, hours banked. First"
                + " person, one to three sentences.")
        String wins,
    @JsonPropertyDescription(
            "What got in the way, taken from the logged blockers and the gaps between planned and"
                + " actual. One to three sentences. Empty string if the week genuinely had none.")
        String blockers,
    @JsonPropertyDescription(
            "Changes to make to the plan or the routine, each one actionable. Derived from the"
                + " gaps, not invented. One to three sentences.")
        String adjustments,
    @JsonPropertyDescription(
            "What next week is for: the one or two in-flight or upcoming plan items that deserve"
                + " the study blocks, by name, with anything date-driven called out.")
        String nextFocus,
    @JsonPropertyDescription(
            "Whether this week kept pace with the quarter. Honest: hours at or near target with"
                + " the priority items moving is true; a week well short of target is false, even"
                + " if it stings.")
        boolean onTrack) {}
