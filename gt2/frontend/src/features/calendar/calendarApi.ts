/** Every calendar and upkeep URL. See features/finance/financeApi.ts for why these modules exist. */
import { api, jsonInit } from "../../lib/api";
import type { CalendarEvent, UpkeepCompletion, UpkeepList, UpkeepTask } from "../../lib/types";

const EVENTS = "/api/calendar";
const UPKEEP = "/api/upkeep";

/** A whole month in one request: the grid needs a marker on every cell. */
export const getMonth = (month: string) =>
  api<CalendarEvent[]>(`${EVENTS}?month=${encodeURIComponent(month)}`);

export const getRange = (from: string, to: string) =>
  api<CalendarEvent[]>(`${EVENTS}?from=${from}&to=${to}`);

export const createEvent = (body: unknown) => api<CalendarEvent>(EVENTS, jsonInit("POST", body));

export const updateEvent = (id: number, body: unknown) =>
  api<CalendarEvent>(`${EVENTS}/${id}`, jsonInit("PATCH", body));

export const deleteEvent = (id: number) => api(`${EVENTS}/${id}`, { method: "DELETE" });

export const getUpkeep = () => api<UpkeepList>(UPKEEP);

export const createTask = (body: unknown) => api<UpkeepTask>(UPKEEP, jsonInit("POST", body));

/** One tap. Sends no date, so the server stamps its own today. */
export const markDone = (id: number) =>
  api<UpkeepTask>(`${UPKEEP}/${id}/done`, jsonInit("POST", {}));

export const updateTask = (id: number, body: unknown) =>
  api<UpkeepTask>(`${UPKEEP}/${id}`, jsonInit("PATCH", body));

export const deleteTask = (id: number) => api(`${UPKEEP}/${id}`, { method: "DELETE" });

export const getHistory = (id: number) => api<UpkeepCompletion[]>(`${UPKEEP}/${id}/history`);
