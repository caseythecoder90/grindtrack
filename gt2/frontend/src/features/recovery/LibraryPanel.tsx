import { useCallback, useEffect, useRef, useState } from "react";
import { errorMessage } from "../../lib/api";
import {
  getLibrary,
  importText,
  restartBible,
  restartReading,
  type ImportReport,
  type Library,
  type LibrarySlot,
  type Slot,
} from "./recoveryApi";

interface Props {
  onBack: () => void;
  /** A book changed, so the today view is stale. */
  onChanged: () => void;
}

const HINTS: Record<Slot, string> = {
  big_book:
    "One plain-text file, the whole book. Chapters are found by their headings; each day's part is whole paragraphs, as many as fit your minutes.",
  reflection:
    "One plain-text file, the whole book. Days are found by their headings (JANUARY 1 … DECEMBER 31); the today card shows the matching day.",
  meditation:
    "Twenty-Four Hours a Day or any book with one entry per date, the same shape. It shows under the timer.",
};

/**
 * Your books: where the texts come from.
 *
 * The AA books are copyrighted, so the app ships none of their text; you bring your own copy
 * once, as one text file per book, and it lives only in your database. The Bible is built in
 * because its translation is in the public domain. A dry run first, like the finance import.
 */
export default function LibraryPanel({ onBack, onChanged }: Props) {
  const [library, setLibrary] = useState<Library | null>(null);
  const [report, setReport] = useState<ImportReport | null>(null);
  const [busy, setBusy] = useState<Slot | "bible" | null>(null);
  const [error, setError] = useState("");
  const files = useRef<Partial<Record<Slot, HTMLInputElement | null>>>({});

  const load = useCallback(async () => {
    try {
      setLibrary(await getLibrary());
    } catch (e) {
      setError(errorMessage(e, "could not load your books"));
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  async function send(slot: Slot, dryRun: boolean) {
    const file = files.current[slot]?.files?.[0];
    if (!file) {
      setError("choose the text file first");
      return;
    }
    setBusy(slot);
    setError("");
    setReport(null);
    try {
      const res = await importText(slot, file, dryRun);
      setReport(res);
      if (!dryRun) {
        const input = files.current[slot];
        if (input) input.value = "";
        load();
        onChanged();
      }
    } catch (e) {
      setError(errorMessage(e, "could not read that file"));
    } finally {
      setBusy(null);
    }
  }

  async function startOver() {
    if (!window.confirm("Back to the first paragraph of the book?")) return;
    setError("");
    try {
      await restartReading();
      onChanged();
    } catch (e) {
      setError(errorMessage(e, "could not restart the reading"));
    }
  }

  async function restartPlan() {
    if (!window.confirm("Start the passages again from the first one, today?")) return;
    setBusy("bible");
    setError("");
    try {
      setLibrary(await restartBible());
      onChanged();
    } catch (e) {
      setError(errorMessage(e, "could not restart the plan"));
    } finally {
      setBusy(null);
    }
  }

  function state(s: LibrarySlot): string {
    if (!s.imported) return s.slot === "meditation" ? "optional" : "not imported";
    if (s.slot === "big_book") return `${s.paragraphs.toLocaleString()} paragraphs · ${s.words.toLocaleString()} words`;
    return `${s.entries} days`;
  }

  return (
    <div className="panel rec-library">
      <div className="panelhead">
        <h2>your books</h2>
        <button type="button" className="linkish" onClick={onBack}>
          ‹ back
        </button>
      </div>
      <p className="rec-hint">
        The AA books are yours to bring: one plain-text file per book, the whole book, once. They live
        only in your database. The Bible is built in.
      </p>
      {error && <div className="error">{error}</div>}

      {library?.slots.map((s) => (
        <section key={s.slot} className="rec-book">
          <div className="rec-cardhead">
            <span>{s.title ?? s.defaultTitle}</span>
            <span className={"rec-note" + (s.imported ? " ok" : "")}>{state(s)}</span>
          </div>
          <p className="rec-hint">{HINTS[s.slot]}</p>
          <div className="rec-import">
            <input
              type="file"
              accept=".txt,text/plain"
              aria-label={`Text file for ${s.defaultTitle}`}
              ref={(el) => {
                files.current[s.slot] = el;
              }}
            />
            <button type="button" disabled={busy !== null} onClick={() => send(s.slot, true)}>
              dry run
            </button>
            <button type="button" className="rec-primary" disabled={busy !== null} onClick={() => send(s.slot, false)}>
              {s.imported ? "replace" : "import"}
            </button>
            {s.slot === "big_book" && s.imported && (
              <button type="button" className="linkish" onClick={startOver}>
                start the book over
              </button>
            )}
          </div>
          {report?.slot === s.slot && <Report report={report} />}
        </section>
      ))}

      {library && (
        <section className="rec-book bible">
          <div className="rec-cardhead">
            <span>Bible</span>
            <span className="rec-note ok">
              {library.bible.verses > 0
                ? `built in · ${library.bible.verses.toLocaleString()} verses`
                : "not seeded yet"}
            </span>
          </div>
          <p className="rec-hint">
            {library.bible.name} ({library.bible.abbrev}), public domain. One short passage a day, in
            order through the Gospels, Psalms, Proverbs and the letters —{" "}
            {library.bible.passages.toLocaleString()} passages, started {library.biblePlanStart}.
            Nothing to upload.
          </p>
          <div className="rec-import">
            <button type="button" className="linkish" disabled={busy !== null} onClick={restartPlan}>
              restart the plan from today
            </button>
          </div>
        </section>
      )}
    </div>
  );
}

function Report({ report }: { report: ImportReport }) {
  return (
    <div className="rec-report">
      <div className="rec-note">
        {report.dryRun ? "dry run — nothing written" : "imported"} · {report.title}
        {report.chapters.length > 0 && ` · ${report.chapters.length} chapters · ${report.paragraphs.toLocaleString()} paragraphs · ${report.words.toLocaleString()} words`}
        {report.entries > 0 && ` · ${report.entries} days` + (report.missingCount > 0 ? `, ${report.missingCount} missing` : ", none missing")}
        {report.cursorReset && " · the reading starts over"}
      </div>
      {report.chapters.length > 0 && (
        <ol className="rec-report-chapters">
          {report.chapters.map((c) => (
            <li key={c.no}>
              <span>{c.title}</span>
              <span className="rec-note">{c.paragraphs} ¶ · {c.words.toLocaleString()} w</span>
            </li>
          ))}
        </ol>
      )}
      {report.missing.length > 0 && (
        <div className="rec-note">missing: {report.missing.join(", ")}{report.missingCount > report.missing.length ? ", …" : ""}</div>
      )}
      {report.warnings.map((w) => (
        <div key={w} className="rec-warning">{w}</div>
      ))}
      <blockquote className="rec-sample">{report.sample}</blockquote>
    </div>
  );
}
