/**
 * Every finance URL, in one place.
 *
 * <p>These paths were previously inline string literals across nine components. A typo was a
 * runtime 404 with nothing catching it at build time, and renaming a backend route meant grepping
 * the frontend and hoping. Now a route change touches this file and TypeScript finds the callers.
 *
 * <p>These functions return parsed data or throw. They never catch and never render -- components
 * own their own loading and error state, which is deliberate (see docs/architecture-conventions.md).
 */
import { api, jsonInit } from "../../lib/api";
import type {
  BudgetMonth,
  CategoryRule,
  FinanceSummary,
  FinanceTransaction,
  ImportBatch,
  ImportResult,
  MonthTotal,
  RecurringReport,
  RuleApplyResult,
  SpendSummary,
  TransactionPage,
} from "../../lib/types";

const BASE = "/api/finance";

// ---------------------------------------------------------------- dashboard

export const getSummary = () => api<FinanceSummary>(`${BASE}/summary`);

export const getSpending = (from: string, to: string) =>
  api<SpendSummary>(`${BASE}/spending?from=${from}&to=${to}`);

export const getMonthlyTotals = (months = 6) =>
  api<MonthTotal[]>(`${BASE}/spending/monthly?months=${months}`);

export const getRecurring = () => api<RecurringReport>(`${BASE}/recurring`);

// ----------------------------------------------------------------- accounts

export const getAccountTransactions = (accountId: number) =>
  api<FinanceTransaction[]>(`${BASE}/accounts/${accountId}/transactions`);

export const createAccount = (body: unknown) =>
  api(`${BASE}/accounts`, jsonInit("POST", body));

export const updateAccount = (id: number, body: unknown) =>
  api(`${BASE}/accounts/${id}`, jsonInit("PUT", body));

export const recordBalance = (id: number, body: unknown) =>
  api(`${BASE}/accounts/${id}/balance`, jsonInit("PATCH", body));

export const deleteAccount = (id: number) => api(`${BASE}/accounts/${id}`, { method: "DELETE" });

// ------------------------------------------------------------- transactions

/** Every transaction, paged and filtered. Blank filters are omitted rather than sent empty. */
export const browseTransactions = (opts: {
  accountId?: number | null;
  txnType?: string | null;
  uncategorizedOnly?: boolean;
  sort?: "amount" | "date";
  page?: number;
  size?: number;
}) => {
  const q = new URLSearchParams();
  if (opts.accountId) q.set("accountId", String(opts.accountId));
  if (opts.txnType) q.set("txnType", opts.txnType);
  if (opts.uncategorizedOnly) q.set("uncategorizedOnly", "true");
  q.set("sort", opts.sort ?? "date");
  q.set("page", String(opts.page ?? 0));
  q.set("size", String(opts.size ?? 50));
  return api<TransactionPage>(`${BASE}/transactions?${q.toString()}`);
};

export const getUncategorized = () =>
  api<FinanceTransaction[]>(`${BASE}/transactions/uncategorized`);

export const addTransaction = (body: unknown) =>
  api(`${BASE}/transactions`, jsonInit("POST", body));

/** Files a row and, when `createRule` is set, teaches the app the merchant. */
export const categorizeAndLearn = (id: number, category: string, createRule: boolean) =>
  api<{ rule: CategoryRule | null; ruleExisted: boolean }>(
    `${BASE}/transactions/${id}/categorize`,
    jsonInit("POST", { category, createRule }),
  );

// -------------------------------------------------------------------- goals

export const createGoal = (body: unknown) => api(`${BASE}/goals`, jsonInit("POST", body));

// -------------------------------------------------------------------- rules

export const getRules = () => api<CategoryRule[]>(`${BASE}/rules`);

export const createRule = (body: unknown) => api(`${BASE}/rules`, jsonInit("POST", body));

export const updateRule = (id: number, body: unknown) =>
  api(`${BASE}/rules/${id}`, jsonInit("PUT", body));

export const deleteRule = (id: number) => api(`${BASE}/rules/${id}`, { method: "DELETE" });

/** Re-runs every rule over the whole history. Safe: a rule never overwrites a manual category. */
/** Re-runs the type classifier over every transaction, after the classifier itself is fixed. */
export const reclassifyAll = () =>
  api<{ examined: number; changed: number }>(`${BASE}/transactions/reclassify`, {
    method: "POST",
  });

export const applyRules = () => api<RuleApplyResult>(`${BASE}/rules/apply`, { method: "POST" });

// ------------------------------------------------------------------ imports

export const getImportHistory = () => api<ImportBatch[]>(`${BASE}/imports`);

/**
 * No Content-Type header on purpose: the browser has to set the multipart boundary itself, and
 * setting it by hand produces a request the server cannot parse.
 */
export const uploadStatement = (accountId: number, dryRun: boolean, file: File) => {
  const form = new FormData();
  form.append("file", file);
  return api<ImportResult>(`${BASE}/imports?accountId=${accountId}&dryRun=${dryRun}`, {
    method: "POST",
    body: form,
  });
};

export const undoImport = (batchId: number) =>
  api(`${BASE}/imports/${batchId}`, { method: "DELETE" });

// ------------------------------------------------------------------ budgets

export const getBudgetMonth = (month: string) =>
  api<BudgetMonth>(`${BASE}/budget/month?month=${month}`);

export const createBudgetLine = (body: unknown) =>
  api(`${BASE}/budget/lines`, jsonInit("POST", body));

export const deleteBudgetLine = (id: number) =>
  api(`${BASE}/budget/lines/${id}`, { method: "DELETE" });

export const createBudgetExtra = (body: unknown) =>
  api(`${BASE}/budget/extras`, jsonInit("POST", body));

export const deleteBudgetExtra = (id: number) =>
  api(`${BASE}/budget/extras/${id}`, { method: "DELETE" });

export const setExpectedIncome = (expectedMonthlyIncome: number | null) =>
  api(`${BASE}/budget/income`, jsonInit("PUT", { expectedMonthlyIncome }));
