import { useCallback, useEffect, useRef, useState } from "react";
import { errorMessage } from "../../lib/api";
import {
  getAssistantStatus,
  getConversation,
  listConversations,
  streamChat,
  StreamCutError,
  type AssistantStatus,
  type ChatTurn,
  type ConversationSummary,
} from "./assistantApi";
import { useSpeech } from "../../lib/speech";
import ConversationsSheet from "./ConversationsSheet";
import ProposedLog from "./ProposedLog";
import ProposedTodos from "./ProposedTodos";
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
/** Three ways into a blank box. Each is a question the assistant is good at. */
const STARTERS = [
  "am I on pace for the CKA?",
  "plan next week for me",
  "remind me to…",
];

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
  const [listOpen, setListOpen] = useState(false);
  // Spoken phrases land in the box next to whatever was typed; nothing is sent until Ask.
  const speech = useSpeech((phrase) =>
    setDraft((d) => (d.trim() ? d.replace(/\s*$/, " ") : "") + phrase),
  );
  const foot = useRef<HTMLDivElement>(null);
  const box = useRef<HTMLTextAreaElement>(null);
  /** What the box shows: the draft, plus the phrase still being heard. */
  const shown = speech.interim ? draft + (draft.trim() ? " " : "") + speech.interim : draft;

  // The box grows with what is in it, one line to six, like every chat composer does.
  useEffect(() => {
    const el = box.current;
    if (!el) return;
    el.style.height = "0px";
    el.style.height = Math.min(el.scrollHeight, 168) + "px";
  }, [shown]);

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
    speech.stop();
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
      if (e instanceof StreamCutError) {
        // The turn itself is almost certainly finished and stored — the server does not stop
        // working when nobody is listening. So find the answer rather than throwing it away, and
        // deliberately do NOT hand the question back: re-asking buys the same reply twice.
        await refreshList();
        const id = conversationId;
        if (id !== null) setTurns(await getConversation(id).catch(() => turns));
        setError(
          id === null
            ? "the connection dropped, but the reply was saved — it is in the list above"
            : "the connection dropped while it was answering; this is what it saved",
        );
      } else {
        setError(errorMessage(e, "the assistant could not answer"));
        setDraft(message); // hand the question back rather than losing it
      }
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
        <button
          type="button"
          onClick={startNew}
          disabled={conversationId === null && !turns.length}
          aria-label="new conversation"
        >
          + new
        </button>
      </div>

      {/* One line until you want the list: the thread is what you came for. */}
      {conversations.length > 0 && (
        <button type="button" className="threadline" onClick={() => setListOpen(true)}>
          <span className="threadline-title">
            {conversations.find((c) => c.id === conversationId)?.title ?? "new conversation"}
          </span>
          <span className="threadline-count">{conversations.length}</span>
          <span className="threadline-chev" aria-hidden="true">
            ⌄
          </span>
        </button>
      )}

      {listOpen && (
        <ConversationsSheet
          conversations={conversations}
          currentId={conversationId}
          onOpen={(id) => {
            setListOpen(false);
            open(id);
          }}
          onNew={() => {
            setListOpen(false);
            startNew();
          }}
          onDeleted={(id) => {
            setConversations((all) => all.filter((c) => c.id !== id));
            if (id === conversationId) startNew();
          }}
          onClose={() => setListOpen(false)}
        />
      )}

      <div className="askthread">
        {turns.length === 0 && !pending && (
          <div className="askempty">
            <p className="empty">
              It can read your logs, plan, calendar and sessions, and draft a week or a day's log
              for you to approve. It changes nothing on its own.
            </p>
            {/* Three ways in, because a blank box is the hardest kind of question. */}
            <div className="starters">
              {STARTERS.map((s) => (
                <button
                  key={s}
                  type="button"
                  className="starter"
                  onClick={() => {
                    // A starter that ends in an ellipsis is a beginning, not a question.
                    setDraft(s.endsWith("…") ? s.slice(0, -1) + " " : s);
                    box.current?.focus();
                  }}
                >
                  {s}
                </button>
              ))}
            </div>
          </div>
        )}
        {turns.map((t, i) => (
          <div key={i}>
            <div className={"bubble " + t.role}>{t.content}</div>
            {t.proposedWeekStart && (
              <ProposedWeek weekStart={t.proposedWeekStart} onBooked={refreshList} />
            )}
            {t.proposedLogDate && <ProposedLog date={t.proposedLogDate} onSaved={refreshList} />}
            {t.proposedTodosDate && (
              <ProposedTodos date={t.proposedTodosDate} onAdded={refreshList} />
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

      {/* The composer: one rounded card, the words on top, the controls in a row beneath — the
          shape every chat app has settled on, so the thumb already knows where the send is. It
          sticks to the bottom of the screen while the thread scrolls above it. */}
      <div className={"composer" + (speech.listening ? " listening" : "")}>
        <textarea
          ref={box}
          className="composer-box"
          value={shown}
          onChange={(e) => {
            // Typing takes the box back: the words on screen become the draft as they stand, and
            // the phrase in flight is dropped so it cannot land a second time.
            if (speech.listening) speech.abort();
            setDraft(e.target.value);
          }}
          placeholder={speech.listening ? "listening…" : "ask about your week…"}
          rows={1}
          onKeyDown={(e) => {
            // Enter sends, Shift+Enter breaks the line — the shape every chat box has.
            if (e.key === "Enter" && !e.shiftKey) {
              e.preventDefault();
              send();
            }
          }}
        />
        <div className="composer-row">
          <div className="composer-left">
            {speech.listening ? (
              <span className="listening-pill">
                <span className="dot" aria-hidden="true" />
                listening
              </span>
            ) : speech.error ? (
              <span className="composer-hint">{speech.error}</span>
            ) : (
              status && (
                <span className="composer-cost" title="the assistant's bill this month">
                  ${status.costThisMonthUsd.toFixed(2)} this month
                  {status.cacheSavingUsd > 0 && <> · saved ${status.cacheSavingUsd.toFixed(2)}</>}
                </span>
              )
            )}
          </div>
          <div className="composer-right">
            {speech.supported && (
              <button
                type="button"
                className={"mic" + (speech.listening ? " on" : "")}
                aria-pressed={speech.listening}
                aria-label={speech.listening ? "stop listening" : "speak your question"}
                disabled={!!pending}
                onClick={() => (speech.listening ? speech.stop() : speech.start())}
              >
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                  strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                  <rect x="9" y="3" width="6" height="11" rx="3" />
                  <path d="M5 11a7 7 0 0 0 14 0M12 18v3" />
                </svg>
              </button>
            )}
            <button
              type="button"
              className="send"
              aria-label={pending ? "thinking" : "send"}
              onClick={send}
              disabled={!draft.trim() || !!pending}
            >
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <path d="M12 19V5M5 12l7-7 7 7" />
              </svg>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
