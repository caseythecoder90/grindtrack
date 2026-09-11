/** Every assistant URL. See features/finance/financeApi.ts for why these modules exist. */
import { api, jsonInit } from "../../lib/api";

/** Mirrors WeeklyReviewService.Report — the stored draft plus what it cost. */
export interface ReviewReport {
  weekStart: string;
  generatedAt: string;
  model: string;
  inputTokens: number;
  outputTokens: number;
  costUsd: number;
  draft: {
    summary: string;
    wins: string;
    blockers: string;
    adjustments: string;
    nextFocus: string;
    onTrack: boolean;
  };
}

export interface AssistantStatus {
  configured: boolean;
  model: string;
  reportsThisMonth: number;
  inputTokens: number;
  outputTokens: number;
  costThisMonthUsd: number;
}

const BASE = "/api/assistant";

export const getAssistantStatus = () => api<AssistantStatus>(`${BASE}/status`);

/** Null when no draft has been generated for that week — same convention as GET /api/days. */
export const getReviewDraft = (weekStart: string) =>
  api<ReviewReport | null>(`${BASE}/reviews?weekStart=${weekStart}`);

/** The click that spends money (~3¢) and takes ~30s. POST, deliberately. */
export const generateReviewDraft = (weekStart: string) =>
  api<ReviewReport>(`${BASE}/reviews?weekStart=${weekStart}`, jsonInit("POST", {}));
