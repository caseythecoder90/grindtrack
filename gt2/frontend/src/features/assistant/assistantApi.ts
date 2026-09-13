/** Every assistant URL. See features/finance/financeApi.ts for why these modules exist. */
import { api, jsonInit, stream } from "../../lib/api";

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
  /** Input tokens written to the prompt cache, billed at 1.25x — chat only. */
  cacheWriteTokens: number;
  /** Input tokens served from the prompt cache, billed at 0.1x. */
  cacheReadTokens: number;
  costThisMonthUsd: number;
  /** Reads saved minus writes paid for. Negative means caching is costing money. */
  cacheSavingUsd: number;
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
  /** Messages in the thread, both sides. One is an attempt, not a conversation. */
  turns: number;
  /** A week was drafted somewhere in it. The draft survives the thread being deleted. */
  hasDraft: boolean;
}

export interface ChatTurn {
  role: "user" | "assistant";
  content: string;
  createdAt: string;
  /** The Monday this turn drafted a week for, or null — which is nearly every turn. */
  proposedWeekStart: string | null;
  /** The day this turn drafted a log entry for, or null. */
  proposedLogDate: string | null;
  /** The day this turn drafted todos on, or null. */
  proposedTodosDate: string | null;
}

export interface ChatReply {
  conversationId: number;
  reply: string;
  inputTokens: number;
  outputTokens: number;
  /**
   * Set when the turn drafted a week. A draft is a card, not a booking: the blocks live in the
   * week-plan endpoint and the calendar is untouched until {@link acceptWeekPlan}.
   */
  proposedWeekStart: string | null;
  /** Same contract for a drafted day's log: a card, and nothing saved until {@link acceptDayLog}. */
  proposedLogDate: string | null;
  /** And for drafted todos: a card, nothing added until {@link acceptTodos}. */
  proposedTodosDate: string | null;
}

export const listConversations = () => api<ConversationSummary[]>(`${BASE}/chat`);

/** The thread and its turns. A drafted week inside it is untouched — it belongs to the week. */
export const deleteConversation = (id: number) =>
  api(`${BASE}/chat/${id}`, { method: "DELETE" });

export const getConversation = (id: number) => api<ChatTurn[]>(`${BASE}/chat/${id}`);

export interface WeekPlanBlock {
  date: string;
  startTime: string;
  endTime: string;
  title: string;
  planItemId: number | null;
}

export interface WeekPlan {
  weekStart: string;
  generatedAt: string;
  model: string;
  inputTokens: number;
  outputTokens: number;
  rationale: string;
  blocks: WeekPlanBlock[];
}

/** Null when nothing has been proposed for that week. */
export const getWeekPlan = (weekStart: string) =>
  api<WeekPlan | null>(`${BASE}/week-plan?weekStart=${weekStart}`);

/** Costs money, books nothing. */
export const proposeWeekPlan = (weekStart: string) =>
  api<WeekPlan>(`${BASE}/week-plan?weekStart=${weekStart}`, jsonInit("POST", {}));

/**
 * Books the stored blocks onto the calendar. The only assistant call that writes anything, and it
 * writes what was shown — the server re-reads its own draft rather than trusting this request.
 */
export const acceptWeekPlan = (weekStart: string) =>
  api<{ weekStart: string; blocksBooked: number }>(
    `${BASE}/week-plan/accept?weekStart=${weekStart}`,
    jsonInit("POST", {}),
  );

/** What the stream can say while a turn is happening. */
export interface ChatStreamHandlers {
  /** The model started reading something — one of the four read tools, by its own name. */
  onTool(name: string): void;
  /** A fragment of the answer. Fragments concatenate to the reply that gets stored. */
  onText(delta: string): void;
}

/**
 * One turn, delivered as it happens.
 *
 * <p>Read with fetch rather than EventSource: EventSource only does GET, and the question belongs
 * in a body. The cost is parsing the frames here, which is a dozen lines and no state — a frame is
 * blank-line delimited, the lines that matter start with "event:" or "data:", and a chunk boundary
 * lands wherever the network puts it, so the tail of an incomplete frame is kept for the next one.
 *
 * <p>An error arrives as an event, not a status: by the time a turn can fail the response has been
 * 200 for some seconds. So a rejected promise here and a failed turn look the same to the caller,
 * which is the point.
 */
/**
 * The stream stopped before the turn said how it went.
 *
 * <p>Its own type because the right response is the opposite of a normal failure's. A turn whose
 * connection dropped was almost certainly finished and stored by the server — nothing about the
 * work depends on anyone still listening — so the answer exists and asking again would buy the
 * same answer a second time. The caller re-reads instead of retrying.
 */
