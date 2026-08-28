package dev.grindtrack.relationship.domain;

/**
 * Where an idea has got to.
 *
 * <p>DONE exists so the list stays short. An idea you acted on links to the moment it became, which
 * is the loop that keeps this from turning into a graveyard of things already given.
 */
public enum IdeaStatus {
  IDEA,
  PLANNED,
  DONE
}
