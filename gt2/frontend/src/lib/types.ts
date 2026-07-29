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

/**
 * Which side of the day a view is showing. "all" is study + work combined; the
 * backend computes all three on every request so switching never refetches.
 */
export type Scope = "all" | "study" | "work";

export const SCOPES: Scope[] = ["all", "study", "work"];

export interface ScopeStats {
  totalHours: number;
  daysLogged: number;
  streak: number;
  daysThisMonth: number;
  weeks: { weekStart: string; hours: number }[];
  categories: { category: string; hours: number }[];
  days: { date: string; hours: number }[];
}

export interface Stats {
  study: ScopeStats;
  work: ScopeStats;
  all: ScopeStats;
}

export interface PublicStats {
  streak: number;
  totalHours: number;
  daysLogged: number;
  days: { date: string; hours: number }[];
}

/** Weekly hour target per scope. "all" is the two added together. */
export const TARGETS: Record<Scope, number> = { study: 20, work: 40, all: 60 };

export const SCOPE_LABELS: Record<Scope, string> = {
  all: "everything",
  study: "study",
  work: "work",
};

// --- focus timer ------------------------------------------------------------

export type FocusKind = "study" | "work";

export interface FocusSession {
  id: number;
  startedAt: string;
  durationMinutes: number;
  completed: boolean;
  kind: FocusKind;
}

export const FOCUS_DEFAULTS = { sessions: 3, focusMin: 60, breakMin: 10, kind: "study" as FocusKind };

// --- 4-year plan ------------------------------------------------------------

export type PlanItemType = "milestone" | "cert" | "module" | "book" | "paper" | "project";
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

// --- work: day-job tracking (separate from study tracking above) -----------

export interface WorkDay {
  logDate: string;
  hours: number;
  categories: string[];
  project: string;
  goals: string;
  did: string;
  blockers: string;
  learnings: string;
}

export type WorkSkillStatus = "not_started" | "in_progress" | "proficient";

export interface WorkSkill {
  id: number;
  name: string;
  category: string;
  detail: string;
  status: WorkSkillStatus;
  notes: string;
  sortOrder: number;
}

export const WORK_WEEKLY_TARGET = TARGETS.work;

export const WORK_CATEGORIES = [
  "Feature dev",
  "Maintenance",
  "Bug fix",
  "Ops / observability",
  "Design",
  "Code review",
  "Meetings",
  "Learning",
  "Docs",
  "On-call",
];

// --- todos ------------------------------------------------------------------

/** Todos are tagged by which side of the day they belong to, not by study/work. */
export type TodoKind = "work" | "personal";

export interface Todo {
  id: number;
  title: string;
  kind: TodoKind;
  done: boolean;
  dueDate: string | null;
  sortOrder: number;
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

export const WEEKLY_TARGET = TARGETS.study;