export class StreamCutError extends Error {}

export async function streamChat(
  conversationId: number | null,
  message: string,
  handlers: ChatStreamHandlers,
): Promise<ChatReply> {
  const res = await stream(
    `${BASE}/chat/stream`,
    jsonInit("POST", { conversationId, message }),
  );
  const reader = res.body!.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  let done: ChatReply | null = null;

  for (;;) {
    let chunk;
    try {
      chunk = await reader.read();
    } catch {
      // The browser's own message here is "network error", which tells a person nothing and is
      // wrong about what happened besides: the turn is fine, this end of the pipe is not.
      throw new StreamCutError("the connection dropped while it was answering");
    }
    if (chunk.done) break;
    buffer += decoder.decode(chunk.value, { stream: true });

    let split = buffer.indexOf("\n\n");
    while (split !== -1) {
      const frame = buffer.slice(0, split);
      buffer = buffer.slice(split + 2);
      split = buffer.indexOf("\n\n");

      let event = "message";
      const data: string[] = [];
      for (const line of frame.split("\n")) {
        if (line.startsWith("event:")) event = line.slice(6).trim();
        else if (line.startsWith("data:")) data.push(line.slice(5).trim());
      }
      if (!data.length) continue;
      const payload = JSON.parse(data.join("\n"));

      if (event === "tool") handlers.onTool(payload.name);
      else if (event === "text") handlers.onText(payload.delta);
      else if (event === "done") done = payload as ChatReply;
      else if (event === "error") throw new Error(payload.error);
      // "tick" is the server keeping a proxy from closing an idle connection. Nothing to do.
    }
  }

  // The stream ended without saying how it went — a dropped connection mid-answer. The turn may
  // well have finished and been stored on the server; what is certain is that this client cannot
  // say what it holds, so it says that rather than inventing a reply.
  if (!done) throw new StreamCutError("the connection dropped before it finished answering");
  return done;
}

// --------------------------------------------------------------- day log drafts

/** What the conversation said — only that. Null means the day keeps what it has. */
export interface DayLogChanges {
  hours: number | null;
  categories: string[] | null;
  focus: string | null;
  did: string | null;
  wins: string | null;
  blockers: string | null;
  energy: number | null;
}

export interface DayLogDraft {
  logDate: string;
  generatedAt: string;
  changes: DayLogChanges;
  /** The whole day as it will read once saved — merged over the day as it stands right now. */
  result: {
    hours: number;
    categories: string[];
    focus: string;
    did: string;
    wins: string;
    blockers: string;
    energy: number | null;
    /** The day already had an entry, so the button says "update", not "create". */
    existed: boolean;
  };
}

/** Null when nothing has been drafted for that day. Re-merged on every read. */
export const getDayLogDraft = (date: string) =>
  api<DayLogDraft | null>(`${BASE}/day-log?date=${date}`);

/** The write. Saves what was stored and shown, merged over the day as it is at that moment. */
export const acceptDayLog = (date: string) =>
  api<{ logDate: string }>(`${BASE}/day-log/accept?date=${date}`, jsonInit("POST", {}));

// ---------------------------------------------------------------- morning brief

export interface MorningBrief {
  date: string;
  generatedAt: string;
  model: string;
  costUsd: number;
  /** motivation is absent on briefs drafted before it existed. */
  draft: { headline: string; today: string; suggestion: string; motivation?: string | null };
}

export interface TodoDraftItem {
  title: string;
  kind: "work" | "personal";
  dueDate: string | null;
}

export interface TodoDraft {
  date: string;
  generatedAt: string | null;
  items: TodoDraftItem[];
}

/** Null when nothing is drafted for that day — or once the batch has been added. */
export const getTodoDraft = (date: string) =>
  api<TodoDraft | null>(`${BASE}/todos?date=${date}`);

/** The write. Adds what was stored and shown, then removes the draft so it cannot repeat. */
export const acceptTodos = (date: string) =>
  api<{ date: string; added: number }>(`${BASE}/todos/accept?date=${date}`, jsonInit("POST", {}));

/** Null when nothing has been drafted for that day yet — before six, or with the assistant off. */
export const getMorningBrief = (date: string) =>
  api<MorningBrief | null>(`${BASE}/brief?date=${date}`);

/** Spends money (~2¢) and replaces the day's brief. The scheduler does this at six on its own. */
export const draftMorningBrief = (date: string) =>
  api<MorningBrief>(`${BASE}/brief?date=${date}`, jsonInit("POST", {}));
