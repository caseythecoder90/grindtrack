import { useEffect, useRef, useState } from "react";
import { errorMessage } from "../../lib/api";
import { getChapter, savePlace, type ChapterText, type Para } from "./recoveryApi";

interface Props {
  chapterNo: number;
  onOpen: (no: number) => void;
  onClose: () => void;
  /** "Mark read to here": everything up to this paragraph counts as read, today. */
  onMarkRead: (seq: number) => Promise<void>;
}

/** Whether the printed page turns between two paragraphs, so a rule can say so. */
function pageTurns(prev: Para | undefined, p: Para): boolean {
  return !!prev && prev.pageSeq !== p.pageSeq && !!p.pageLabel;
}

/**
 * A chapter open to read, from anywhere in the book. Set like a page: serif, the book's own
 * paragraphs, a thin rule where a page turns. Tapping a paragraph selects it, and the bar that
 * appears marks everything up to it as read — for reading ahead, or catching up in one sitting.
 * Opening a chapter saves it as your place, so the other device picks up here.
 */
export default function BookReader({ chapterNo, onOpen, onClose, onMarkRead }: Props) {
  const [chapter, setChapter] = useState<ChapterText | null>(null);
  const [selected, setSelected] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const top = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let live = true;
    setChapter(null);
    setSelected(null);
    setError("");
    getChapter(chapterNo)
      .then((c) => {
        if (!live) return;
        setChapter(c);
        // The place: this chapter, from its first paragraph. Fire and forget; a failure here
        // costs nothing the reader can see.
        savePlace(c.paragraphs[0]?.seq ?? 0).catch(() => {});
      })
      .catch((e) => live && setError(errorMessage(e, "could not open that chapter")));
    return () => {
      live = false;
    };
  }, [chapterNo]);

  // Once the chapter is on screen, bring its top into view: the contents that opened it may
  // have been scrolled far down the page.
  useEffect(() => {
    if (chapter) top.current?.scrollIntoView({ block: "start" });
  }, [chapter]);

  async function mark(seq: number) {
    setBusy(true);
    setError("");
    try {
      await onMarkRead(seq);
      setSelected(null);
    } catch (e) {
      setError(errorMessage(e, "could not mark that"));
    } finally {
      setBusy(false);
    }
  }

  if (error && !chapter) {
    return (
      <div className="rec-card">
        <div className="error">{error}</div>
        <button type="button" className="linkish" onClick={onClose}>‹ back to today's part</button>
      </div>
    );
  }
  if (!chapter) {
    return <div className="rec-card"><span className="rec-note">opening…</span></div>;
  }

  const first = chapter.paragraphs[0];
  const last = chapter.paragraphs[chapter.paragraphs.length - 1];
  const pages =
    first?.pageLabel && last?.pageLabel
      ? first.pageLabel === last.pageLabel
        ? `p. ${first.pageLabel}`
        : `pp. ${first.pageLabel}–${last.pageLabel}`
      : "";

  return (
    <div className="rec-card rec-pages rec-reader" ref={top}>
      <div className="rec-cardhead">
        <span className="rec-lbl">
          {chapter.no > 0 && `chapter ${chapter.no} · `}
          {chapter.title}
        </span>
        <span className="rec-note">{pages}</span>
      </div>
      <div className="rec-reader-nav">
        <button type="button" className="linkish" onClick={onClose}>‹ today's part</button>
        <span>
          {chapter.prevNo !== null && (
            <button type="button" className="linkish" onClick={() => onOpen(chapter.prevNo!)}>‹ previous</button>
          )}
          {chapter.nextNo !== null && (
            <button type="button" className="linkish" onClick={() => onOpen(chapter.nextNo!)}>next ›</button>
          )}
        </span>
      </div>
      <div className="rec-serif rec-reader-text">
        {chapter.paragraphs.map((p, i) => (
          <div key={p.seq}>
            {pageTurns(chapter.paragraphs[i - 1], p) && (
              <div className="rec-pagerule" aria-label={`page ${p.pageLabel}`}>
                <span />
                <em>{p.pageLabel}</em>
                <span />
              </div>
            )}
            <p
              className={
                "rec-pline" +
                (p.seq < chapter.cursor ? " read" : "") +
                (p.seq === chapter.cursor ? " cursor" : "") +
                (selected === p.seq ? " selected" : "")
              }
              onClick={() => setSelected((s) => (s === p.seq ? null : p.seq))}
            >
              {p.body}
            </p>
          </div>
        ))}
      </div>
      {error && <div className="error">{error}</div>}
      <div className="rec-foot">
        <span className="rec-note">
          {selected === null
            ? chapter.cursor > (first?.seq ?? 0) && chapter.cursor <= (last?.seq ?? 0)
              ? "the cursor is in this chapter"
              : "tap a paragraph to mark read to there"
            : "marks everything up to the tapped paragraph"}
        </span>
        {selected !== null ? (
          <button type="button" className="rec-primary" disabled={busy} onClick={() => mark(selected)}>
            mark read to here
          </button>
        ) : (
          last && (
            <button type="button" disabled={busy} onClick={() => mark(last.seq)}>
              mark chapter read
            </button>
          )
        )}
      </div>
    </div>
  );
}
