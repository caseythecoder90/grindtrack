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

export interface ConversationSummary {
  id: number;
  title: string;
  lastMessageAt: string;
}

export interface ChatTurn {
  role: "user" | "assistant";
  content: string;
  createdAt: string;
}

export interface ChatReply {
  conversationId: number;
  reply: string;
  inputTokens: number;
  outputTokens: number;
}

export const listConversations = () => api<ConversationSummary[]>(`${BASE}/chat`);

export const getConversation = (id: number) => api<ChatTurn[]>(`${BASE}/chat/${id}`);

/**
 * One turn. Ten to thirty seconds — the model reads the plan, the logs and the calendar before it
 * answers — so the caller must show the wait rather than block silently.
 */
export const sendChat = (conversationId: number | null, message: string) =>
  api<ChatReply>(`${BASE}/chat`, jsonInit("POST", { conversationId, message }));
