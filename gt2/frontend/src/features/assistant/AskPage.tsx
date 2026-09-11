import { useCallback, useEffect, useRef, useState } from "react";
import { errorMessage } from "../../lib/api";
import {
  getAssistantStatus,
  getConversation,
  listConversations,
  streamChat,
  type AssistantStatus,
  type ChatTurn,
  type ConversationSummary,
} from "./assistantApi";
import ProposedWeek from "./ProposedWeek";

/**
 * Asking the assistant things.
 *
 * <p>One conversation at a time with a list to switch between them, because the useful shape of
 * this is "carry on from yesterday's question", not a fresh mind every time.
 *
 * <p>A turn takes ten to thirty seconds — the model reads the plan, the logs, the calendar before
 * it answers — and the answer streams back a fragment at a time. So the wait is never silent: the
 * question appears in the thread the moment it is sent, then a line saying what the model is
 * reading, then the answer arriving word by word. A spinner for half a minute reads as broken.
 */

/**
 * Tool names as a waiting person would say them. Falling back to the raw name matters: a tool
 * added later should read a little awkwardly here, not vanish and leave the bubble blank.
 */
const READING: Record<string, string> = {
  get_plan: "reading your plan…",
  get_days: "reading your daily logs…",
  get_calendar: "reading your calendar…",
  get_focus_sessions: "reading your focus sessions…",
};
export default function AskPage() {
  const [status, setStatus] = useState<AssistantStatus | null>(null);
  const [configured, setConfigured] = useState<boolean | null>(null);
  const [conversations, setConversations] = useState<ConversationSummary[]>([]);
  const [conversationId, setConversationId] = useState<number | null>(null);
  const [turns, setTurns] = useState<ChatTurn[]>([]);
  /** The question already sent but not yet answered — rendered as its own bubble. */
  const [pending, setPending] = useState<string | null>(null);
  /** What the model is doing right now, until the first word of the answer replaces it. */
  const [doing, setDoing] = useState("reading your data…");
  /** The answer so far. Rendered in place, so the thread reads the same before and after. */
  const [streamed, setStreamed] = useState("");
  const [draft, setDraft] = useState("");
  const [error, setError] = useState("");
  const foot = useRef<HTMLDivElement>(null);

  const refreshList = useCallback(async () => {
    try {
      setConversations(await listConversations());
    } catch {
      /* the list is navigation; a failure here must not block asking */
    }
  }, []);

  useEffect(() => {
    getAssistantStatus()
      .then((s) => {
        setStatus(s);
        setConfigured(s.configured);
      })
      .catch(() => setConfigured(false));
    refreshList();
  }, [refreshList]);

  // Follow the thread down as it grows, including while a question is pending.
  useEffect(() => {
    foot.current?.scrollIntoView({ block: "end" });
  }, [turns, pending, streamed]);

  async function open(id: number) {
    setError("");
    setConversationId(id);
    setTurns(await getConversation(id).catch(() => []));
  }

  function startNew() {
    setConversationId(null);
    setTurns([]);
    setError("");
  }

  async function send() {
    const message = draft.trim();
    if (!message || pending) return;
    setDraft("");
    setPending(message);
    setDoing("reading your data…");
    setStreamed("");
    setError("");
    try {
      const reply = await streamChat(conversationId, message, {
        onTool: (name) => setDoing(READING[name] ?? `reading ${name.replace(/_/g, " ")}…`),
        onText: (delta) => setStreamed((soFar) => soFar + delta),
      });
      setConversationId(reply.conversationId);
      // Re-read rather than keeping what was streamed: the server is what decides what the thread
      // holds, and a reload must show the same thing this does.
      setTurns(await getConversation(reply.conversationId));
      refreshList();
      getAssistantStatus().then(setStatus).catch(() => {});
    } catch (e) {
      setError(errorMessage(e, "the assistant could not answer"));
      setDraft(message); // hand the question back rather than losing it
    } finally {
      setPending(null);
      setStreamed("");
    }
  }

  if (configured === false) {
    return (
      <div className="panel">
        <h2>ask</h2>
        <p className="refpara">
          The assistant is off. Set <code>ANTHROPIC_API_KEY</code> on the deployment to turn it on —
          everything else in the app works either way.
        </p>
      </div>
    );
  }

  return (
    <div className="panel askpanel">
      <div className="panelhead">
        <h2>ask</h2>
        <button type="button" onClick={startNew} disabled={conversationId === null && !turns.length}>
          new conversation
        </button>
      </div>

      {conversations.length > 0 && (
        <div className="askthreads">
          {conversations.slice(0, 8).map((c) => (
            <button
              key={c.id}
              type="button"
              className={"chip" + (c.id === conversationId ? " active" : "")}
              aria-pressed={c.id === conversationId}
              onClick={() => open(c.id)}
            >
              {c.title}
            </button>
          ))}
        </div>
      )}

      <div className="askthread">
        {turns.length === 0 && !pending && (
          <div className="empty">
            Ask about the week, the plan, what to study next. It can read your logs, plan,
            calendar and sessions — and change none of them.
          </div>
        )}
        {turns.map((t, i) => (
          <div key={i}>
            <div className={"bubble " + t.role}>{t.content}</div>
            {t.proposedWeekStart && (
              <ProposedWeek weekStart={t.proposedWeekStart} onBooked={refreshList} />
            )}
          </div>
        ))}
        {pending && (
          <>
            <div className="bubble user">{pending}</div>
            {streamed ? (
              <div className="bubble assistant">
                {streamed}
                <span className="caret" aria-hidden="true" />
              </div>
            ) : (
              <div className="bubble assistant thinking" aria-live="polite">
                {doing}
              </div>
            )}
          </>
        )}
        <div ref={foot} />
      </div>

      {error && <div className="error">{error}</div>}

      <div className="askform">
        <textarea
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          placeholder="e.g. am I on pace for CKA in December?"
          rows={2}
          onKeyDown={(e) => {
            // Enter sends, Shift+Enter breaks the line — the shape every chat box has.
            if (e.key === "Enter" && !e.shiftKey) {
              e.preventDefault();
              send();
            }
          }}
        />
        <button type="button" className="primary" onClick={send} disabled={!draft.trim() || !!pending}>
          {pending ? "thinking…" : "Ask"}
        </button>
      </div>

      {status && (
        <p className="askbill">
          ${status.costThisMonthUsd.toFixed(2)} this month
          {status.cacheSavingUsd > 0 && (
            <> · caching saved ${status.cacheSavingUsd.toFixed(2)}</>
          )}
        </p>
      )}
    </div>
  );
}
