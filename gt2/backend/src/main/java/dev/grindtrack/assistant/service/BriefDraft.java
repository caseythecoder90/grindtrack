package dev.grindtrack.assistant.service;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * The morning brief: three short pieces, read once, before the day is decided.
 *
 * <p>Deliberately not a plan and not a review. It says what is already true about today — what is
 * booked, what is nearest on the plan, what yesterday's own notes said — and offers one thing. A
 * brief that tried to schedule the day would be the planner with worse information, and one that
 * graded yesterday would be the review a week early.
 */
public record BriefDraft(
    @JsonPropertyDescription(
            "One sentence, the shape of today: what is booked and what it is for. Plain text.")
        String headline,
    @JsonPropertyDescription(
            "Two or three sentences. Where the nearest plan item stands against its target date,"
                + " what yesterday's notes said (quote a phrase if there is one), and anything"
                + " due — upkeep or a todo. Numbers only from the context. Plain text.")
        String today,
    @JsonPropertyDescription(
            "One concrete suggestion for the morning block, named by plan item, and why it is"
                + " the one. One or two sentences. Plain text.")
        String suggestion,
    @JsonPropertyDescription(
            "One or two sentences to start the day on, before anything else. Grounded in the"
                + " three things that matter: the plan, his wife and family, his recovery — the"
                + " context carries the day count when it is known; use it. Warm and direct, no"
                + " platitudes, no exclamation marks, nothing that reads like a poster. Plain"
                + " text.")
        String motivation) {}
