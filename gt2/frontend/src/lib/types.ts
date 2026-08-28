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

// --- finance ----------------------------------------------------------------

export type Institution =
  | "CAPITAL_ONE"
  | "CHASE"
  | "WELLS_FARGO"
  | "BANK_OF_AMERICA"
  | "AIDVANTAGE"
  | "OTHER";

export type AccountType = "CHECKING" | "SAVINGS" | "CREDIT_CARD" | "LOAN";

/**
 * SPEND and INCOME are real money movement. TRANSFER and PAYMENT are not — a card
 * payment settles a purchase that was already counted, so including it would double
 * count. The backend excludes both from every rollup.
 */
export type TxnType = "SPEND" | "INCOME" | "TRANSFER" | "PAYMENT";

export type CategorySource = "UNCATEGORIZED" | "RULE" | "MANUAL";

export interface FinanceAccount {
  id: number;
  name: string;
  institution: Institution;
  accountType: AccountType;
  last4: string | null;
  currentBalance: number;
  balanceAsOf: string | null;
  countsTowardSavings: boolean;
  active: boolean;
  sortOrder: number;
  transactionCount: number;
}

export interface FinanceTransaction {
  id: number;
  accountId: number;
  postedDate: string;
  transactionDate: string | null;
  amount: number;
  description: string;
  merchant: string | null;
  txnType: TxnType;
  category: string | null;
  issuerCategory: string | null;
  categorySource: CategorySource;
  pending: boolean;
  notes: string;
}

export interface SavingsGoal {
  id: number;
  name: string;
  targetAmount: number;
  targetDate: string | null;
  note: string;
  active: boolean;
  sortOrder: number;
  currentAmount: number;
  remaining: number;
  progressPercent: number;
}

/** Result of parsing an upload — identical shape whether or not it was committed. */
export interface ImportResult {
  batchId: number | null;
  format: string;
  rowsInFile: number;
  imported: number;
  duplicates: number;
  pending: number;
  skipped: number;
  periodStart: string | null;
  periodEnd: string | null;
  /** Present when the statement asserts a balance (Capital One deposits, Aidvantage). */
  balanceUpdate: string | null;
  warnings: string[];
  dryRun: boolean;
}

export interface ImportBatch {
  id: number;
  accountId: number;
  filename: string;
  sourceFormat: string;
  rowsInFile: number;
  rowsImported: number;
  rowsDuplicate: number;
  rowsPending: number;
  periodStart: string | null;
  periodEnd: string | null;
  importedAt: string;
}

export interface FinanceSummary {
  savingsBalance: number;
  netWorth: number;
  goals: SavingsGoal[];
  accounts: FinanceAccount[];
  uncategorizedCount: number;
}

export const INSTITUTION_LABELS: Record<Institution, string> = {
  CAPITAL_ONE: "Capital One",
  CHASE: "Chase",
  WELLS_FARGO: "Wells Fargo",
  BANK_OF_AMERICA: "Bank of America",
  AIDVANTAGE: "Aidvantage",
  OTHER: "Other",
};

export const ACCOUNT_TYPE_LABELS: Record<AccountType, string> = {
  CHECKING: "checking",
  SAVINGS: "savings",
  CREDIT_CARD: "credit card",
  LOAN: "loan",
};

// --- app constants ----------------------------------------------------------

export const CATEGORIES = [
  "Certs",
  "Protocols & security",
  "Distributed systems",
  "Java",
  "TypeScript & React",
  "Linux",
  "Payments",
  "Projects",
  "Open source",
  "AI",
  "Interview prep",
  "Work impact",
  "Reading",
];

export const WEEKLY_TARGET = TARGETS.study;
