
export interface DayLog {
  logDate: string;
  hours: number;
  categories: string[];
  focus: string;
  did: string;
  wins: string;
  blockers: string;
  energy: number | null;
}

export interface WeekReview {
  weekStart: string;
  summary: string;
  wins: string;
  blockers: string;
  adjustments: string;
  nextFocus: string;
  onTrack: boolean | null;
}

export interface Stats {
  totalHours: number;
  daysLogged: number;
  streak: number;
  weeks: { weekStart: string; hours: number }[];
  categories: { category: string; hours: number }[];
}

export interface PublicStats {
  streak: number;
  totalHours: number;
  daysLogged: number;
  days: { date: string; hours: number }[];
}

export const CATEGORIES = [
  "Certs",
  "Protocols & security",
  "Distributed systems",
  "Go",
  "Java",
  "Payments",
  "Projects",
  "Open source",
  "AI",
  "Interview prep",
  "Work impact",
  "Reading",
];

export const WEEKLY_TARGET = 16;
