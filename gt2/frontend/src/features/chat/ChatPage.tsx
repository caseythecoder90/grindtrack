import { useEffect, useRef, useState, type ReactNode } from "react";
import { errorMessage } from "../../lib/api";
import { posterUrl, type ChatMessage } from "./chatApi";
import { chatStore, useChat } from "./chatStore";
import { prepare, type Prepared } from "./media";
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
 * scrolled up to read something older. Tapping a message opens what can be done to it. The
 * composer takes words, a picture or a clip from the phone (prepared there first: resized, the
 * poster made), and stickers from the tray.
 */
export default function ChatPage() {
  const view = useChat();
  const [draft, setDraft] = useState("");
  const [openId, setOpenId] = useState<number | null>(null);
  const [attachment, setAttachment] = useState<Prepared | null>(null);
  const [preparing, setPreparing] = useState(false);
  const [attachError, setAttachError] = useState("");
  const [trayOpen, setTrayOpen] = useState(false);
  const box = useRef<HTMLTextAreaElement>(null);
  const picker = useRef<HTMLInputElement>(null);
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

  // A preview that was never sent still holds memory until it is let go.
  useEffect(() => {
    return () => {
      if (attachment) URL.revokeObjectURL(attachment.previewUrl);
    };
  }, [attachment]);

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
    if (!body && !attachment) return;
    setDraft("");
    const picture = attachment;
    setAttachment(null);
    void chatStore.send(body, picture);
    box.current?.focus();
  }

  async function pick(file: File | undefined) {
    if (!file) return;
    setAttachError("");
    setPreparing(true);
    try {
      if (attachment) URL.revokeObjectURL(attachment.previewUrl);
      setAttachment(await prepare(file, view.maxBytes));
    } catch (e) {
      setAttachment(null);
      setAttachError(errorMessage(e, "that could not be read"));
    } finally {
      setPreparing(false);
      if (picker.current) picker.current.value = "";
    }
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
        onKeepSticker={(on) => {
          setOpenId(null);
          if (m.media) void chatStore.keepSticker(m.media.id, on);
        }}
      />,
    );
  }

  const live =
    view.connection === "open" ? "live" : view.connection === "connecting" ? "connecting…" : "offline";
  const canSend = !!view.them && (draft.trim().length > 0 || attachment !== null) && !preparing;

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
            <div className={"bubble" + (p.preview ? " media" : "")}>
              {p.preview && <img className="chat-media" src={p.preview} alt="" />}
              {p.body && <div className={p.preview ? "chat-caption" : undefined}>{p.body}</div>}
            </div>
            <div className="chat-meta">
              {p.failed ? (
                <>
                  {p.error || "could not send"} ·{" "}
                  <button type="button" className="linkish" onClick={() => void chatStore.retry(p.clientId)}>
                    retry
                  </button>{" "}
                  ·{" "}
                  <button type="button" className="linkish" onClick={() => chatStore.discard(p.clientId)}>
                    discard
                  </button>
                </>
              ) : p.attachment && p.mediaId === null ? (
                `uploading ${Math.round(p.progress * 100)}%`
              ) : (
                "sending…"
              )}
            </div>
          </div>
        ))}
        <div ref={foot} />
      </div>

      {trayOpen && view.mediaOn && (
        <div className="chat-tray" role="group" aria-label="stickers">
          {view.stickers.length === 0 && (
            <p className="hint">
              No stickers yet. Tap any picture in the thread and choose “keep as sticker”.
            </p>
          )}
          {view.stickers.map((s) => (
            <button
              key={s.id}
              type="button"
              aria-label="send sticker"
              onClick={() => {
                setTrayOpen(false);
                void chatStore.sendSticker(s);
              }}
            >
              <img src={posterUrl(s.id)} alt="" loading="lazy" />
            </button>
          ))}
        </div>
      )}

      <div className="composer chat-composer">
        {attachment && (
          <div className="chat-attach">
            <img src={attachment.previewUrl} alt="" />
            <span className="hint">
              {attachment.kind === "video" ? "video clip" : "photo"}
              {attachment.durationMs ? ` · ${Math.round(attachment.durationMs / 1000)} s` : ""}
            </span>
            <button
              type="button"
              className="linkish"
              aria-label="remove attachment"
              onClick={() => setAttachment(null)}
            >
              ×
            </button>
          </div>
        )}
        <textarea
          ref={box}
          className="composer-box"
          value={draft}
          rows={1}
          placeholder={
            view.them
              ? attachment
                ? "add a caption…"
                : `message ${view.them.username}…`
              : "nobody to message yet"
          }
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
          <div className="composer-left">
            {preparing ? (
              <span className="composer-hint">preparing…</span>
            ) : attachError ? (
              <span className="composer-hint error">{attachError}</span>
            ) : null}
          </div>
          <div className="composer-right">
            {view.mediaOn && view.them && (
              <>
                <input
                  ref={picker}
                  type="file"
                  accept="image/*,video/*"
                  hidden
                  onChange={(e) => void pick(e.target.files?.[0])}
                />
                <button
                  type="button"
                  className="tool"
                  aria-label="stickers"
                  aria-pressed={trayOpen}
                  onClick={() => setTrayOpen((o) => !o)}
                >
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                    strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                    <path d="M4 6a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v8l-6 6H6a2 2 0 0 1-2-2z" />
                    <path d="M14 20v-4a2 2 0 0 1 2-2h4M9 10h.01M15 10h.01M9 14c.8 1 2.2 1 3 0" />
                  </svg>
                </button>
                <button
                  type="button"
                  className="tool"
                  aria-label="photo or video"
                  disabled={preparing}
                  onClick={() => picker.current?.click()}
                >
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                    strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                    <path d="M4 8h3l2-3h6l2 3h3v11H4z" />
                    <circle cx="12" cy="13" r="3.5" />
                  </svg>
                </button>
              </>
            )}
            <button
              type="button"
              className="send"
              aria-label="send"
              disabled={!canSend}
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
