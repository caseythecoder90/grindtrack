import { useEffect, useMemo, useState } from "react";
import { errorMessage } from "../../lib/api";
import { deleteConversation, type ConversationSummary } from "./assistantApi";

/**
 * "today", "yesterday", or the date — the grouping a person actually thinks in. Computed in the
 * viewer's own zone, because "yesterday" on a server clock is a different day on a phone at 11pm.
 */
function dayOf(iso: string, now: Date): string {
  const d = new Date(iso);
  const day = (x: Date) => Math.floor((x.getTime() - x.getTimezoneOffset() * 60000) / 86400000);
  const diff = day(now) - day(d);
  if (diff === 0) return "today";
  if (diff === 1) return "yesterday";
  return d.toLocaleDateString(undefined, { weekday: "short", day: "numeric", month: "short" });
}

function timeOf(iso: string): string {
  return new Date(iso).toLocaleTimeString(undefined, { hour: "numeric", minute: "2-digit" });
}

/**
 * Every conversation, as a sheet over the thread.
 *
 * <p>A sheet rather than a chip row, because a conversation's title is a sentence and a chip is
 * for a word. And a list rather than chips for a second reason that only shows up in use: two
 * threads that began with the same question have the same title, and a retry begins with exactly
 * the same question. So time is promoted from footnote to the thing you read second, and the turn
 * count sits beside it — a thread of one turn is an attempt, not a conversation.
 *
 * <p>Search is here from the start even though six conversations do not need it, because the
 * point of building the sheet over the one-line list is that it still works at sixty.
 */
export default function ConversationsSheet({
  conversations,
  currentId,
  onOpen,
  onNew,
  onDeleted,
  onClose,
}: {
  conversations: ConversationSummary[];
  currentId: number | null;
  onOpen: (id: number) => void;
  onNew: () => void;
  onDeleted: (id: number) => void;
  onClose: () => void;
}) {
  const [query, setQuery] = useState("");
  const [confirming, setConfirming] = useState<number | null>(null);
  const [error, setError] = useState("");

  // Escape closes; the scrim closes; nothing else is a dismiss gesture, on purpose — a swipe that
  // sometimes scrolls and sometimes dismisses is the worst of both.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  const groups = useMemo(() => {
    const now = new Date();
    const q = query.trim().toLowerCase();
    const shown = q ? conversations.filter((c) => c.title.toLowerCase().includes(q)) : conversations;
    const out: { day: string; items: ConversationSummary[] }[] = [];
    for (const c of shown) {
      const day = dayOf(c.lastMessageAt, now);
      const last = out[out.length - 1];
      if (last && last.day === day) last.items.push(c);
      else out.push({ day, items: [c] });
    }
    return out;
  }, [conversations, query]);

  async function remove(id: number) {
    setError("");
    try {
      await deleteConversation(id);
      setConfirming(null);
      onDeleted(id);
    } catch (e) {
      setError(errorMessage(e, "could not delete that"));
    }
  }

  return (
    <div className="sheet-scrim" onClick={onClose} role="presentation">
      <div
        className="sheet"
        role="dialog"
        aria-label="Conversations"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="sheet-grip" aria-hidden="true" />
        <div className="sheet-head">
          <strong>Conversations</strong>
          <div>
            <button type="button" className="sheet-new" onClick={onNew}>
              + new
            </button>
            <button type="button" className="ghost" aria-label="close" onClick={onClose}>
              ×
            </button>
          </div>
        </div>

        <input
          className="sheet-search"
          type="search"
          placeholder="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          aria-label="Search conversations"
        />

        {error && <div className="error">{error}</div>}

        <div className="sheet-list">
          {groups.length === 0 && (
            <p className="empty">{query ? "nothing matches" : "no conversations yet"}</p>
          )}
          {groups.map((g) => (
            <div key={g.day}>
              <div className="sheet-day">{g.day}</div>
              {g.items.map((c) =>
                confirming === c.id ? (
                  <div className="convrow confirming" key={c.id}>
                    <div className="convbody">
                      <div className="convtitle">{c.title}</div>
                      <div className="confirm">
                        <span>
                          {c.hasDraft
                            ? "delete this? the drafted week stays on the week tab"
                            : "delete this?"}
                        </span>
                        <button type="button" onClick={() => setConfirming(null)}>
                          keep
                        </button>
                        <button type="button" className="danger" onClick={() => remove(c.id)}>
                          delete
                        </button>
                      </div>
                    </div>
                  </div>
                ) : (
                  <div className={"convrow" + (c.id === currentId ? " on" : "")} key={c.id}>
                    <button type="button" className="convbody" onClick={() => onOpen(c.id)}>
                      <div className={"convtitle" + (c.turns <= 1 ? " thin" : "")}>{c.title}</div>
                      <div className="convmeta">
                        {timeOf(c.lastMessageAt)} · {c.turns} turn{c.turns === 1 ? "" : "s"}
                        {c.hasDraft && <span className="drafted"> · week drafted</span>}
                      </div>
                    </button>
                    <button
                      type="button"
                      className="rowdelete"
                      aria-label={`delete "${c.title}"`}
                      onClick={() => setConfirming(c.id)}
                    >
                      <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                        strokeWidth="1.6" strokeLinecap="round" aria-hidden="true">
                        <path d="M4 7h16M9 7V5h6v2M6 7l1 13h10l1-13" />
                      </svg>
                    </button>
                  </div>
                ),
              )}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
