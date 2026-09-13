/** Every recovery URL. See features/finance/financeApi.ts for why these modules exist. */
import { api, jsonInit } from "../../lib/api";

const BASE = "/api/recovery";

export type Slot = "big_book" | "reflection" | "meditation";
export type PersonRole = "sponsor" | "prospect" | "friend";
export type PersonState = "ask" | "overdue" | "due" | "ok";

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

export interface Para {
  seq: number;
  body: string;
  pageLabel: string | null;
  pageSeq: number;
}

export interface Chapter {
  no: number;
  title: string;
  firstSeq: number;
  paragraphs: number;
  firstPage: string | null;
  lastPage: string | null;
  state: "done" | "now" | "later";
}

/** Today's part of the book, or what was read today, plus the table of contents. */
export interface Reading {
  bookTitle: string;
  chapterNo: number;
  chapterTitle: string;
  paragraphs: Para[];
  words: number;
  pagesPerDay: number;
  pagesDue: number;
  pagesCarried: number;
  pageFrom: string | null;
  pageTo: string | null;
  percent: number;
  doneToday: boolean;
  dayNumber: number;
  readThroughs: number;
  paragraphCount: number;
  pageCount: number;
  place: number | null;
  chapters: Chapter[];
}

export interface ChapterText {
  no: number;
  title: string;
  paragraphs: Para[];
  prevNo: number | null;
  nextNo: number | null;
  cursor: number;
}

export interface Settings {
  pagesPerDay: number;
  meditationMinutes: number;
}

export interface Meditation {
  streak: number;
  doneToday: boolean;
}

export interface Person {
  id: number;
  name: string;
  role: PersonRole;
  cadenceDays: number;
  note: string | null;
  lastContact: string | null;
  lastNote: string | null;
  nextDue: string | null;
  overdueDays: number;
  state: PersonState;
  contacts: number;
}

export interface Contact {
  id: number;
  at: string;
  note: string | null;
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
  nextCall: Person | null;
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
  pages: number;
  entries: number;
  files: string[];
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
  pages: number;
  chapters: {
    no: number;
    title: string;
    paragraphs: number;
    words: number;
    firstPage: string | null;
    lastPage: string | null;
  }[];
  entries: number;
  missingCount: number;
  missing: string[];
  sample: string;
  warnings: string[];
  cursorReset: boolean;
}

export const getToday = () => api<RecoveryToday>(`${BASE}/today`);

// ---- the reading ----

export const finishReading = () =>
  api<RecoveryToday>(`${BASE}/read/done`, { method: "POST" });

export const markReadTo = (seq: number) =>
  api<RecoveryToday>(`${BASE}/read/mark?seq=${seq}`, { method: "POST" });

export const catchUp = () => api<RecoveryToday>(`${BASE}/read/catch-up`, { method: "POST" });

export const restartReading = () =>
  api<RecoveryToday>(`${BASE}/read/restart`, { method: "POST" });

export const getBook = () => api<Reading>(`${BASE}/book`);

export const getChapter = (no: number) => api<ChapterText>(`${BASE}/book/chapters/${no}`);

export const savePlace = (seq: number) =>
  api(`${BASE}/book/place`, jsonInit("PUT", { seq }));

export const updateSettings = (body: Partial<Settings>) =>
  api<Settings>(`${BASE}/settings`, jsonInit("PUT", body));

export const logSession = (minutes: number, completed: boolean) =>
  api<Meditation>(`${BASE}/sessions`, jsonInit("POST", { minutes, completed }));

// ---- the journal ----

export const getJournal = (before?: number) =>
  api<JournalEntry[]>(before ? `${BASE}/journal?before=${before}` : `${BASE}/journal`);

export const addJournal = (body: string, spoken: boolean) =>
  api<JournalEntry>(`${BASE}/journal`, jsonInit("POST", { body, spoken }));

export const deleteJournal = (id: number) =>
  api(`${BASE}/journal/${id}`, { method: "DELETE" });

// ---- the people ----

export const getPeople = () => api<Person[]>(`${BASE}/people`);

export const addPerson = (body: {
  name: string;
  role: PersonRole;
  cadenceDays: number;
  note?: string;
}) => api<Person>(`${BASE}/people`, jsonInit("POST", body));

export const updatePerson = (
  id: number,
  body: { name?: string; role?: PersonRole; cadenceDays?: number; note?: string; clearNote?: boolean },
) => api<Person>(`${BASE}/people/${id}`, jsonInit("PATCH", body));

export const archivePerson = (id: number) =>
  api(`${BASE}/people/${id}`, { method: "DELETE" });

export const logContact = (id: number, note?: string) =>
  api<Person>(`${BASE}/people/${id}/contacts`, jsonInit("POST", { note: note ?? null }));

export const getContacts = (id: number) => api<Contact[]>(`${BASE}/people/${id}/contacts`);

// ---- the books ----

export const getLibrary = () => api<Library>(`${BASE}/library`);

/**
 * No Content-Type header on purpose: the browser has to set the multipart boundary itself, and
 * setting it by hand produces a request the server cannot parse.
 */
export const importFiles = (slot: Slot, files: File[], dryRun: boolean, title?: string) => {
  const form = new FormData();
  for (const f of files) form.append("files", f);
  const query = `dryRun=${dryRun}` + (title ? `&title=${encodeURIComponent(title)}` : "");
  return api<ImportReport>(`${BASE}/import/${slot}?${query}`, { method: "POST", body: form });
};

export const reparse = (slot: Slot) =>
  api<ImportReport>(`${BASE}/import/${slot}/reparse`, { method: "POST" });

export const restartBible = () => api<Library>(`${BASE}/bible/restart`, { method: "POST" });
