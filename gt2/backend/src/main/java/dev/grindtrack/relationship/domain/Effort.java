package dev.grindtrack.relationship.domain;

/**
 * How much doing this actually takes.
 *
 * <p>The deciding factor on an ordinary evening is rarely the idea, it is the effort. Recording it
 * lets the list lead with the two-minute options, which are the ones that get done.
 */
public enum Effort {
  /** Minutes. A note, a text, picking something up on the way home. */
  SMALL,
  /** An evening. */
  MEDIUM,
  /** Planning, money, or a day off. */
  BIG
}
