/** Every focus-timer URL. See features/finance/financeApi.ts for why these modules exist. */
import { api, jsonInit } from "../../lib/api";
import type { FocusKind, FocusSession } from "../../lib/types";

const BASE = "/api/focus";

export const getSessions = (date: string, kind: FocusKind) =>
  api<FocusSession[]>(`${BASE}/sessions?date=${date}&kind=${kind}`);

export const recordSession = (body: unknown) =>
  api(`${BASE}/sessions`, jsonInit("POST", body));
