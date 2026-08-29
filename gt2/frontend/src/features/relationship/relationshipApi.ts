/** Every relationship URL. See financeApi.ts for why these modules exist. */
import { api, jsonInit } from "../../lib/api";
import type { Idea, ReadingItem, RelationshipSummary, Upcoming } from "../../lib/types";

const BASE = "/api/relationship";

export const getSummary = () => api<RelationshipSummary>(`${BASE}/summary`);

export const logMoment = (body: unknown) => api(`${BASE}/moments`, jsonInit("POST", body));

// -------------------------------------------------------------------- ideas

export const getIdeas = () => api<Idea[]>(`${BASE}/ideas`);

export const createIdea = (body: unknown) => api(`${BASE}/ideas`, jsonInit("POST", body));

/** Acting on an idea logs it as a moment and takes it off the list. */
export const completeIdea = (id: number) =>
  api(`${BASE}/ideas/${id}/done`, jsonInit("POST", { on: null }));

export const deleteIdea = (id: number) => api(`${BASE}/ideas/${id}`, { method: "DELETE" });

// ---------------------------------------------------------------- occasions

export const getOccasions = () => api<Upcoming[]>(`${BASE}/occasions`);

export const createOccasion = (body: unknown) =>
  api<Upcoming[]>(`${BASE}/occasions`, jsonInit("POST", body));

export const deleteOccasion = (id: number) => api(`${BASE}/occasions/${id}`, { method: "DELETE" });

// ------------------------------------------------------------------ reading

export const getReading = () => api<ReadingItem[]>(`${BASE}/reading`);

export const addReading = (body: unknown) =>
  api<ReadingItem[]>(`${BASE}/reading`, jsonInit("POST", body));

export const markRead = (id: number, takeaway: string) =>
  api<ReadingItem[]>(`${BASE}/reading/${id}/read`, jsonInit("POST", { takeaway, readOn: null }));

/** Turns a takeaway into a gesture idea, which is the point of having written it down. */
export const promoteTakeaway = (id: number, effort = "SMALL") =>
  api(`${BASE}/reading/${id}/promote`, jsonInit("POST", { effort }));

export const deleteReading = (id: number) => api(`${BASE}/reading/${id}`, { method: "DELETE" });
