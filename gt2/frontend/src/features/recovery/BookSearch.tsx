import { useState } from "react";
import { errorMessage } from "../../lib/api";
import { findPage, searchBook, type Hit } from "./recoveryApi";

interface Props {
  /** Open the reader at a paragraph. */
  onOpen: (chapterNo: number, seq: number) => void;
}

/** The query's words lit up inside a snippet. */
function Marked({ text, words }: { text: string; words: string[] }) {
  const stems = words.filter((w) => w.length >= 2).map((w) => (w.length > 4 ? w.slice(0, -2) : w));
  if (stems.length === 0) return <>{text}</>;
  const re = new RegExp("(" + stems.map((w) => w.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")).join("|") + ")", "ig");
  return (
    <>
      {text.split(re).map((part, i) =>
        i % 2 === 1 ? <mark key={i}>{part}</mark> : <span key={i}>{part}</span>,
      )}
    </>
  );
}

/**
 * The book searched, or opened at a page: for the meeting where someone says "page 417", or
 * "the bit about half measures", and the physical book is at home.
 */
export default function BookSearch({ onOpen }: Props) {
  const [q, setQ] = useState("");
  const [page, setPage] = useState("");
  const [hits, setHits] = useState<Hit[] | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function search() {
    const query = q.trim();
    if (query.length < 2) return;
    setBusy(true);
    setError("");
    try {
      setHits(await searchBook(query));
    } catch (e) {
      setError(errorMessage(e, "could not search"));
    } finally {
      setBusy(false);
    }
  }

  async function go() {
    const label = page.trim();
    if (!label) return;
    setBusy(true);
    setError("");
    try {
      const place = await findPage(label);
      setPage("");
      onOpen(place.chapterNo, place.seq);
    } catch (e) {
      setError(errorMessage(e, `no page ${label}`));
    } finally {
      setBusy(false);
    }
  }

  const words = q.trim().toLowerCase().split(/\s+/);

  return (
    <div className="rec-card rec-search">
      <div className="rec-search-row">
        <input
          type="search"
          value={q}
          placeholder="search the book"
          aria-label="Search the book"
          enterKeyHint="search"
          onChange={(e) => {
            setQ(e.target.value);
            if (!e.target.value.trim()) setHits(null);
          }}
          onKeyDown={(e) => { if (e.key === "Enter") search(); }}
        />
        <button type="button" disabled={busy || q.trim().length < 2} onClick={search}>find</button>
        <input
          type="text"
          inputMode="numeric"
          value={page}
          placeholder="page"
          aria-label="Go to page"
          enterKeyHint="go"
          onChange={(e) => setPage(e.target.value)}
          onKeyDown={(e) => { if (e.key === "Enter") go(); }}
        />
        <button type="button" disabled={busy || !page.trim()} onClick={go}>go</button>
      </div>
      {error && <div className="error">{error}</div>}
      {hits !== null && (
        <div className="rec-hits">
          <div className="rec-cardhead">
            <span className="rec-note">{hits.length === 0 ? "nothing found" : hits.length === 40 ? "40 hits — the first forty; add a word" : `${hits.length} hit${hits.length === 1 ? "" : "s"}`}</span>
            <button type="button" className="linkish" onClick={() => { setHits(null); setQ(""); }}>clear</button>
          </div>
          {hits.map((h) => (
            <button type="button" key={h.seq} className="rec-hit" onClick={() => onOpen(h.chapterNo, h.seq)}>
              <span className="rec-note">{h.pageLabel ? `p. ${h.pageLabel} · ` : ""}{h.chapterTitle}</span>
              <span className="rec-hit-text"><Marked text={h.snippet} words={words} /></span>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
