import { useEffect, useRef, useState, type ReactNode } from "react";
import type { ChatMessage } from "./chatApi";
import { chatStore, useChat } from "./chatStore";
import Message, { type Receipt } from "./Message";

/** Within this many pixels of the bottom counts as reading the newest, so new ones scroll into view. */
const NEAR_BOTTOM_PX = 160;

function dayLabel(iso: string): string {
  const day = new Date(iso);
  const today = new Date();
  const yesterday = new Date();
  yesterday.setDate(today.getDate() - 1);
  const same = (a: Date, b: Date) => a.toDateString() === b.toDateString();
  if (same(day, today)) return "today";
  if (same(day, yesterday)) return "yesterday";
  return day.toLocaleDateString([], { weekday: "short", month: "short", day: "numeric" });
}

/**
 * The room. The thread is the page and the composer sticks to the bottom, like the ask tab; new
 * messages scroll into view when you are already at the bottom, and stay put when you have
 * scrolled up to read something older. Tapping a message opens what can be done to it.
 */
export default function ChatPage() {
  const view = useChat();
  const [draft, setDraft] = useState("");
  const [openId, setOpenId] = useState<number | null>(null);
  const box = useRef<HTMLTextAreaElement>(null);
  const foot = useRef<HTMLDivElement>(null);
  const nearBottom = useRef(true);

  // On screen means read: the store marks what arrives as read while this is mounted and visible.
  useEffect(() => {
    chatStore.setReading(true);
    const onVisibility = () => chatStore.setReading(document.visibilityState === "visible");
    document.addEventListener("visibilitychange", onVisibility);
    return () => {
      document.removeEventListener("visibilitychange", onVisibility);
      chatStore.setReading(false);
    };
  }, []);

  useEffect(() => {
    const onScroll = () => {
      const gap =
        document.documentElement.scrollHeight - window.innerHeight - window.scrollY;
      nearBottom.current = gap < NEAR_BOTTOM_PX;
    };
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  const last = view.messages[view.messages.length - 1];
  const lastId = last?.id ?? 0;
  const lastIsMine = !!last && !!view.me && last.senderId === view.me.id;
  const pendingCount = view.pending.length;
  useEffect(() => {
    if (!view.loaded) return;
    if (nearBottom.current || lastIsMine || pendingCount > 0) {
      foot.current?.scrollIntoView({ block: "end" });
    }
  }, [view.loaded, lastId, lastIsMine, pendingCount]);

  async function older() {
    const before = document.documentElement.scrollHeight;
    await chatStore.loadOlder();
    // Keep what was on screen where it was, with the earlier page now above it.
    requestAnimationFrame(() => {
      window.scrollBy(0, document.documentElement.scrollHeight - before);
    });
  }

  function send() {
    const body = draft.trim();
    if (!body) return;
    setDraft("");
    void chatStore.send(body);
    box.current?.focus();
  }

  useEffect(() => {
    const el = box.current;
    if (!el) return;
    el.style.height = "0px";
    el.style.height = Math.min(el.scrollHeight, 168) + "px";
  }, [draft]);

  const me = view.me;
  const rows: ReactNode[] = [];
  let lastDay = "";
  let lastMineId = 0;
  for (const m of view.messages) {
    if (me && m.senderId === me.id) lastMineId = m.id;
  }
  for (const m of view.messages) {
    const day = dayLabel(m.sentAt);
    if (day !== lastDay) {
      rows.push(
        <div key={"day-" + m.id} className="chat-day">
          {day}
        </div>,
      );
      lastDay = day;
    }
    rows.push(
      <Message
        key={m.id}
        message={m}
        mine={!!me && m.senderId === me.id}
        meId={me?.id ?? 0}
        open={openId === m.id}
        receipt={m.id === lastMineId ? receiptFor(m, view.theirs) : null}
        onToggle={() => setOpenId((o) => (o === m.id ? null : m.id))}
        onReact={(emoji) => void chatStore.react(m.id, emoji)}
        onUnsend={() => {
          setOpenId(null);
          void chatStore.unsend(m.id);
        }}
      />,
    );
  }

  const live =
    view.connection === "open" ? "live" : view.connection === "connecting" ? "connecting…" : "offline";

  return (
    <section className="chat" aria-label="chat">
      <div className="chat-head">
        <span>{view.them ? `with ${view.them.username}` : "the room"}</span>
        <span className={view.typing ? "live" : ""}>{view.typing ? "typing…" : live}</span>
      </div>

      {!view.loaded && !view.error && <p className="muted">opening…</p>}
      {view.error && (
        <div className="error">
          {view.error}{" "}
          <button type="button" className="linkish" onClick={() => chatStore.clearError()}>
            ok
          </button>
        </div>
      )}
      {view.loaded && !view.them && (
        <p className="muted">
          There is nobody else here yet. Add a partner under Accounts and this is where the two of
          you talk.
        </p>
      )}
      {view.hasMore && (
        <button
          type="button"
          className="linkish chat-older"
          disabled={view.loadingOlder}
          onClick={older}
        >
          {view.loadingOlder ? "loading…" : "earlier messages"}
        </button>
      )}

      <div className="chat-thread">
        {rows}
        {view.pending.map((p) => (
          <div key={p.clientId} className="chat-msg mine pending">
            <div className="bubble">{p.body}</div>
            <div className="chat-meta">
              {p.failed ? (
                <>
                  could not send ·{" "}
                  <button type="button" className="linkish" onClick={() => void chatStore.retry(p.clientId)}>
                    retry
                  </button>{" "}
                  ·{" "}
                  <button type="button" className="linkish" onClick={() => chatStore.discard(p.clientId)}>
                    discard
                  </button>
                </>
              ) : (
                "sending…"
              )}
            </div>
          </div>
        ))}
        <div ref={foot} />
      </div>

      <div className="composer chat-composer">
        <textarea
          ref={box}
          className="composer-box"
          value={draft}
          rows={1}
          placeholder={view.them ? `message ${view.them.username}…` : "nobody to message yet"}
          disabled={!view.them}
          onChange={(e) => {
            setDraft(e.target.value);
            chatStore.typing(e.target.value.trim().length > 0);
          }}
          onKeyDown={(e) => {
            // Enter sends, Shift+Enter breaks the line — the shape every chat box has.
            if (e.key === "Enter" && !e.shiftKey) {
              e.preventDefault();
              send();
            }
          }}
        />
        <div className="composer-row">
          <div className="composer-left" />
          <div className="composer-right">
            <button
              type="button"
              className="send"
              aria-label="send"
              disabled={!draft.trim() || !view.them}
              onClick={send}
            >
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <path d="M12 19V5M5 12l7-7 7 7" />
              </svg>
            </button>
          </div>
        </div>
      </div>
    </section>
  );
}

function receiptFor(m: ChatMessage, theirs: { deliveredId: number; readId: number } | null): Receipt {
  if (m.deletedAt !== null) return null;
  if (!theirs) return "sent";
  if (theirs.readId >= m.id) return "seen";
  if (theirs.deliveredId >= m.id) return "delivered";
  return "sent";
}
