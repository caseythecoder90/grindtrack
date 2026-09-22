import { useEffect, useRef, useState, type ReactNode } from "react";
import { errorMessage } from "../../lib/api";
import {
  addMark,
  deleteMark,
  updateMark,
  MARK_COLORS,
  type Mark,
  type MarkColor,
  type Para,
} from "./recoveryApi";

interface Props {
  paragraphs: Para[];
  /** The marks on these paragraphs. */
  marks: Mark[];
  /** The marks on these paragraphs after an add, a change or a removal. */
  onMarksChanged: (next: Mark[]) => void;
  /** A paragraph to light up: a search hit, the top of a page. */
  focusSeq?: number | null;
  /** The reading cursor, to dim what is read and mark where it is; null for none. */
  cursor?: number | null;
  /** "Mark read to here", when the surrounding card offers it. */
  onMarkRead?: (seq: number) => Promise<void>;
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

/** The words selected in this container, inside one paragraph, with offsets into its text. */
function selectionIn(container: HTMLElement): Selection | null {
  const sel = window.getSelection();
  if (!sel || sel.isCollapsed || sel.rangeCount === 0) return null;
  const range = sel.getRangeAt(0);
  const node = range.commonAncestorContainer;
  const el = node instanceof Element ? node : node.parentElement;
  const p = el?.closest("p.rec-pline") as HTMLElement | null;
  if (!p || !container.contains(p) || !p.id.startsWith("para-")) return null;
  const text = range.toString();
  if (!text.trim()) return null;
  const before = document.createRange();
  before.selectNodeContents(p);
  before.setEnd(range.startContainer, range.startOffset);
  const start = before.toString().length;
  return { seq: Number(p.id.slice("para-".length)), start, end: start + text.length, text };
}

/**
 * A paragraph with its highlights drawn in: runs of words wrapped in a mark. The text underneath
 * is exactly the body, so a selection's offsets into it are the body's.
 */
function Body({ body, marks, onMark }: { body: string; marks: Mark[]; onMark: (m: Mark) => void }) {
  const runs = marks
    .filter((m) => m.start !== null && m.end !== null && m.color)
    .sort((a, b) => a.start! - b.start!);
  if (runs.length === 0) return <>{body}</>;
  const out: ReactNode[] = [];
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
 * The book's paragraphs with the pencil: tap one and a bar offers four colours and a note; select
 * words first (hold on a phone, drag with a mouse) and the highlight is on those words, otherwise
 * on the whole paragraph. A note shows under its paragraph; a tap on a highlight offers its note
 * and its removal. Used by the chapter reader and by today's pages, so the pencil is wherever the
 * book is.
 */
export default function Pencil({
  paragraphs,
  marks,
  onMarksChanged,
  focusSeq = null,
  cursor = null,
  onMarkRead,
}: Props) {
  const [selected, setSelected] = useState<number | null>(null);
  const [selection, setSelection] = useState<Selection | null>(null);
  /** A highlight tapped: its note and its removal are on offer. */
  const [active, setActive] = useState<Mark | null>(null);
  /** The note box: for a new mark on a paragraph (or its selection), or an existing mark. */
  const [noting, setNoting] = useState<{ seq: number; mark: Mark | null } | null>(null);
  const [noteText, setNoteText] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const box = useRef<HTMLDivElement>(null);

  // Words selected inside a paragraph are kept, with their offsets, until they are highlighted or
  // another paragraph is tapped. Kept here rather than read at the tap, because on some phones the
  // tap on the colour is what ends the selection.
  useEffect(() => {
    const onChange = () => {
      const el = box.current;
      if (!el) return;
      const found = selectionIn(el);
      if (!found) return;
      setSelection(found);
      setSelected(found.seq);
      setActive(null);
    };
    document.addEventListener("selectionchange", onChange);
    return () => document.removeEventListener("selectionchange", onChange);
  }, []);

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
    // A mouse drag that selected words ends in a click on the same paragraph. That click is the
    // end of the selecting, not a tap to close the bar it just opened.
    const el = box.current;
    const dragged = el ? selectionIn(el) : null;
    if (dragged && dragged.seq === seq) {
      setSelection(dragged);
      setSelected(seq);
      setActive(null);
      return;
    }
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

  function close() {
    window.getSelection()?.removeAllRanges();
    setSelection(null);
    setSelected(null);
    setActive(null);
    setNoting(null);
    setNoteText("");
  }

  function highlight(seq: number, color: MarkColor) {
    void run(async () => {
      const mark = await addMark({ seq, ...target(seq), color });
      onMarksChanged([...marks, mark]);
      close();
    }, "could not highlight that");
  }

  function saveNote() {
    if (!noting) return;
    const text = noteText.trim();
    const { seq, mark } = noting;
    void run(async () => {
      if (mark) {
        const updated = text
          ? await updateMark(mark.id, { note: text })
          : await updateMark(mark.id, { clearNote: true });
        onMarksChanged(
          updated ? marks.map((m) => (m.id === mark.id ? updated : m)) : marks.filter((m) => m.id !== mark.id),
        );
      } else if (text) {
        onMarksChanged([...marks, await addMark({ seq, ...target(seq), note: text })]);
      }
      close();
    }, "could not save the note");
  }

  function remove(mark: Mark) {
    void run(async () => {
      await deleteMark(mark.id);
      onMarksChanged(marks.filter((m) => m.id !== mark.id));
      close();
    }, "could not remove that");
  }

  function unhighlight(mark: Mark) {
    void run(async () => {
      const updated = await updateMark(mark.id, { clearColor: true });
      onMarksChanged(
        updated ? marks.map((m) => (m.id === mark.id ? updated : m)) : marks.filter((m) => m.id !== mark.id),
      );
      close();
    }, "could not remove that");
  }

  function markRead(seq: number) {
    if (!onMarkRead) return;
    void run(async () => {
      await onMarkRead(seq);
      close();
    }, "could not mark that");
  }

  const byPara = new Map<number, Mark[]>();
  for (const m of marks) {
    const list = byPara.get(m.seq) ?? [];
    list.push(m);
    byPara.set(m.seq, list);
  }
  const wholeOf = (seq: number) => (byPara.get(seq) ?? []).find((m) => m.start === null && m.color);

  return (
    <div className="rec-serif rec-reader-text rec-pencil" ref={box}>
      {paragraphs.map((p, i) => {
        const own = byPara.get(p.seq) ?? [];
        const notes = own.filter((m) => m.note);
        const whole = wholeOf(p.seq);
        const picked = selection && selection.seq === p.seq ? selection : null;
        return (
          <div key={p.seq}>
            {pageTurns(paragraphs[i - 1], p) && (
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
                (whole ? " hl-" + whole.color : "") +
                (p.seq === focusSeq ? " hit" : "") +
                (cursor !== null && p.seq < cursor ? " read" : "") +
                (cursor !== null && p.seq === cursor ? " cursor" : "") +
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
                <span className="rec-note">note{m.start !== null ? ` · “${clip(m.quote, 48)}”` : ""}</span>
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
                      aria-label="Note"
                      placeholder={
                        noting.mark
                          ? "the note"
                          : picked
                            ? `a note on “${clip(picked.text, 40)}”`
                            : "a note on this paragraph"
                      }
                      onChange={(e) => setNoteText(e.target.value)}
                    />
                    <div className="rec-hl-chips">
                      <button type="button" className="rec-primary" disabled={busy} onClick={saveNote}>
                        save note
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
                        {picked ? `highlight “${clip(picked.text, 32)}”` : "highlight the paragraph"}
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
                    </div>
                    <div className="rec-hl-chips">
                      <button
                        type="button"
                        onClick={() => {
                          setNoting({ seq: p.seq, mark: null });
                          setNoteText("");
                        }}
                      >
                        add a note
                      </button>
                      {whole && (
                        <button type="button" className="linkish" disabled={busy} onClick={() => unhighlight(whole)}>
                          remove highlight
                        </button>
                      )}
                      {onMarkRead && (
                        <button type="button" className="linkish" disabled={busy} onClick={() => markRead(p.seq)}>
                          mark read to here
                        </button>
                      )}
                    </div>
                  </>
                )}
                {error && <div className="error">{error}</div>}
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}
