/** Every focus-timer URL. See features/finance/financeApi.ts for why these modules exist. */
import { api, jsonInit } from "../../lib/api";
import type { FocusKind, FocusSession, ReadingProgress } from "../../lib/types";

const BASE = "/api/focus";

export const getSessions = (date: string, kind: FocusKind) =>
  api<FocusSession[]>(`${BASE}/sessions?date=${date}&kind=${kind}`);

export const recordSession = (body: unknown) =>
  api<FocusSession>(`${BASE}/sessions`, jsonInit("POST", body));

/** Written after the session ends, which is the only time you know it. */
export const saveTakeaway = (id: number, takeaway: string) =>
  api<FocusSession>(`${BASE}/sessions/${id}/takeaway`, jsonInit("PATCH", { takeaway }));

export const getReadingProgress = () => api<ReadingProgress>(`${BASE}/reading`);
