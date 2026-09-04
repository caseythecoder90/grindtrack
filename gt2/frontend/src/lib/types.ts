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

/**
 * "study" is the 6-8am block and "work" the day job; "reading" and "review" are the
 * lunch kinds. All but "work" fold their minutes into daily_logs.
 */
export type FocusKind = "study" | "work" | "reading" | "review";

/**
 * The two lunch kinds: they get their own streak and weekly target, and they are the
 * only kinds that carry a subject. Mirrors FocusKind.isLunch() on the backend, which
 * enforces the same rule — this copy decides what the UI offers, that one decides what
 * is stored.
 */
export function isLunchKind(kind: FocusKind): boolean {
  return kind === "reading" || kind === "review";
}

export const FOCUS_KIND_LABEL: Record<FocusKind, string> = {
  study: "study",
  work: "work",
  reading: "reading",
  review: "code review",
};

export interface FocusSession {
  id: number;
  startedAt: string;
  durationMinutes: number;
  completed: boolean;
  kind: FocusKind;
  /** The plan item this went into, when it is one. Null for a code review. */
  planItemId: number | null;
  /** What it went into, in words. Snapshotted at write time so history survives a re-import. */
  topic: string;
  takeaway: string;
}

/** One thing being worked through at lunch — a book, a paper, or a repo. */
export interface ReadingSubject {
  planItemId: number | null;
  label: string;
  kind: FocusKind;
  sessions: number;
  hours: number;
  lastOn: string;
}

export interface ReadingTakeaway {
  sessionId: number;
  on: string;
  label: string;
  kind: FocusKind;
  text: string;
}

/** The lunch dashboard: streak, weekly pace, what it went into, and what you wrote down. */
export interface ReadingProgress {
  weekdayStreak: number;
  /** Distinct days since Monday — the same unit the streak counts. */
  daysThisWeek: number;
  sessionsThisWeek: number;
  weeklyTarget: number;
  hoursThisWeek: number;
  totalSessions: number;
  totalHours: number;
  subjects: ReadingSubject[];
  recentTakeaways: ReadingTakeaway[];
}

export const FOCUS_DEFAULTS = {
  sessions: 3,
  focusMin: 60,
  breakMin: 10,
  kind: "study" as FocusKind,
  planItemId: null as number | null,
  topic: "",
};

/** A lunch break is one session, and it is shorter than a study block. */
export const LUNCH_DEFAULTS = { sessions: 1, focusMin: 40, breakMin: 5 };

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

export type AccountType = "CHECKING" | "SAVINGS" | "CREDIT_CARD" | "LOAN" | "RETIREMENT";

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
  /** Rows the parser could not read. A run of these means a bank changed its export layout. */
  skipped: number;
  /** How many of the imported rows a category rule was able to file automatically. */
  categorized: number;
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

/** One pattern, one category. Rules run in priority order and the first match wins. */
export interface CategoryRule {
  id: number;
  pattern: string;
  matchType: "CONTAINS" | "EQUALS" | "REGEX";
  category: string;
  priority: number;
  active: boolean;
  hitCount: number;
  lastApplied: string | null;
}

/** Result of re-running every rule over the whole history. */
export interface RuleApplyResult {
  examined: number;
  categorized: number;
  stillUncategorized: number;
}

/** One row of a spending rollup. `label` is null for uncategorized, which must stay visible. */
export interface CategoryTotal {
  label: string | null;
  total: number;
  count: number;
}

/**
 * Where the money went over a window. Every figure excludes transfers, card payments and
 * unsettled rows, so `totalSpend` is spending rather than movement.
 */
export interface SpendSummary {
  from: string;
  to: string;
  totalSpend: number;
  totalIncome: number;
  net: number;
  transactionCount: number;
  byCategory: CategoryTotal[];
  topMerchants: CategoryTotal[];
}

/** One month of totals, for comparing months against each other. `spend` is positive. */
export interface MonthTotal {
  month: string;
  spend: number;
  income: number;
  net: number;
  /** True for the month in progress, which is always short and must be labelled as such. */
  partial: boolean;
}

/** A charge that comes back on a rhythm. */
export interface Recurring {
  merchant: string;
  category: string | null;
  cadence: "WEEKLY" | "FORTNIGHTLY" | "MONTHLY" | "QUARTERLY" | "YEARLY";
  occurrences: number;
  typicalAmount: number;
  /** Cost per month once cadence is accounted for, so cadences can be added together. */
  monthlyEquivalent: number;
  lowest: number;
  highest: number;
  /** The amount moves around: a utility rather than a subscription. */
  variable: boolean;
  /** Nothing seen for a while. Cancelled, or about to reappear. */
  lapsed: boolean;
  firstSeen: string;
  lastSeen: string;
  nextExpected: string;
}

