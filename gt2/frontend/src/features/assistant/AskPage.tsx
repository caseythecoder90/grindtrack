import { useCallback, useEffect, useRef, useState } from "react";
import { errorMessage } from "../../lib/api";
import {
  getAssistantStatus,
  getConversation,
  listConversations,
  sendChat,
  type ChatTurn,
  type ConversationSummary,
} from "./assistantApi";

/**
 * Asking the assistant things.
 *
 * <p>One conversation at a time with a list to switch between them, because the useful shape of
 * this is "carry on from yesterday's question", not a fresh mind every time.
 *
 * <p>A turn takes ten to thirty seconds — the model reads the plan, the logs, the calendar before
 * it answers. So the question appears in the thread the moment it is sent and the wait is shown
 * as a pending bubble rather than a disabled form: a silent spinner for half a minute reads as
 * broken.
 */
export default function AskPage() {
  const [configured, setConfigured] = useState<boolean | null>(null);
  const [conversations, setConversations] = useState<ConversationSummary[]>([]);
  const [conversationId, setConversationId] = useState<number | null>(null);
  const [turns, setTurns] = useState<ChatTurn[]>([]);
  /** The question already sent but not yet answered — rendered as its own bubble. */
  const [pending, setPending] = useState<string | null>(null);
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
      .then((s) => setConfigured(s.configured))
      .catch(() => setConfigured(false));
    refreshList();
  }, [refreshList]);

  // Follow the thread down as it grows, including while a question is pending.
  useEffect(() => {
    foot.current?.scrollIntoView({ block: "end" });
  }, [turns, pending]);

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
    setError("");
    try {
      const reply = await sendChat(conversationId, message);
      setConversationId(reply.conversationId);
      // Re-read rather than appending locally: the server is what decides what the thread holds,
      // and a reload must show the same thing this does.
      setTurns(await getConversation(reply.conversationId));
      refreshList();
    } catch (e) {
      setError(errorMessage(e, "the assistant could not answer"));
      setDraft(message); // hand the question back rather than losing it
    } finally {
      setPending(null);
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
          <div key={i} className={"bubble " + t.role}>
            {t.content}
          </div>
        ))}
        {pending && (
          <>
            <div className="bubble user">{pending}</div>
            <div className="bubble assistant thinking">reading your data…</div>
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
    </div>
  );
}
