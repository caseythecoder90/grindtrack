/** Every plan URL. See features/finance/financeApi.ts for why these modules exist. */
import { api, jsonInit } from "../../lib/api";
import type { PlanData, PlanItem } from "../../lib/types";

const BASE = "/api/plan";

export const getPlan = () => api<PlanData>(BASE);

export const importPlan = (body: unknown) => api(`${BASE}/import`, jsonInit("POST", body));

export const updatePlanItem = (id: number, body: unknown) =>
  api<PlanItem>(`${BASE}/items/${id}`, jsonInit("PATCH", body));
