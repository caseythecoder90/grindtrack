import type { PlanItem, PlanItemType, PlanStatus } from "../../lib/types";

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
