package dev.grindtrack.assistant.service;

/** The brief's seam to a model, for the reason every other one here exists: tests spend nothing. */
public interface BriefModel {

  boolean configured();

  /** Draft this morning's brief from the assembled context. */
  DraftedBrief draft(String contextJson);

  record DraftedBrief(BriefDraft draft, String model, long inputTokens, long outputTokens) {}
}