export interface RecurringReport {
  monthlyCommitment: number;
  liveCount: number;
  lapsedCount: number;
  items: Recurring[];
}

/** The recurring plan: what a category costs in a normal month. */
export interface BudgetLine {
  id: number;
  category: string;
  monthlyAmount: number;
  note: string;
  active: boolean;
  sortOrder: number;
}

/**
 * Something that only happens in one month. Negative is a one-off cost, positive is one-off money
 * in. Tagging a category makes a cost raise that category for the month only.
 */
export interface BudgetExtra {
  id: number;
  month: string;
  label: string;
  amount: number;
  category: string | null;
  note: string;
}

export type BudgetPace = "UNDER" | "ON_TRACK" | "AHEAD_OF_PACE" | "EXCEEDED" | "WITHIN";

/** Where one category stands this month. Every figure is positive. */
export interface BudgetCategoryLine {
  budgetId: number;
  category: string;
  budget: number;
  extra: number;
  planned: number;
  spent: number;
  /** Negative means over. Shown that way, because how far over is what changes behaviour. */
  left: number;
  percentUsed: number;
  pace: BudgetPace;
  extraLabels: string[];
}

export interface UnbudgetedLine {
  category: string | null;
  spent: number;
  count: number;
}

/** One month, fully reconciled: the plan, the one-offs, and what actually happened. */
export interface BudgetMonth {
  month: string;
  monthLabel: string;
  dayOfMonth: number;
  daysInMonth: number;
  currentMonth: boolean;
  expectedIncome: number;
  /** True when income was inferred from a trailing average rather than declared. */
  incomeIsEstimated: boolean;
  incomeSoFar: number;
  planned: number;
  spent: number;
  leftToSpend: number;
  projectedNet: number;
  expectedSpentByNow: number;
  extraExpenses: number;
  extraIncome: number;
  categories: BudgetCategoryLine[];
  unbudgeted: UnbudgetedLine[];
  extras: BudgetExtra[];
}

/** One page of transactions from the browse endpoint. */
export interface TransactionPage {
  items: FinanceTransaction[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
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
  RETIREMENT: "retirement",
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

/* ---------------------------------------------------------------- the us tab */

export type MomentKind =
  | "DATE_NIGHT"
  | "NOTE_LEFT"
  | "GIFT_GIVEN"
  | "INTIMACY"
  | "CONVERSATION"
  | "TRIP"
  | "GESTURE";

export type IdeaKind = "GIFT" | "DATE" | "GESTURE";
export type IdeaStatus = "IDEA" | "PLANNED" | "DONE";
export type Effort = "SMALL" | "MEDIUM" | "BIG";
export type ReadingKind = "ARTICLE" | "BOOK" | "PODCAST";
export type ReadingStatus = "TO_READ" | "READ";

export interface Moment {
  id: number;
  occurredOn: string;
  kind: MomentKind;
  note: string;
  /** Optional 1-3: how the week felt, to you. Never charted. */
  feltClose: number | null;
  /** True for kinds the discreet toggle hides. */
  isPrivate: boolean;
}

/** When something last happened. `daysSince` is null for never, which must not look like zero. */
export interface Recency {
  kind: MomentKind;
  lastOn: string | null;
  daysSince: number | null;
  note: string | null;
}

/**
 * Deliberately three tones and no warning. There is no red state for this card and there must
 * never be one — the whole feature exists to settle a question, not to grade an answer.
 */
export type PerspectiveTone = "CALM" | "NEUTRAL" | "SUGGEST";

export interface Perspective {
  headline: string;
  detail: string;
  tone: PerspectiveTone;
}

export interface Closeness {
  /** The last few, as dates. This is the thing that actually answers the question. */
  recentDates: string[];
  daysSince: number | null;
  lastThirtyDays: number;
  /** Your own trailing average. The only baseline used anywhere. */
  typicalPerMonth: number | null;
  perspective: Perspective;
}

export interface Upcoming {
  id: number;
  label: string;
  on: string;
  daysAway: number;
  years: number | null;
  ideaCount: number;
  note: string;
}

export interface Idea {
  id: number;
  kind: IdeaKind;
  title: string;
  detail: string;
  occasion: string | null;
  estCost: number | null;
  effort: Effort | null;
  status: IdeaStatus;
}

export interface ReadingItem {
  id: number;
  title: string;
  url: string | null;
  source: string | null;
  kind: ReadingKind;
  status: ReadingStatus;
  takeaway: string;
  readOn: string | null;
}

export interface RelationshipSummary {
  recency: Recency[];
  closeness: Closeness;
  upcoming: Upcoming[];
  readyIdeas: Idea[];
  lately: Moment[];
}
