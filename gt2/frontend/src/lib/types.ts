/** Shared API payload shapes and app-wide constants, grouped by feature. */

// --- tracking: daily logs and weekly reviews -------------------------------

export interface DayLog {
  logDate: string;
  hours: number;
  categories: string[];
  focus: string;
  did: string;
  wins: string;
  blockers: string;
  energy: number | null;
}

export interface WeekReview {
  weekStart: string;
  summary: string;
  wins: string;
  blockers: string;
  adjustments: string;
  nextFocus: string;
  onTrack: boolean | null;
}

// --- stats: header bar and heatmap -----------------------------------------

export interface Stats {
  totalHours: number;
  daysLogged: number;
  streak: number;
  weeks: { weekStart: string; hours: number }[];
  categories: { category: string; hours: number }[];
}

export interface PublicStats {
  streak: number;
  totalHours: number;
  daysLogged: number;
  days: { date: string; hours: number }[];
}

// --- focus timer ------------------------------------------------------------

export interface FocusSession {
  id: number;
  startedAt: string;
  durationMinutes: number;
  completed: boolean;
}

export const FOCUS_DEFAULTS = { sessions: 3, focusMin: 60, breakMin: 10 };

// --- 3-year plan ------------------------------------------------------------

export type PlanItemType = "milestone" | "cert" | "module" | "book" | "project";
export type PlanStatus = "not_started" | "in_progress" | "done";

export interface PlanItem {
  id: number;
  type: PlanItemType;
  title: string;
  details: string;
  targetLabel: string;
  targetDate: string | null;
  yearNum: number | null;
  qtr: number | null;
  tier: string | null;
  status: PlanStatus;
  notes: string;
  completedAt: string | null;
  sortOrder: number;
}

export interface PlanQuarter {
  qtr: number;
  windowLabel: string;
  yearNum: number;
  primaryFocus: string;
  secondaryFocus: string;
  careerTrack: string;
  deliverables: string;
}

export interface PlanReferenceSheet {
  sheet: string;
  title: string;
  contentJson: string;
  sortOrder: number;
}

export interface PlanData {
  items: PlanItem[];
  quarters: PlanQuarter[];
  reference: PlanReferenceSheet[];
}

// --- app constants ----------------------------------------------------------

export const CATEGORIES = [
  "Certs",
  "Protocols & security",
  "Distributed systems",
  "Go",
  "Java",
  "Payments",
  "Projects",
  "Open source",
  "AI",
  "Interview prep",
  "Work impact",
  "Reading",
];

export const WEEKLY_TARGET = 20;
