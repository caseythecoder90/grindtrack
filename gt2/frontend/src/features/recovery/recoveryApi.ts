/** Every recovery URL. See features/finance/financeApi.ts for why these modules exist. */
import { api, jsonInit } from "../../lib/api";

const BASE = "/api/recovery";

export type Slot = "big_book" | "reflection" | "meditation";

export interface SoberNumber {
  sobrietyDate: string;
  days: number;
  spelledOut: string;
  nextMilestoneDays: number;
  nextMilestoneLabel: string;
  daysToMilestone: number;
}

export interface DailyEntry {
  bookTitle: string;
  title: string;
  body: string;
  month: number;
  day: number;
}

export interface Verse {
  verse: number;
  para: boolean;
  text: string;
}

export interface Passage {
  reference: string;
  translation: string;
  dayInPlan: number;
  planSize: number;
  verses: Verse[];
}

export interface Chapter {
  no: number;
  title: string;
  firstSeq: number;
  paragraphs: number;
  state: "done" | "now" | "later";
}

export interface Reading {
  bookTitle: string;
  chapterNo: number;
  chapterTitle: string;
  paragraphs: { seq: number; body: string }[];
  words: number;
  minutes: number;
  percent: number;
  doneToday: boolean;
  dayNumber: number;
  readThroughs: number;
  paragraphCount: number;
  chapters: Chapter[];
}

export interface Settings {
  readMinutes: number;
  meditationMinutes: number;
}

export interface Meditation {
  streak: number;
  doneToday: boolean;
}

export interface RecoveryToday {
  today: string;
  number: SoberNumber | null;
  reflection: DailyEntry | null;
  meditationEntry: DailyEntry | null;
  passage: Passage | null;
  reading: Reading | null;
  settings: Settings;
  meditation: Meditation;
}

export interface JournalEntry {
  id: number;
  createdAt: string;
  body: string;
  spoken: boolean;
}

export interface LibrarySlot {
  slot: Slot;
  defaultTitle: string;
  imported: boolean;
  title: string | null;
  importedAt: string | null;
  paragraphs: number;
  words: number;
  entries: number;
}

export interface Library {
  slots: LibrarySlot[];
  bible: { name: string; abbrev: string; verses: number; passages: number };
  biblePlanStart: string;
}

export interface ImportReport {
  dryRun: boolean;
  slot: Slot;
  title: string;
  paragraphs: number;
  words: number;
  chapters: { no: number; title: string; paragraphs: number; words: number }[];
  entries: number;
  missingCount: number;
  missing: string[];
  sample: string;
  warnings: string[];
  cursorReset: boolean;
}

export const getToday = () => api<RecoveryToday>(`${BASE}/today`);

export const finishReading = () =>
  api<RecoveryToday>(`${BASE}/read/done`, { method: "POST" });

export const restartReading = () =>
  api<RecoveryToday>(`${BASE}/read/restart`, { method: "POST" });

export const updateSettings = (body: Partial<Settings>) =>
  api<Settings>(`${BASE}/settings`, jsonInit("PUT", body));

export const logSession = (minutes: number, completed: boolean) =>
  api<Meditation>(`${BASE}/sessions`, jsonInit("POST", { minutes, completed }));

export const getJournal = (before?: number) =>
  api<JournalEntry[]>(before ? `${BASE}/journal?before=${before}` : `${BASE}/journal`);

export const addJournal = (body: string, spoken: boolean) =>
  api<JournalEntry>(`${BASE}/journal`, jsonInit("POST", { body, spoken }));

export const deleteJournal = (id: number) =>
  api(`${BASE}/journal/${id}`, { method: "DELETE" });

export const getLibrary = () => api<Library>(`${BASE}/library`);

/**
 * No Content-Type header on purpose: the browser has to set the multipart boundary itself, and
 * setting it by hand produces a request the server cannot parse.
 */
export const importText = (slot: Slot, file: File, dryRun: boolean, title?: string) => {
  const form = new FormData();
  form.append("file", file);
  const query = `dryRun=${dryRun}` + (title ? `&title=${encodeURIComponent(title)}` : "");
  return api<ImportReport>(`${BASE}/import/${slot}?${query}`, { method: "POST", body: form });
};

export const restartBible = () => api<Library>(`${BASE}/bible/restart`, { method: "POST" });
