import { useCallback, useEffect, useState } from "react";
import Segmented from "../../components/Segmented";
import { errorMessage } from "../../lib/api";
import { useAppResume } from "../../lib/resume";
import JournalComposer from "./JournalComposer";
import LibraryPanel from "./LibraryPanel";
import MeditationTimer from "./MeditationTimer";
import {
  addJournal,
  deleteJournal,
  finishReading,
  getJournal,
  getToday,
  logSession,
  updateSettings,
  type DailyEntry,
  type JournalEntry,
  type Passage,
  type Reading,
  type RecoveryToday,
} from "./recoveryApi";

type View = "today" | "read" | "journal" | "books";

const VIEWS: { value: View; label: string; tone?: string }[] = [
  { value: "today", label: "today", tone: "rec" },
  { value: "read", label: "read", tone: "rec" },
  { value: "journal", label: "journal", tone: "rec" },
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

/**
 * The recovery tab: the number, the day's readings, the timer, the book, the journal.
 *
 * Three views on a phone — today, read, journal — and three columns on a desktop, the same
 * markup either way; styles.css decides which. "Your books" is the fourth screen, where the
 * texts are imported.
 */
export default function RecoveryPage() {
  const [view, setView] = useState<View>("today");
  const [data, setData] = useState<RecoveryToday | null>(null);
  const [entries, setEntries] = useState<JournalEntry[]>([]);
  const [exhausted, setExhausted] = useState(false);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [focusKey, setFocusKey] = useState(0);
  const [minutesOpen, setMinutesOpen] = useState(false);

  const load = useCallback(async () => {
    setError("");
    try {
      setData(await getToday());
    } catch (e) {
      setError(errorMessage(e, "could not load the page"));
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
    loadJournal();
  }, [load, loadJournal]);

  // Marked read on the phone, then opened on the laptop: the laptop should know.
  useAppResume(() => {
    load();
    loadJournal();
  });

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

  async function changeMinutes(n: number) {
    setMinutesOpen(false);
    try {
      await updateSettings({ readMinutes: n });
      load();
    } catch (e) {
      setError(errorMessage(e, "could not change the minutes"));
    }
  }

  const sat = useCallback(
    async (minutes: number, completed: boolean) => {
      try {
        const m = await logSession(minutes, completed);
        setData((d) => (d ? { ...d, meditation: m, settings: { ...d.settings, meditationMinutes: minutes } } : d));
      } catch (e) {
        setError(errorMessage(e, "could not log the sitting"));
      }
    },
    [],
  );

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

  if (view === "books") {
    return <LibraryPanel onBack={() => setView("today")} onChanged={load} />;
  }

  const today = data?.today ?? localDate(new Date().toISOString());
  const dayNumber = data?.number?.days ?? null;

  return (
    <div className={"recpage view-" + view}>
      <div className="rec-head">
        <Segmented label="Recovery view" value={view} onChange={setView} options={VIEWS} />
        <button type="button" className="linkish" onClick={() => setView("books")}>
          your books ›
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

          <Daily label="daily reflection" entry={data?.reflection ?? null} date={today}
            missing="your copy of Daily Reflections is not imported yet" onBooks={() => setView("books")} />

          <PassageCard passage={data?.passage ?? null} />

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

          <BookCard reading={data?.reading ?? null} loaded={!!data} onRead={() => setView("read")} onBooks={() => setView("books")} />

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
          {data?.reading ? (
            <ReadCard
              reading={data.reading}
              busy={busy}
              minutesOpen={minutesOpen}
              onMinutes={() => setMinutesOpen(true)}
              onMinutesPicked={changeMinutes}
              onDone={done}
            />
          ) : (
            data && (
              <div className="rec-card">
                <span className="rec-lbl">big book</span>
                <p className="rec-hint">
                  Not imported yet. Bring your own copy, as one text file, on{" "}
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

function PassageCard({ passage }: { passage: Passage | null }) {
  if (!passage) return null;
  // Verses grouped by paragraph; a group with a line of poetry in it sets each verse on its own line.
  const groups: Passage["verses"][] = [];
  for (const v of passage.verses) {
    if (v.para || groups.length === 0) groups.push([v]);
    else groups[groups.length - 1].push(v);
  }
  return (
    <div className="rec-card">
      <div className="rec-cardhead">
        <span className="rec-lbl">today's passage</span>
        <span className="rec-note">{passage.reference} · {passage.translation}</span>
      </div>
      <div className="rec-serif">
        {groups.map((g) => (
          <p key={g[0].verse} className={"rec-verse-para" + (g.some((v) => v.text.includes("\n")) ? " poetry" : "")}>
            {g.map((v) => (
              <span key={v.verse}>
                <sup className="rec-vn">{v.verse}</sup>
                {v.text}{" "}
              </span>
            ))}
          </p>
        ))}
      </div>
      <div className="rec-note" style={{ marginTop: 8 }}>passage {passage.dayInPlan} of {passage.planSize.toLocaleString()}</div>
    </div>
  );
}

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
  return (
    <div className="rec-card">
      <div className="rec-cardhead">
        <span className="rec-lbl">{reading.bookTitle} · {reading.minutes} min</span>
        <span className="rec-note">day {reading.dayNumber}{reading.readThroughs > 0 ? ` · read ${reading.readThroughs}×` : ""}</span>
      </div>
      <div className="rec-title ellipsis">
        {reading.chapterNo > 0 && `Ch. ${reading.chapterNo} · `}{reading.chapterTitle}
        <span className="muted"> · ~{reading.words} words</span>
      </div>
      <div className="rec-meter"><i style={{ width: `${reading.percent}%` }} /></div>
      <div className="rec-foot">
        <span className="rec-note">{reading.percent}% read · {reading.doneToday ? "read today ✓" : "not yet today"}</span>
        <button type="button" className={reading.doneToday ? "" : "rec-primary"} onClick={onRead}>
          {reading.doneToday ? "read it again" : "read now"}
        </button>
      </div>
    </div>
  );
}

function ReadCard({ reading, busy, minutesOpen, onMinutes, onMinutesPicked, onDone }: {
  reading: Reading; busy: boolean; minutesOpen: boolean; onMinutes: () => void;
  onMinutesPicked: (n: number) => void; onDone: () => void;
}) {
  return (
    <>
      <div className="rec-card rec-pages">
        <div className="rec-cardhead">
          <span className="rec-lbl">
            {reading.chapterNo > 0 && `chapter ${reading.chapterNo} · `}{reading.chapterTitle}
          </span>
          <span className="rec-note">day {reading.dayNumber} · ~{reading.words} words</span>
        </div>
        <div className="rec-serif">
          {reading.paragraphs.map((p) => (
            <p key={p.seq} className="rec-pline">{p.body}</p>
          ))}
        </div>
        <div className="rec-foot">
          <span className="rec-note">
            {minutesOpen ? (
              <select
                aria-label="Minutes a day"
                autoFocus
                defaultValue={reading.minutes}
                onChange={(e) => onMinutesPicked(Number(e.target.value))}
                onBlur={(e) => onMinutesPicked(Number(e.target.value))}
              >
                {[3, 5, 7, 10, 15, 20, 30].map((n) => (
                  <option key={n} value={n}>{n} min</option>
                ))}
              </select>
            ) : (
              <>
                {reading.minutes} min a day ·{" "}
                <button type="button" className="linkish inline" onClick={onMinutes}>change</button>
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
      <div className="rec-card">
        <div className="rec-cardhead">
          <span className="rec-lbl">the plan</span>
          <span className="rec-note">{reading.chapters.length} chapters · {reading.minutes} min a day · {reading.percent}% read</span>
        </div>
        <div className="rec-chapters">
          {reading.chapters.map((c) => (
            <div key={c.no} className={"rec-chap " + c.state}>
              <span className="tick" aria-hidden="true" />
              <span className="ellipsis">{c.title}</span>
              <span className="rec-note right">{c.paragraphs} ¶</span>
            </div>
          ))}
        </div>
      </div>
    </>
  );
}
