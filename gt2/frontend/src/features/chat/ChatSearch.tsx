import { useEffect, useState } from "react";
import { errorMessage } from "../../lib/api";
import {
  getLinkMessages,
  getMediaMessages,
  posterUrl,
  searchMessages,
  type ChatMessage,
  type Person,
} from "./chatApi";
import { clock } from "./media";

type Tab = "search" | "media" | "links";

interface Props {
  me: Person | null;
  them: Person | null;
  onJump: (id: number) => void;
  onClose: () => void;
}

const URL_RE = /(https?:\/\/[^\s<>]+?)(?=[.,;:!?)\]'"]*(?:\s|$))/g;

function when(iso: string): string {
  const d = new Date(iso);
  const now = new Date();
  const sameYear = d.getFullYear() === now.getFullYear();
  return d.toLocaleDateString([], sameYear ? { month: "short", day: "numeric" } : { year: "numeric", month: "short", day: "numeric" });
}

/** The words with the query lit. */
function Marked({ text, q }: { text: string; q: string }) {
  const needle = q.trim().toLowerCase();
  if (!needle) return <>{text}</>;
  const out: React.ReactNode[] = [];
  let at = 0;
  const lower = text.toLowerCase();
  for (let i = lower.indexOf(needle); i >= 0 && out.length < 40; i = lower.indexOf(needle, at)) {
    if (i > at) out.push(<span key={at}>{text.slice(at, i)}</span>);
    out.push(<mark key={"m" + i}>{text.slice(i, i + needle.length)}</mark>);
    at = i + needle.length;
  }
  if (at < text.length) out.push(<span key={"e" + at}>{text.slice(at)}</span>);
  return <>{out}</>;
}

/** Around a hundred and sixty characters around the first hit, on word boundaries. */
function snippet(body: string, q: string): string {
  const one = body.replace(/\s+/g, " ").trim();
  if (one.length <= 160) return one;
  const at = one.toLowerCase().indexOf(q.trim().toLowerCase());
  let start = Math.max(0, (at < 0 ? 0 : at) - 60);
  if (start > 0) {
    const space = one.indexOf(" ", start);
    if (space >= 0 && space < start + 20) start = space + 1;
  }
  const end = Math.min(one.length, start + 160);
  return (start > 0 ? "…" : "") + one.slice(start, end) + (end < one.length ? "…" : "");
}

/**
 * The conversation searched, its pictures, and its links: one panel in place of the thread. A hit
 * opens the thread around its message.
 */
export default function ChatSearch({ me, them, onJump, onClose }: Props) {
  const [tab, setTab] = useState<Tab>("search");
  const [q, setQ] = useState("");
  const [hits, setHits] = useState<ChatMessage[] | null>(null);
  const [media, setMedia] = useState<ChatMessage[] | null>(null);
  const [links, setLinks] = useState<ChatMessage[] | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (tab === "media" && media === null) {
      getMediaMessages().then(setMedia).catch((e) => setError(errorMessage(e, "could not load the pictures")));
    }
    if (tab === "links" && links === null) {
      getLinkMessages().then(setLinks).catch((e) => setError(errorMessage(e, "could not load the links")));
    }
  }, [tab, media, links]);

  async function search() {
    const query = q.trim();
    if (query.length < 2) return;
    setBusy(true);
    setError("");
    try {
      setHits(await searchMessages(query));
    } catch (e) {
      setError(errorMessage(e, "could not search"));
    } finally {
      setBusy(false);
    }
  }

  function who(m: ChatMessage): string {
    if (me && m.senderId === me.id) return "you";
    return them?.username ?? "them";
  }

  return (
    <section className="chat-search" aria-label="search the chat">
      <div className="chat-search-head">
        <div className="seg" role="group" aria-label="What to look through">
          {(["search", "media", "links"] as Tab[]).map((t) => (
            <button key={t} type="button" aria-pressed={tab === t} onClick={() => setTab(t)}>
              {t}
            </button>
          ))}
        </div>
        <button type="button" className="linkish" onClick={onClose}>‹ back to the room</button>
      </div>
      {error && <div className="error">{error}</div>}

      {tab === "search" && (
        <>
          <div className="rec-search-row bible">
            <input
              type="search"
              value={q}
              placeholder="words to find"
              aria-label="Search the conversation"
              enterKeyHint="search"
              autoFocus
              onChange={(e) => {
                setQ(e.target.value);
                if (!e.target.value.trim()) setHits(null);
              }}
              onKeyDown={(e) => { if (e.key === "Enter") search(); }}
            />
            <button type="button" disabled={busy || q.trim().length < 2} onClick={search}>find</button>
          </div>
          {hits !== null && (
            <div className="chat-hits">
              <div className="hint">
                {hits.length === 0 ? "nothing found" : hits.length === 40 ? "40 hits — the newest forty; add a word" : `${hits.length} hit${hits.length === 1 ? "" : "s"}`}
              </div>
              {hits.map((m) => (
                <button type="button" key={m.id} className="chat-hit" onClick={() => onJump(m.id)}>
                  <span className="hint">{who(m)} · {when(m.sentAt)}</span>
                  <span className="chat-hit-text"><Marked text={snippet(m.body, q)} q={q} /></span>
                </button>
              ))}
            </div>
          )}
        </>
      )}

      {tab === "media" && (
        <div className="chat-mediagrid">
          {media === null && <span className="hint">loading…</span>}
          {media !== null && media.length === 0 && <span className="hint">no pictures yet</span>}
          {media?.map((m) => (
            m.media && (
              <button type="button" key={m.id} className={"chat-mediacell " + m.media.kind.toLowerCase()} aria-label={`open ${m.media.kind.toLowerCase()} from ${when(m.sentAt)}`} onClick={() => onJump(m.id)}>
                {m.media.kind === "AUDIO" ? (
                  <span className="chat-mediacell-voice">🎤 {clock(m.media.durationMs ?? 0)}</span>
                ) : (
                  <img src={posterUrl(m.media.id)} alt="" loading="lazy" />
                )}
                {m.media.kind === "VIDEO" && <span className="chat-mediacell-badge">▶</span>}
              </button>
            )
          ))}
        </div>
      )}

      {tab === "links" && (
        <div className="chat-hits">
          {links === null && <span className="hint">loading…</span>}
          {links !== null && links.length === 0 && <span className="hint">no links yet</span>}
          {links?.map((m) => {
            const urls = m.body.match(URL_RE) ?? [];
            if (urls.length === 0) return null;
            return (
              <div key={m.id} className="chat-hit chat-link">
                <span className="hint">{who(m)} · {when(m.sentAt)} · <button type="button" className="linkish inline" onClick={() => onJump(m.id)}>in the thread ›</button></span>
                {urls.map((u, i) => (
                  <a key={i} href={u} target="_blank" rel="noopener noreferrer">{u}</a>
                ))}
              </div>
            );
          })}
        </div>
      )}
    </section>
  );
}
