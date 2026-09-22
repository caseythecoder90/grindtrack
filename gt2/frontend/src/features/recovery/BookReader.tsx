import { useCallback, useEffect, useRef, useState } from "react";
import { errorMessage } from "../../lib/api";
import Pencil from "./Pencil";
import { getChapter, savePlace, type ChapterText, type Mark } from "./recoveryApi";

interface Props {
  chapterNo: number;
  /** A paragraph to land on and light up: a search hit, the top of a page, or a mark. */
  focusSeq?: number | null;
  onOpen: (no: number) => void;
  onClose: () => void;
  /** "Mark read to here": everything up to this paragraph counts as read, today. */
  onMarkRead: (seq: number) => Promise<void>;
  /** A highlight or a note was added, changed or removed, so the marks list is stale. */
  onMarksChanged?: () => void;
}

/**
 * A chapter open to read, from anywhere in the book. Set like a page: serif, the book's own
 * paragraphs, a thin rule where a page turns, and the pencil (see {@link Pencil}). Opening a
 * chapter saves it as your place, so the other device picks up here.
 */
export default function BookReader({
  chapterNo,
  focusSeq = null,
  onOpen,
  onClose,
  onMarkRead,
  onMarksChanged,
}: Props) {
  const [chapter, setChapter] = useState<ChapterText | null>(null);
  const [marks, setMarks] = useState<Mark[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const top = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let live = true;
    setChapter(null);
    setError("");
    getChapter(chapterNo)
      .then((c) => {
        if (!live) return;
        setChapter(c);
        setMarks(c.marks);
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
    if (!chapter) return;
    const hit = focusSeq !== null ? document.getElementById("para-" + focusSeq) : null;
    if (hit) hit.scrollIntoView({ block: "center" });
    else top.current?.scrollIntoView({ block: "start" });
  }, [chapter, focusSeq]);

  const changed = useCallback(
    (next: Mark[]) => {
      setMarks(next);
      onMarksChanged?.();
    },
    [onMarksChanged],
  );

  async function markChapter(seq: number) {
    setBusy(true);
    setError("");
    try {
      await onMarkRead(seq);
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
      <p className="rec-hint rec-pencil-hint">
        Tap a paragraph to highlight it or add a note. To mark only some words, select them first:
        hold on a phone, drag with a mouse.
      </p>
      <Pencil
        paragraphs={chapter.paragraphs}
        marks={marks}
        onMarksChanged={changed}
        focusSeq={focusSeq}
        cursor={chapter.cursor}
        onMarkRead={onMarkRead}
      />
      {error && <div className="error">{error}</div>}
      <div className="rec-foot">
        <span className="rec-note">
          {chapter.cursor > (first?.seq ?? 0) && chapter.cursor <= (last?.seq ?? 0)
            ? "the cursor is in this chapter"
            : ""}
        </span>
        {last && (
          <button type="button" disabled={busy} onClick={() => markChapter(last.seq)}>
            mark chapter read
          </button>
        )}
      </div>
    </div>
  );
}
