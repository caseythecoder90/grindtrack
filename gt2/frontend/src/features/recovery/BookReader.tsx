import { useCallback, useEffect, useRef, useState } from "react";
import { errorMessage } from "../../lib/api";
import {
  addMark,
  deleteMark,
  getChapter,
  savePlace,
  updateMark,
  MARK_COLORS,
  type ChapterText,
  type Mark,
  type MarkColor,
  type Para,
} from "./recoveryApi";

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

/** Words selected with the finger or the mouse, inside one paragraph. */
interface Selection {
  seq: number;
  start: number;
  end: number;
  text: string;
}

/** Whether the printed page turns between two paragraphs, so a rule can say so. */
function pageTurns(prev: Para | undefined, p: Para): boolean {
  return !!prev && prev.pageSeq !== p.pageSeq && !!p.pageLabel;
}

/** The first few words, for a bar or a list. */
function clip(text: string, max = 60): string {
  const one = text.replace(/\s+/g, " ").trim();
  return one.length <= max ? one : one.slice(0, max - 1).trimEnd() + "…";
}

/**
 * A paragraph with its highlights drawn in: runs of words wrapped in a mark, the whole paragraph
 * tinted when it is marked whole. Marks that overlap are drawn first-come; the text underneath is
 * exactly the body, so a selection's offsets into it are the body's.
 */
function Body({ body, marks, onMark }: { body: string; marks: Mark[]; onMark: (m: Mark) => void }) {
  const runs = marks
    .filter((m) => m.start !== null && m.end !== null && m.color)
    .sort((a, b) => a.start! - b.start!);
  if (runs.length === 0) return <>{body}</>;
  const out: React.ReactNode[] = [];
  let at = 0;
  for (const m of runs) {
    const start = Math.max(m.start!, at);
    const end = Math.min(m.end!, body.length);
    if (start >= end) continue;
    if (start > at) out.push(<span key={"t" + at}>{body.slice(at, start)}</span>);
    out.push(
      <mark
        key={"m" + m.id}
        className={"hl " + m.color}
        onClick={(e) => {
          e.stopPropagation();
          onMark(m);
        }}
      >
        {body.slice(start, end)}
      </mark>,
    );
    at = end;
  }
  if (at < body.length) out.push(<span key={"t" + at}>{body.slice(at)}</span>);
  return <>{out}</>;
}

