/** Every work URL. See features/finance/financeApi.ts for why these modules exist. */
import { api, jsonInit } from "../../lib/api";
import type { WorkDay, WorkSkill } from "../../lib/types";

const BASE = "/api/work";

export const getWorkDay = (date: string) => api<WorkDay | null>(`${BASE}/days/${date}`);

export const getWorkDays = (from: string, to: string) =>
  api<WorkDay[]>(`${BASE}/days?from=${from}&to=${to}`);

export const saveWorkDay = (date: string, body: unknown) =>
  api(`${BASE}/days/${date}`, jsonInit("PUT", body));

export const getWorkSkills = () => api<WorkSkill[]>(`${BASE}/skills`);

export const createWorkSkill = (body: unknown) => api(`${BASE}/skills`, jsonInit("POST", body));

export const updateWorkSkill = (id: number, body: unknown) =>
  api<WorkSkill>(`${BASE}/skills/${id}`, jsonInit("PATCH", body));

export const deleteWorkSkill = (id: number) => api(`${BASE}/skills/${id}`, { method: "DELETE" });
