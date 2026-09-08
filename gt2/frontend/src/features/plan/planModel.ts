import type { PlanItem, PlanItemType, PlanQuarter, PlanStatus } from "../../lib/types";

/**
 * Framework-free plan vocabulary and helpers: display labels, the status
 * cycle, target-date ordering, and progress math.
 */

export const TYPE_LABEL: Record<PlanItemType, string> = {
  milestone: "milestones",
  cert: "certs",
  module: "protocol modules",
  book: "books",
  paper: "papers & RFCs",
  project: "projects",
};

export const TYPES = Object.keys(TYPE_LABEL) as PlanItemType[];

/** Plan year 1 is Jul 2026 – Jun 2027; every later year is the same window shifted. */
export const PLAN_START_YEAR = 2026;

export function yearWindow(year: number): string {
  const from = PLAN_START_YEAR + year - 1;
  return `Jul ${from} – Jun ${from + 1}`;
}

/** July, zero-indexed. Plan year 1 runs Jul 2026 – Jun 2027, so quarter 1 starts in July. */
const PLAN_START_MONTH = 6;

/**
 * Which quarter today falls in, or null if today is outside the plan entirely.
 *
 * <p>Derived from the date rather than stored, so it cannot go stale, and looked up against the
 * quarters that actually exist rather than clamped to the nearest. Before the plan starts and
 * after it ends are both real answers, and neither should be reported as "you are in Q1" —
 * the same rule the assistant's context service follows on the server.
 */
export function currentQuarter(quarters: PlanQuarter[], today = new Date()): PlanQuarter | null {
  const months =
    (today.getFullYear() - PLAN_START_YEAR) * 12 + (today.getMonth() - PLAN_START_MONTH);
  if (months < 0) return null;
  return quarters.find((q) => q.qtr === Math.floor(months / 3) + 1) ?? null;
}

/**
 * The plan years to render, 1..N, where N is the highest year any item or quarter
 * carries. The workbook decides how long the plan is; the UI does not hardcode it.
 */
export function planYears(
  items: { yearNum: number | null }[],
  quarters: { yearNum: number }[],
): number[] {
  const last = Math.max(
    0,
    ...items.map((i) => i.yearNum ?? 0),
    ...quarters.map((q) => q.yearNum),
  );
  return Array.from({ length: last }, (_, i) => i + 1);
}

/** Clicking a status chip advances not_started → in_progress → done → not_started. */
export const NEXT_STATUS: Record<PlanStatus, PlanStatus> = {
  not_started: "in_progress",
  in_progress: "done",
  done: "not_started",
};

export const STATUS_LABEL: Record<PlanStatus, string> = {
  not_started: "not started",
  in_progress: "in progress",
  done: "done ✓",
};

/** Sort: dated items first (ascending by target date), then workbook order. */
export function byTarget(a: PlanItem, b: PlanItem): number {
  if (a.targetDate && b.targetDate && a.targetDate !== b.targetDate) {
    return a.targetDate < b.targetDate ? -1 : 1;
  }
  if (!!a.targetDate !== !!b.targetDate) return a.targetDate ? -1 : 1;
  return a.sortOrder - b.sortOrder;
}

export function doneCount(items: PlanItem[]): number {
  return items.filter((i) => i.status === "done").length;
}

/** Integer percent for a progress bar; an empty list reads as 0%. */
export function progressPercent(done: number, total: number): number {
  return Math.round((done / Math.max(1, total)) * 100);
}
