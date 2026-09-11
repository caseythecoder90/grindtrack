package dev.grindtrack.assistant.service;

/** The planning seam. Same reason as {@link ReviewModel}: the service must be testable for free. */
public interface WeekPlanModel {

  boolean configured();

  PlannedWeek plan(String contextJson, String weekStart);

  record PlannedWeek(WeekPlanDraft draft, String model, long inputTokens, long outputTokens) {}
}
