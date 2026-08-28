import type { Effort, IdeaKind, MomentKind, ReadingKind } from "../../lib/types";

/** Plain wording. The enum names are for the database; nobody should read them on screen. */
export const MOMENT_LABEL: Record<MomentKind, string> = {
  DATE_NIGHT: "date night",
  NOTE_LEFT: "note left",
  GIFT_GIVEN: "gift",
  INTIMACY: "made love",
  CONVERSATION: "real conversation",
  TRIP: "trip",
  GESTURE: "gesture",
};

/** The order the log form offers, easiest and most common first. */
export const MOMENT_KINDS: MomentKind[] = [
  "DATE_NIGHT",
  "NOTE_LEFT",
  "GESTURE",
  "CONVERSATION",
  "INTIMACY",
  "GIFT_GIVEN",
  "TRIP",
];

export const IDEA_LABEL: Record<IdeaKind, string> = {
  GIFT: "gift",
  DATE: "date",
  GESTURE: "gesture",
};

export const EFFORT_LABEL: Record<Effort, string> = {
  SMALL: "minutes",
  MEDIUM: "an evening",
  BIG: "planning",
};

export const READING_LABEL: Record<ReadingKind, string> = {
  ARTICLE: "article",
  BOOK: "book",
  PODCAST: "podcast",
};

/** "today" / "yesterday" / "6 days ago" — the way a person would say it. */
export function daysAgo(days: number | null): string {
  if (days === null) return "not yet";
  if (days <= 0) return "today";
  if (days === 1) return "yesterday";
  if (days < 31) return `${days} days ago`;
  const months = Math.round(days / 30.44);
  return months <= 1 ? "about a month ago" : `about ${months} months ago`;
}

export function inDays(days: number): string {
  if (days <= 0) return "today";
  if (days === 1) return "tomorrow";
  return `in ${days} days`;
}
