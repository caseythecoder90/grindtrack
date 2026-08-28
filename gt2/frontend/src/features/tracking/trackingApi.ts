/**
 * Every study-tracker URL. See features/finance/financeApi.ts for why these modules exist.
 *
 * <p>These sit under a bare `/api` rather than `/api/tracking` because they are the original
 * endpoints from before the app had more than one thing in it. Left alone deliberately: renaming a
 * working route to satisfy a naming scheme is churn the deployed instance would pay for.
 */
import { api, jsonInit } from "../../lib/api";
import type { DayLog, PublicStats, Stats, WeekReview } from "../../lib/types";

export const getDay = (date: string) => api<DayLog | null>(`/api/days/${date}`);

export const getDays = (from: string, to: string) =>
  api<DayLog[]>(`/api/days?from=${from}&to=${to}`);

export const saveDay = (date: string, body: unknown) =>
  api(`/api/days/${date}`, jsonInit("PUT", body));

export const getWeek = (weekStart: string) => api<WeekReview | null>(`/api/weeks/${weekStart}`);

export const saveWeek = (weekStart: string, body: unknown) =>
  api(`/api/weeks/${weekStart}`, jsonInit("PUT", body));

export const getStats = () => api<Stats>("/api/stats");

/** The landing page's projection: hours and streak only, never any text field. */
export const getPublicStats = () => api<PublicStats>("/api/public/stats");

/**
 * The JSON backup, as a URL rather than a function.
 *
 * <p>Deliberately not fetched: the point is to let the browser follow the Content-Disposition
 * header and save the file. Pulling it through the wrapper would land the whole export in memory
 * as a string with nowhere useful to put it.
 */
export const EXPORT_URL = "/api/export";
