/**
 * The app's sections, and which of them a phone gets in the bottom bar.
 *
 * One list, two navigations: the desktop tab strip renders all of `TABS`, the bottom
 * bar renders `PRIMARY_TABS` plus a "more" button holding the rest. Keeping the split
 * here rather than inside either component is what makes promoting a tab a one-line
 * change — which it already was once, when the calendar took a slot and `todos`
 * moved into the sheet.
 */

export type Tab =
  | "today"
  | "focus"
  | "cal"
  | "todos"
  | "plan"
  | "work"
  | "money"
  | "us"
  | "week"
  | "stats";

export const TABS: Tab[] = [
  "today",
  "focus",
  "cal",
  "todos",
  "plan",
  "work",
  "money",
  "us",
  "week",
  "stats",
];

/**
 * Four slots, because the fifth belongs to "more". These are the sections opened
 * daily: two of them are the write paths the app exists for — logging hours and
 * starting a timer — `cal` answers "what is on" before the day starts, and `plan`
 * is what those hours are against.
 *
 * `todos` gave up its slot to the calendar when that landed, which is what this
 * array being one line is for.
 */
export const PRIMARY_TABS: Tab[] = ["today", "focus", "cal", "plan"];

/** Everything the bottom bar cannot fit. Reviewed weekly rather than opened daily. */
export const SECONDARY_TABS: Tab[] = TABS.filter((t) => !PRIMARY_TABS.includes(t));

/** Bottom-bar labels. Only where the tab name is too wide for a fifth of 390px. */
const SHORT: Partial<Record<Tab, string>> = { cal: "cal" };

export function shortLabel(tab: Tab): string {
  return SHORT[tab] ?? tab;
}