/**
 * A chapter open to read, from anywhere in the book. Set like a page: serif, the book's own
 * paragraphs, a thin rule where a page turns. Tapping a paragraph selects it, and the bar that
 * appears offers the pencil: a highlight in one of four colours — on the words selected with a
 * long press, or the whole paragraph — a note, and "mark read to here". A note shows under its
 * paragraph; a tap on a highlight offers its note and its removal. Opening a chapter saves it as
 * your place, so the other device picks up here.
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
  const [selected, setSelected] = useState<number | null>(null);
  const [selection, setSelection] = useState<Selection | null>(null);
  /** A highlight tapped: its note and its removal are on offer. */
  const [active, setActive] = useState<Mark | null>(null);
  /** The note box: for a new mark on a paragraph (or its selection), or an existing mark. */
  const [noting, setNoting] = useState<{ seq: number; mark: Mark | null } | null>(null);
  const [noteText, setNoteText] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const top = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let live = true;
    setChapter(null);
    setSelected(null);
    setActive(null);
    setNoting(null);
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

  // Words selected inside a paragraph — a long press on a phone, a drag on a laptop — are kept,
  // with their offsets into the paragraph, until they are highlighted or another paragraph is
  // tapped. Kept here rather than read at the tap, because the tap on the button is what ends
  // the selection on some phones.
  useEffect(() => {
    const onChange = () => {
      const sel = window.getSelection();
      if (!sel || sel.isCollapsed || sel.rangeCount === 0) return;
      const range = sel.getRangeAt(0);
      const node = range.commonAncestorContainer;
      const el = node instanceof Element ? node : node.parentElement;
      const p = el?.closest("p.rec-pline") as HTMLElement | null;
      if (!p || !p.id.startsWith("para-")) return;
      const text = range.toString();
      if (!text.trim()) return;
      const before = document.createRange();
      before.selectNodeContents(p);
      before.setEnd(range.startContainer, range.startOffset);
      const start = before.toString().length;
      const seq = Number(p.id.slice("para-".length));
      setSelection({ seq, start, end: start + text.length, text });
      setSelected(seq);
      setActive(null);
    };
    document.addEventListener("selectionchange", onChange);
    return () => document.removeEventListener("selectionchange", onChange);
  }, []);

  const changed = useCallback(
    (next: Mark[]) => {
      setMarks(next);
      onMarksChanged?.();
    },
    [onMarksChanged],
  );

  async function run(work: () => Promise<void>, failing: string) {
    setBusy(true);
    setError("");
    try {
      await work();
    } catch (e) {
      setError(errorMessage(e, failing));
    } finally {
      setBusy(false);
    }
  }

  function tap(seq: number) {
    setActive(null);
    setNoting(null);
    if (selection && selection.seq !== seq) setSelection(null);
    setSelected((s) => (s === seq ? null : seq));
  }

  /** The words selected in this paragraph, or the whole of it. */
  function target(seq: number): { start: number | null; end: number | null } {
    return selection && selection.seq === seq
      ? { start: selection.start, end: selection.end }
      : { start: null, end: null };
  }

  function highlight(seq: number, color: MarkColor) {
    void run(async () => {
      const mark = await addMark({ seq, ...target(seq), color });
      changed([...marks, mark]);
      window.getSelection()?.removeAllRanges();
      setSelection(null);
      setSelected(null);
    }, "could not highlight that");
  }

  function saveNote() {
    if (!noting) return;
    const text = noteText.trim();
    const { seq, mark } = noting;
    void run(async () => {
      if (mark) {
        const updated = text ? await updateMark(mark.id, { note: text }) : await updateMark(mark.id, { clearNote: true });
        changed(updated ? marks.map((m) => (m.id === mark.id ? updated : m)) : marks.filter((m) => m.id !== mark.id));
      } else if (text) {
        const created = await addMark({ seq, ...target(seq), note: text });
        changed([...marks, created]);
        window.getSelection()?.removeAllRanges();
        setSelection(null);
      }
      setNoting(null);
      setNoteText("");
      setSelected(null);
      setActive(null);
    }, "could not save the note");
  }

  function remove(mark: Mark) {
    void run(async () => {
      await deleteMark(mark.id);
      changed(marks.filter((m) => m.id !== mark.id));
      setActive(null);
      setSelected(null);
    }, "could not remove that");
  }

  function unhighlight(mark: Mark) {
    void run(async () => {
      const updated = await updateMark(mark.id, { clearColor: true });
      changed(updated ? marks.map((m) => (m.id === mark.id ? updated : m)) : marks.filter((m) => m.id !== mark.id));
      setActive(null);
      setSelected(null);
    }, "could not remove that");
  }

  async function markRead(seq: number) {
    await run(async () => {
      await onMarkRead(seq);
      setSelected(null);
    }, "could not mark that");
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
  const byPara = new Map<number, Mark[]>();
  for (const m of marks) {
    const list = byPara.get(m.seq) ?? [];
    list.push(m);
    byPara.set(m.seq, list);
  }
  const wholeTint = (seq: number): string => {
    const whole = (byPara.get(seq) ?? []).find((m) => m.start === null && m.color);
    return whole ? " hl-" + whole.color : "";
  };

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
        {chapter.paragraphs.map((p, i) => {
          const own = byPara.get(p.seq) ?? [];
          const notes = own.filter((m) => m.note);
          return (
            <div key={p.seq}>
              {pageTurns(chapter.paragraphs[i - 1], p) && (
                <div className="rec-pagerule" aria-label={`page ${p.pageLabel}`}>
                  <span />
                  <em>{p.pageLabel}</em>
                  <span />
                </div>
              )}
              <p
                id={"para-" + p.seq}
                className={
                  "rec-pline" +
                  wholeTint(p.seq) +
                  (p.seq === focusSeq ? " hit" : "") +
                  (p.seq < chapter.cursor ? " read" : "") +
                  (p.seq === chapter.cursor ? " cursor" : "") +
                  (selected === p.seq ? " selected" : "")
                }
                onClick={() => tap(p.seq)}
              >
                <Body
                  body={p.body}
                  marks={own}
                  onMark={(m) => {
                    setSelected(p.seq);
                    setNoting(null);
                    setActive((a) => (a?.id === m.id ? null : m));
                  }}
                />
              </p>
              {notes.map((m) => (
                <div key={m.id} className={"rec-mark-note" + (m.color ? " " + m.color : "")}>
                  <span className="rec-note">
                    note{m.start !== null ? ` · “${clip(m.quote, 48)}”` : ""}
                  </span>
                  <div className="txt">{m.note}</div>
                  <div className="rec-mark-note-actions">
                    <button
                      type="button"
                      className="linkish"
                      onClick={() => {
                        setSelected(p.seq);
                        setActive(null);
                        setNoting({ seq: p.seq, mark: m });
                        setNoteText(m.note ?? "");
                      }}
                    >
                      edit
                    </button>
                    <button type="button" className="linkish" disabled={busy} onClick={() => remove(m)}>
                      remove
                    </button>
                  </div>
                </div>
              ))}
              {selected === p.seq && (
                <div className="rec-markbar" onClick={(e) => e.stopPropagation()}>
                  {noting && noting.seq === p.seq ? (
                    <div className="rec-mark-edit">
                      <textarea
                        autoFocus
                        value={noteText}
                        maxLength={4000}
                        placeholder={
                          noting.mark
                            ? "the note"
                            : selection && selection.seq === p.seq
                              ? `a note on “${clip(selection.text, 40)}”`
                              : "a note on this paragraph"
                        }
                        onChange={(e) => setNoteText(e.target.value)}
                      />
                      <div className="rec-hl-chips">
                        <button type="button" className="rec-primary" disabled={busy} onClick={saveNote}>
                          save
                        </button>
                        <button type="button" className="linkish" onClick={() => setNoting(null)}>cancel</button>
                      </div>
                    </div>
                  ) : active ? (
                    <div className="rec-hl-chips">
                      <span className="rec-note">“{clip(active.quote, 40)}”</span>
                      <button
                        type="button"
                        className="linkish"
                        onClick={() => {
                          setNoting({ seq: p.seq, mark: active });
                          setNoteText(active.note ?? "");
                        }}
                      >
                        {active.note ? "edit note" : "add a note"}
                      </button>
                      <button type="button" className="linkish" disabled={busy} onClick={() => unhighlight(active)}>
                        remove highlight
                      </button>
                    </div>
                  ) : (
                    <>
                      <div className="rec-hl-chips">
                        <span className="rec-note">
                          {selection && selection.seq === p.seq
                            ? `highlight “${clip(selection.text, 32)}”`
                            : "highlight the paragraph"}
                        </span>
                        {MARK_COLORS.map((c) => (
                          <button
                            key={c}
                            type="button"
                            className={"rec-hl-chip " + c}
                            aria-label={`highlight ${c}`}
                            disabled={busy}
                            onClick={() => highlight(p.seq, c)}
                          />
                        ))}
                        <button
                          type="button"
                          className="linkish"
                          onClick={() => {
                            setNoting({ seq: p.seq, mark: null });
                            setNoteText("");
                          }}
                        >
                          note
                        </button>
                      </div>
                      <div className="rec-hl-chips">
                        <span className="rec-note">everything up to here read today</span>
                        <button type="button" disabled={busy} onClick={() => markRead(p.seq)}>
                          mark read to here
                        </button>
                        {(byPara.get(p.seq) ?? []).some((m) => m.start === null && m.color) && (
                          <button
                            type="button"
                            className="linkish"
                            disabled={busy}
                            onClick={() => unhighlight((byPara.get(p.seq) ?? []).find((m) => m.start === null && m.color)!)}
                          >
                            remove highlight
                          </button>
                        )}
                      </div>
                    </>
                  )}
                </div>
              )}
            </div>
          );
        })}
      </div>
      {error && <div className="error">{error}</div>}
      <div className="rec-foot">
        <span className="rec-note">
          {chapter.cursor > (first?.seq ?? 0) && chapter.cursor <= (last?.seq ?? 0)
            ? "the cursor is in this chapter · tap a paragraph for the pencil"
            : "tap a paragraph to highlight it, note it, or mark read to there; hold to pick words"}
        </span>
        {last && (
          <button type="button" disabled={busy} onClick={() => markRead(last.seq)}>
            mark chapter read
          </button>
        )}
      </div>
    </div>
  );
}
