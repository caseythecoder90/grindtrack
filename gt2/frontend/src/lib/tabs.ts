/**
 * The app's sections, and which of them a phone gets in the bottom bar.
 *
 * One list, two navigations: the desktop tab strip renders all of `TABS`, the bottom
 * bar renders `PRIMARY_TABS` plus a "more" button holding the rest. Keeping the split
 * here rather than inside either component is what makes promoting a tab a one-line
 * change — which it will be, because the calendar takes a primary slot when it lands
 * and `todos` moves into the sheet.
 */

export type Tab =
  | "today"
  | "focus"
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
 * starting a timer — and `plan` is what those hours are against.
 */
export const PRIMARY_TABS: Tab[] = ["today", "focus", "todos", "plan"];

/** Everything the bottom bar cannot fit. Reviewed weekly rather than opened daily. */
export const SECONDARY_TABS: Tab[] = TABS.filter((t) => !PRIMARY_TABS.includes(t));

/** Bottom-bar labels. Only where the tab name is too wide for a fifth of 390px. */
const SHORT: Partial<Record<Tab, string>> = { today: "today", todos: "todos" };

export function shortLabel(tab: Tab): string {
  return SHORT[tab] ?? tab;
}
