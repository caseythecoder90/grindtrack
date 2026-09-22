import { useCallback, useEffect, useState } from "react";
import Segmented from "../../components/Segmented";
import { errorMessage } from "../../lib/api";
import { useAppResume } from "../../lib/resume";
import BibleBrowser, { type Place as BiblePlace } from "./BibleBrowser";
import BookReader from "./BookReader";
import BookSearch from "./BookSearch";
import JournalComposer from "./JournalComposer";
import LibraryPanel from "./LibraryPanel";
import MeditationTimer from "./MeditationTimer";
import PeoplePanel from "./PeoplePanel";
import Pencil from "./Pencil";
import {
  addJournal,
  catchUp,
  deleteJournal,
  explainPassage,
  finishReading,
  getJournal,
  getMarks,
  getPeople,
  getToday,
  logSession,
  markReadTo,
  nextPassage,
  updateSettings,
  type DailyEntry,
  type JournalEntry,
  type Mark,
  type Passage,
  type Person,
  type Reading,
  type RecoveryToday,
} from "./recoveryApi";
import Verses from "./Verses";

type View = "today" | "read" | "journal" | "people" | "books";

/** What the read view shows: the book on the plan, or the Bible. */
type Shelf = "book" | "bible";

const VIEWS: { value: View; label: string; tone?: string }[] = [
  { value: "today", label: "today", tone: "rec" },
  { value: "read", label: "read", tone: "rec" },
  { value: "journal", label: "journal", tone: "rec" },
  { value: "people", label: "people", tone: "rec" },
];

/** "September 13" from an ISO date. */
function longDay(iso: string): string {
  const [y, m, d] = iso.split("-").map(Number);
  return new Date(y, m - 1, d).toLocaleDateString(undefined, { month: "long", day: "numeric" });
}

/** "Sep 5, 2024" from an ISO date. */
function shortDay(iso: string): string {
  const [y, m, d] = iso.split("-").map(Number);
  return new Date(y, m - 1, d).toLocaleDateString(undefined, { month: "short", day: "numeric", year: "numeric" });
}

/** "07:41" in the viewer's zone — the 24-hour form, because it fits the column. */
function timeOf(iso: string): string {
  return new Date(iso).toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit", hour12: false });
}

function localDate(iso: string): string {
  const d = new Date(iso);
  const pad = (n: number) => (n < 10 ? "0" + n : "" + n);
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** "today · day 739", "yesterday", "Thursday", "Sep 3". */
function dayLabel(date: string, today: string, dayNumber: number | null): string {
  if (date === today) return dayNumber ? `today · day ${dayNumber}` : "today";
  const [y, m, d] = date.split("-").map(Number);
  const then = new Date(y, m - 1, d);
  const [ty, tm, td] = today.split("-").map(Number);
  const now = new Date(ty, tm - 1, td);
  const back = Math.round((now.getTime() - then.getTime()) / 86_400_000);
  if (back === 1) return "yesterday";
  if (back < 7) return then.toLocaleDateString(undefined, { weekday: "long" });
  return then.toLocaleDateString(undefined, { month: "short", day: "numeric" });
}

/** "pp. 58–59", "p. 58", or the chapter when the book has no page numbers. */
function pageRange(r: Reading): string {
  if (!r.pageFrom) return r.chapterTitle;
  return r.pageFrom === r.pageTo ? `p. ${r.pageFrom}` : `pp. ${r.pageFrom}–${r.pageTo}`;
}

/** The chapter a paragraph is in: the last chapter that starts at or before it. */
function chapterOf(r: Reading, seq: number): number | null {
  let found: number | null = null;
  for (const c of r.chapters) {
    if (c.firstSeq <= seq) found = c.no;
  }
  return found;
}

/**
 * The recovery tab: the number, the day's readings, the timer, the book, the journal, the
 * people.
 *
 * Four views on a phone — today, read, journal, people — and three columns on a desktop, the
 * same markup either way; styles.css decides which. "Your books" is the fifth screen, where the
 * texts are imported.
 */
export default function RecoveryPage() {
  const [view, setView] = useState<View>("today");
  const [data, setData] = useState<RecoveryToday | null>(null);
  const [people, setPeople] = useState<Person[]>([]);
  const [marks, setMarks] = useState<Mark[]>([]);
  const [entries, setEntries] = useState<JournalEntry[]>([]);
  const [exhausted, setExhausted] = useState(false);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [focusKey, setFocusKey] = useState(0);
  const [pagesOpen, setPagesOpen] = useState(false);
  /** A chapter open in the reader, or null for today's part. */
  const [openChapter, setOpenChapter] = useState<number | null>(null);
  /** A paragraph to land on in the reader: a search hit, or the top of a page. */
  const [focusSeq, setFocusSeq] = useState<number | null>(null);
  const [shelf, setShelf] = useState<Shelf>("book");
  /** Where the Bible is open, or null for the books. */
  const [biblePlace, setBiblePlace] = useState<BiblePlace | null>(null);

  const load = useCallback(async () => {
    setError("");
    try {
      setData(await getToday());
    } catch (e) {
      setError(errorMessage(e, "could not load the page"));
    }
  }, []);

  const loadPeople = useCallback(async () => {
    try {
      setPeople(await getPeople());
    } catch {
      /* the list is the people view's; the today view must not wait on it */
    }
  }, []);

  const loadMarks = useCallback(async () => {
    try {
      setMarks(await getMarks());
    } catch {
      /* the list is the read view's; the today view must not wait on it */
    }
  }, []);

  const loadJournal = useCallback(async () => {
    try {
      const page = await getJournal();
      setEntries(page);
      setExhausted(page.length < 50);
    } catch {
      /* the list is the journal view's; the today view must not wait on it */
    }
  }, []);

  useEffect(() => {
    load();
    loadPeople();
    loadJournal();
    loadMarks();
  }, [load, loadPeople, loadJournal, loadMarks]);

  // Marked read on the phone, then opened on the laptop: the laptop should know.
  useAppResume(() => {
    load();
    loadPeople();
    loadJournal();
    loadMarks();
  });

  function peopleChanged() {
    loadPeople();
    load();
  }

  async function done() {
    setBusy(true);
    setError("");
    try {
      setData(await finishReading());
    } catch (e) {
      setError(errorMessage(e, "could not mark that read"));
    } finally {
      setBusy(false);
    }
  }

  async function markTo(seq: number) {
    setData(await markReadTo(seq));
  }

  async function forgive() {
    if (!window.confirm("Forgive the backlog? Tomorrow owes the usual pages again.")) return;
    setBusy(true);
    setError("");
    try {
      setData(await catchUp());
    } catch (e) {
      setError(errorMessage(e, "could not catch up"));
    } finally {
      setBusy(false);
    }
  }

  async function changePages(n: number) {
    setPagesOpen(false);
    try {
      await updateSettings({ pagesPerDay: n });
      load();
    } catch (e) {
      setError(errorMessage(e, "could not change the pages"));
    }
  }

  const sat = useCallback(async (minutes: number, completed: boolean) => {
    try {
      const m = await logSession(minutes, completed);
      setData((d) => (d ? { ...d, meditation: m, settings: { ...d.settings, meditationMinutes: minutes } } : d));
    } catch (e) {
      setError(errorMessage(e, "could not log the sitting"));
    }
  }, []);

  async function save(body: string, spoken: boolean) {
    const entry = await addJournal(body, spoken);
    setEntries((list) => [entry, ...list]);
  }

  async function remove(entry: JournalEntry) {
    if (!window.confirm("Delete this entry?")) return;
    try {
      await deleteJournal(entry.id);
      setEntries((list) => list.filter((e) => e.id !== entry.id));
    } catch (e) {
      setError(errorMessage(e, "could not delete that"));
    }
  }

  async function older() {
    const last = entries[entries.length - 1];
    if (!last) return;
    try {
      const page = await getJournal(last.id);
      setEntries((list) => [...list, ...page]);
      setExhausted(page.length < 50);
    } catch (e) {
      setError(errorMessage(e, "could not load older entries"));
    }
  }

  function open(no: number, seq: number | null = null) {
    setOpenChapter(no);
    setFocusSeq(seq);
    setShelf("book");
    setView("read");
  }

  function openBible(book: string, chapter: number) {
    setBiblePlace({ book, chapter, verse: null });
    setShelf("bible");
    setView("read");
  }

  async function another() {
    setBusy(true);
    setError("");
    try {
      setData(await nextPassage());
    } catch (e) {
      setError(errorMessage(e, "could not move on"));
    } finally {
      setBusy(false);
    }
  }

  if (view === "books") {
    return <LibraryPanel onBack={() => setView("today")} onChanged={load} />;
  }

  const today = data?.today ?? localDate(new Date().toISOString());
  const dayNumber = data?.number?.days ?? null;
  const reading = data?.reading ?? null;

  return (
    <div className={"recpage view-" + view}>
      <div className="rec-head">
        <Segmented label="Recovery view" value={view} onChange={setView} options={VIEWS} />
        <button type="button" className="linkish" onClick={() => setView("books")}>
          books ›
        </button>
      </div>
      {error && <div className="error">{error}</div>}

      <div className="rec-cols">
        {/* ---- today ---- */}
        <section className="rec-col rec-today" aria-label="Today">
          {data?.number ? (
            <div className="rec-card number">
              <div>
                <div className="rec-days">{data.number.days.toLocaleString()}</div>
                <div className="rec-note">days · since {shortDay(data.number.sobrietyDate)}</div>
              </div>
              <div className="rec-right">
                <div>{data.number.spelledOut}</div>
                <div className="rec-note">
                  {data.number.nextMilestoneLabel} in {data.number.daysToMilestone} day{data.number.daysToMilestone === 1 ? "" : "s"}
                </div>
              </div>
            </div>
          ) : (
            data && <div className="rec-card"><span className="rec-note">no sobriety date is set — docs/deployment.md has the one line</span></div>
          )}

          <PeoplePanel people={people} onChanged={peopleChanged} compact />

          <Daily label="daily reflection" entry={data?.reflection ?? null} date={today}
            missing="your copy of Daily Reflections is not imported yet" onBooks={() => setView("books")} />

          <PassageCard passage={data?.passage ?? null} busy={busy} onAnother={another} onOpen={openBible} />

          {data && (
            <MeditationTimer
              minutes={data.settings.meditationMinutes}
              streak={data.meditation.streak}
              doneToday={data.meditation.doneToday}
              onDone={sat}
            />
          )}

          {data?.meditationEntry && (
            <Daily label="meditation reading" entry={data.meditationEntry} date={today} missing="" onBooks={() => setView("books")} />
          )}

          <BookCard reading={reading} loaded={!!data} onRead={() => { setOpenChapter(null); setView("read"); }} onBooks={() => setView("books")} />

          {/* The door to the journal: tapping it opens the journal view with the cursor in the
              box. A phone-only thing; a desktop has the composer in view already. */}
          <button
            type="button"
            className="rec-door"
            onClick={() => {
              setView("journal");
              setFocusKey((k) => k + 1);
            }}
          >
            <span>how are you today?</span>
            <span className="rec-door-icons" aria-hidden="true">
              <span className="round" />
              <span className="round send" />
            </span>
          </button>
        </section>

        {/* ---- read ---- */}
        <section className="rec-col rec-read" aria-label="Read">
          <div className="rec-shelf">
            <Segmented
              label="Text"
              value={shelf}
              onChange={setShelf}
              options={[
                { value: "book", label: reading?.bookTitle ?? "big book", tone: "rec" },
                { value: "bible", label: "bible", tone: "rec" },
              ]}
            />
          </div>
          {shelf === "bible" ? (
            <BibleBrowser place={biblePlace} onPlace={setBiblePlace} />
          ) : reading ? (
            <>
              <BookSearch onOpen={(no, seq) => open(no, seq)} />
              {openChapter !== null ? (
                <BookReader
                  chapterNo={openChapter}
                  focusSeq={focusSeq}
                  onOpen={(no) => open(no)}
                  onClose={() => setOpenChapter(null)}
                  onMarkRead={markTo}
                  onMarksChanged={loadMarks}
                />
              ) : (
                <ReadCard
                  reading={reading}
                  marks={marks.filter((m) => reading.paragraphs.some((p) => p.seq === m.seq))}
                  onMarksChanged={(next) => {
                    const here = new Set(reading.paragraphs.map((p) => p.seq));
                    setMarks((all) =>
                      [...all.filter((m) => !here.has(m.seq)), ...next].sort(
                        (a, b) => a.seq - b.seq || (a.start ?? -1) - (b.start ?? -1) || a.id - b.id,
                      ),
                    );
                    void loadMarks();
                  }}
                  busy={busy}
                  pagesOpen={pagesOpen}
                  onPages={() => setPagesOpen(true)}
                  onPagesPicked={changePages}
                  onDone={done}
                  onCatchUp={forgive}
                />
              )}
              <Contents reading={reading} openChapter={openChapter} onOpen={(no) => open(no)} />
              <MarksCard marks={marks} onOpen={(no, seq) => open(no, seq)} />
            </>
          ) : (
            data && (
              <div className="rec-card">
                <span className="rec-lbl">big book</span>
                <p className="rec-hint">
                  Not imported yet. Bring your own copy, the PDFs all together, on{" "}
                  <button type="button" className="linkish inline" onClick={() => setView("books")}>your books ›</button>
                </p>
              </div>
            )
          )}
        </section>

        {/* ---- journal ---- */}
        <section className="rec-col rec-journal" aria-label="Journal">
          <div className="rec-entries">
            {entries.length === 0 && <div className="empty">nothing written yet — the box below is the whole feature</div>}
            {entries.map((e, i) => {
              const date = localDate(e.createdAt);
              const first = i === 0 || localDate(entries[i - 1].createdAt) !== date;
              return (
                <div key={e.id}>
                  {first && (
                    <div className="rec-day">
                      <span className="txt">{dayLabel(date, today, dayNumber)}</span>
                      <span className="rule" />
                    </div>
                  )}
                  <div className="rec-entry">
                    <span className="when">{timeOf(e.createdAt)}</span>
                    <div className="body">
                      {e.body}
                      {e.spoken && <span className="spoken"> · spoken</span>}
                    </div>
                    <button type="button" className="ghost" aria-label="Delete this entry" onClick={() => remove(e)}>×</button>
                  </div>
                </div>
              );
            })}
            {!exhausted && entries.length > 0 && (
              <button type="button" className="linkish" onClick={older}>older entries</button>
            )}
          </div>
          <JournalComposer onSave={save} focusKey={focusKey} />
        </section>

        {/* ---- people ---- */}
        <section className="rec-col rec-people" aria-label="People">
          <PeoplePanel people={people} onChanged={peopleChanged} />
        </section>
      </div>
    </div>
  );
}

function Daily({ label, entry, date, missing, onBooks }: {
  label: string; entry: DailyEntry | null; date: string; missing: string; onBooks: () => void;
}) {
  const [open, setOpen] = useState(false);
  if (!entry) {
    if (!missing) return null;
    return (
      <div className="rec-card">
        <div className="rec-cardhead">
          <span className="rec-lbl">{label}</span>
          <span className="rec-note">{longDay(date)}</span>
        </div>
        <p className="rec-hint">
          {missing} ·{" "}
          <button type="button" className="linkish inline" onClick={onBooks}>your books ›</button>
        </p>
      </div>
    );
  }
  const long = entry.body.length > 600;
  return (
    <div className="rec-card">
      <div className="rec-cardhead">
        <span className="rec-lbl">{label}</span>
        <span className="rec-note">{longDay(date)}</span>
      </div>
      {entry.title && <div className="rec-title">{entry.title}</div>}
      <p className={"rec-prose" + (long && !open ? " clipped" : "")}>{entry.body}</p>
      {long && (
        <div className="rec-foot right">
          <button type="button" className="linkish" onClick={() => setOpen((o) => !o)}>
            {open ? "less" : "read the page ›"}
          </button>
        </div>
      )}
    </div>
  );
}

function PassageCard({ passage, busy, onAnother, onOpen }: {
  passage: Passage | null; busy: boolean; onAnother: () => void; onOpen: (book: string, chapter: number) => void;
}) {
  const [note, setNote] = useState<{ reference: string; body: string } | null>(null);
  const [asking, setAsking] = useState(false);
  const [error, setError] = useState("");
  if (!passage) return null;
  // The note kept for this passage, or the one just written for it; a different passage starts over.
  const explanation = note?.reference === passage.reference ? note.body : passage.explanation;

  async function explain() {
    setAsking(true);
    setError("");
    try {
      setNote(await explainPassage());
    } catch (e) {
      setError(errorMessage(e, "could not explain it"));
    } finally {
      setAsking(false);
    }
  }

  return (
    <div className="rec-card">
      <div className="rec-cardhead">
        <span className="rec-lbl">today's passage</span>
        <span className="rec-note">{passage.reference} · {passage.translation}</span>
      </div>
      <Verses verses={passage.verses} />
      {explanation ? (
        <div className="rec-explain">
          <div className="rec-note">what it means</div>
          <p className="rec-prose">{explanation}</p>
        </div>
      ) : (
        error && <div className="error">{error}</div>
      )}
      <div className="rec-foot">
        <span className="rec-note">
          passage {passage.position} of {passage.planSize.toLocaleString()} ·{" "}
          <button type="button" className="linkish inline" onClick={() => onOpen(passage.book, passage.chapter)}>read on ›</button>
        </span>
        <span className="rec-passage-actions">
          {!explanation && (
            <button type="button" disabled={asking} onClick={explain}>
              {asking ? "thinking…" : "what does it mean?"}
            </button>
          )}
          <button type="button" disabled={busy} onClick={onAnother}>another passage ›</button>
        </span>
      </div>
    </div>
  );
}

/** The today card: what the book owes today, in pages, and whether it is done. */
function BookCard({ reading, loaded, onRead, onBooks }: {
  reading: Reading | null; loaded: boolean; onRead: () => void; onBooks: () => void;
}) {
  if (!reading) {
    if (!loaded) return null;
    return (
      <div className="rec-card">
        <span className="rec-lbl">big book</span>
        <p className="rec-hint">
          Not imported yet ·{" "}
          <button type="button" className="linkish inline" onClick={onBooks}>your books ›</button>
        </p>
      </div>
    );
  }
  const owed = reading.doneToday
    ? "read today ✓"
    : `${reading.pagesDue} page${reading.pagesDue === 1 ? "" : "s"}` +
      (reading.pagesCarried > 0 ? ` · ${reading.pagesCarried} carried over` : "");
  return (
    <div className="rec-card">
      <div className="rec-cardhead">
        <span className="rec-lbl">{reading.bookTitle}</span>
        <span className="rec-note">day {reading.dayNumber}{reading.readThroughs > 0 ? ` · read ${reading.readThroughs}×` : ""}</span>
      </div>
      <div className="rec-title ellipsis">
        {pageRange(reading)} <span className="muted">· {reading.chapterTitle}</span>
      </div>
      <div className="rec-meter"><i style={{ width: `${reading.percent}%` }} /></div>
      <div className="rec-foot">
        <span className={"rec-note" + (reading.pagesCarried > 0 ? " warn" : "")}>{reading.percent}% read · {owed}</span>
        <button type="button" className={reading.doneToday ? "" : "rec-primary"} onClick={onRead}>
          {reading.doneToday ? "read on" : "read now"}
        </button>
      </div>
    </div>
  );
}

/** Today's part, set like a page, with the pages owed and the button that clears them. */
function ReadCard({ reading, marks, onMarksChanged, busy, pagesOpen, onPages, onPagesPicked, onDone, onCatchUp }: {
  reading: Reading; marks: Mark[]; onMarksChanged: (next: Mark[]) => void; busy: boolean;
  pagesOpen: boolean; onPages: () => void;
  onPagesPicked: (n: number) => void; onDone: () => void; onCatchUp: () => void;
}) {
  return (
    <div className="rec-card rec-pages rec-reader">
      <div className="rec-cardhead">
        <span className="rec-lbl">
          {reading.chapterNo > 0 && `chapter ${reading.chapterNo} · `}{reading.chapterTitle}
        </span>
        <span className="rec-note">day {reading.dayNumber} · {pageRange(reading)}</span>
      </div>
      {reading.pagesCarried > 0 && !reading.doneToday && (
        <div className="rec-carry">
          <span>
            {reading.pagesDue} pages today: {reading.pagesPerDay} for today and {reading.pagesCarried} carried over from the days missed.
          </span>
          <button type="button" className="linkish" disabled={busy} onClick={onCatchUp}>catch up from here</button>
        </div>
      )}
      <p className="rec-hint rec-pencil-hint">
        Tap a paragraph to highlight it or add a note. To mark only some words, select them first:
        hold on a phone, drag with a mouse.
      </p>
      <Pencil paragraphs={reading.paragraphs} marks={marks} onMarksChanged={onMarksChanged} />
      <div className="rec-foot">
        <span className="rec-note">
          {pagesOpen ? (
            <select
              aria-label="Pages a day"
              autoFocus
              defaultValue={reading.pagesPerDay}
              onChange={(e) => onPagesPicked(Number(e.target.value))}
              onBlur={(e) => onPagesPicked(Number(e.target.value))}
            >
              {[1, 2, 3, 4, 5, 6, 8, 10].map((n) => (
                <option key={n} value={n}>{n} page{n === 1 ? "" : "s"}</option>
              ))}
            </select>
          ) : (
            <>
              {reading.pagesPerDay} page{reading.pagesPerDay === 1 ? "" : "s"} a day ·{" "}
              <button type="button" className="linkish inline" onClick={onPages}>change</button>
            </>
          )}
        </span>
        {reading.doneToday ? (
          <span className="rec-note ok">read today ✓</span>
        ) : (
          <button type="button" className="rec-primary" disabled={busy} onClick={onDone}>
            done for today
          </button>
        )}
      </div>
    </div>
  );
}

/** The table of contents: every chapter with its pages, and where the cursor is. */
function Contents({ reading, openChapter, onOpen }: {
  reading: Reading; openChapter: number | null; onOpen: (no: number) => void;
}) {
  const placeChapter = reading.place !== null ? chapterOf(reading, reading.place) : null;
  return (
    <div className="rec-card">
      <div className="rec-cardhead">
        <span className="rec-lbl">contents</span>
        <span className="rec-note">{reading.pageCount} pages · {reading.percent}% read</span>
      </div>
      {placeChapter !== null && placeChapter !== openChapter && (
        <div className="rec-foot" style={{ marginTop: 6 }}>
          <span className="rec-note">your place</span>
          <button type="button" className="linkish" onClick={() => onOpen(placeChapter)}>
            continue from {reading.chapters.find((c) => c.no === placeChapter)?.title} ›
          </button>
        </div>
      )}
      <div className="rec-chapters">
        {reading.chapters.map((c) => (
          <button
            type="button"
            key={c.no}
            className={"rec-chap " + c.state + (c.no === openChapter ? " open" : "")}
            onClick={() => onOpen(c.no)}
          >
            <span className="tick" aria-hidden="true" />
            <span className="ellipsis">{c.title}</span>
            <span className="rec-note right">
              {c.firstPage && (c.firstPage === c.lastPage ? c.firstPage : `${c.firstPage}–${c.lastPage}`)}
            </span>
          </button>
        ))}
      </div>
    </div>
  );
}

/** The first few words, for a list. */
function clipWords(text: string, max: number): string {
  const one = text.replace(/\s+/g, " ").trim();
  return one.length <= max ? one : one.slice(0, max - 1).trimEnd() + "…";
}

/** Every highlight and note in the book, in reading order, by chapter: the pencil's index. */
function MarksCard({ marks, onOpen }: { marks: Mark[]; onOpen: (no: number, seq: number) => void }) {
  const [open, setOpen] = useState(false);
  if (marks.length === 0) return null;
  const notes = marks.filter((m) => m.note).length;
  const highlights = marks.filter((m) => m.color).length;
  const shown = open ? marks : marks.slice(0, 5);
  let lastChapter = -1;
  return (
    <div className="rec-card">
      <div className="rec-cardhead">
        <span className="rec-lbl">your marks</span>
        <span className="rec-note">
          {highlights > 0 && `${highlights} highlight${highlights === 1 ? "" : "s"}`}
          {highlights > 0 && notes > 0 && " · "}
          {notes > 0 && `${notes} note${notes === 1 ? "" : "s"}`}
        </span>
      </div>
      <div className="rec-marks">
        {shown.map((m) => {
          const head = m.chapterNo !== lastChapter;
          lastChapter = m.chapterNo;
          return (
            <div key={m.id}>
              {head && <div className="rec-note rec-marks-chapter">{m.chapterTitle}</div>}
              <button type="button" className={"rec-mark" + (m.color ? " " + m.color : "")} onClick={() => onOpen(m.chapterNo, m.seq)}>
                <span className="rec-mark-quote">“{clipWords(m.quote, 110)}”</span>
                {m.note && <span className="rec-mark-text">{clipWords(m.note, 140)}</span>}
                {m.pageLabel && <span className="rec-note">p. {m.pageLabel}</span>}
              </button>
            </div>
          );
        })}
      </div>
      {marks.length > 5 && (
        <div className="rec-foot right">
          <button type="button" className="linkish" onClick={() => setOpen((o) => !o)}>
            {open ? "fewer" : `all ${marks.length} ›`}
          </button>
        </div>
      )}
    </div>
  );
}
