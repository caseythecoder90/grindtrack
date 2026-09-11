import { useCallback, useEffect, useState } from "react";
import { errorMessage } from "../../lib/api";
import { getSummary, logMoment } from "./relationshipApi";
import { todayISO } from "../../lib/dates";
import type { MomentKind, RelationshipSummary } from "../../lib/types";
import ClosenessCard from "./ClosenessCard";
import IdeasPanel from "./IdeasPanel";
import LatelyPanel from "./LatelyPanel";
import OccasionsPanel from "./OccasionsPanel";
import ReadingPanel from "./ReadingPanel";
import { daysAgo, MOMENT_KINDS, MOMENT_LABEL } from "./kinds";
import { useAppResume } from "../../lib/resume";

/** Per-device, remembered locally. Laptops get opened on kitchen tables. */
const DISCREET_KEY = "gt-us-discreet";

/** Which drawer is open. Per-device too: it is a view preference, not data. */
const DRAWER_KEY = "gt-us-drawer";

type Drawer = "lately" | "ideas" | "occasions" | "reading";

const DRAWERS: Drawer[] = ["lately", "ideas", "occasions", "reading"];

function stored(key: string, fallback: string): string {
  try {
    return localStorage.getItem(key) ?? fallback;
  } catch {
    return fallback;
  }
}

function remember(key: string, value: string) {
  try {
    localStorage.setItem(key, value);
  } catch {
    /* a browser refusing storage should not break the page */
  }
}

/**
 * The us tab.
 *
 * <p>The answer first, because that is the question you arrive with: a sentence, then the dates
 * behind it, then the three recencies as plain facts. Then one drawer at a time.
 *
 * <p>Two things this deliberately is not. It is not a ledger — it used to borrow the finance tab's
 * stat tiles and transaction tables, which rendered a feature whose whole point is reassurance as
 * a set of accounts. And it is not seven sections stacked down one scroll, which is what you got
 * before: everything below the fold was passed over on every visit, including the ideas that only
 * earn their keep by resurfacing.
 *
 * <p>There are still no streaks, no targets and no scores anywhere on this page, and there must
 * not be. It exists to settle a question, not to grade an answer.
 */
export default function RelationshipPage() {
  const [summary, setSummary] = useState<RelationshipSummary | null>(null);
  const [error, setError] = useState("");
  const [discreet, setDiscreet] = useState(() => stored(DISCREET_KEY, "0") === "1");
  const [drawer, setDrawer] = useState<Drawer>(
    () => (DRAWERS as string[]).includes(stored(DRAWER_KEY, "lately"))
      ? (stored(DRAWER_KEY, "lately") as Drawer)
      : "lately",
  );

  const [kind, setKind] = useState<MomentKind>("DATE_NIGHT");
  const [date, setDate] = useState(todayISO());
  const [note, setNote] = useState("");
  const [saving, setSaving] = useState(false);
  const [logging, setLogging] = useState(false);

  const load = useCallback(async () => {
    setError("");
    try {
      setSummary(await getSummary());
    } catch (e) {
      setError(errorMessage(e, "could not load this tab"));
    }
  }, []);

  // Anything logged on another device since this screen loaded.
  useAppResume(() => load());

  useEffect(() => {
    load();
  }, [load]);

  function toggleDiscreet() {
    setDiscreet((current) => {
      remember(DISCREET_KEY, current ? "0" : "1");
      return !current;
    });
  }

  function openDrawer(next: Drawer) {
    setDrawer(next);
    remember(DRAWER_KEY, next);
  }

  async function log() {
    setSaving(true);
    setError("");
    try {
      await logMoment({ occurredOn: date, kind, note, feltClose: null });
      setNote("");
      setDate(todayISO());
      setLogging(false);
      openDrawer("lately");
      load();
    } catch (e) {
      setError(errorMessage(e, "could not log that"));
    } finally {
      setSaving(false);
    }
  }

  if (!summary) {
    return (
      <div className="us">
        {error ? <p className="error">{error}</p> : <p className="muted">loading…</p>}
      </div>
    );
  }

  const facts = summary.recency.filter((r) => r.kind !== "INTIMACY").slice(0, 3);

  return (
    <div className="us">
      {error && <p className="error">{error}</p>}

      <ClosenessCard
        closeness={summary.closeness}
        discreet={discreet}
        onToggleDiscreet={toggleDiscreet}
      />

      {/* Facts, not tiles. "6 days ago" is a thing that is true, and a KPI card around it is the
          one piece of furniture this page must never wear. */}
      <div className="facts">
        {facts.map((r) => (
          <div className="fact" key={r.kind}>
            <span>{MOMENT_LABEL[r.kind]}</span>
            <b className={r.daysSince !== null && r.daysSince > 21 ? "far" : undefined}>
              {daysAgo(r.daysSince)}
            </b>
          </div>
        ))}
      </div>

      {logging ? (
        <section className="logform">
          <div className="row3">
            <select
              value={kind}
              aria-label="what happened"
              onChange={(e) => setKind(e.target.value as MomentKind)}
            >
              {MOMENT_KINDS.map((k) => (
                <option key={k} value={k}>
                  {MOMENT_LABEL[k]}
                </option>
              ))}
            </select>
            <input
              type="date"
              value={date}
              aria-label="when"
              onChange={(e) => setDate(e.target.value)}
            />
          </div>
          <input
            placeholder="note (optional)"
            value={note}
            onChange={(e) => setNote(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") log();
            }}
          />
          <div className="logactions">
            <button type="button" onClick={() => setLogging(false)}>
              cancel
            </button>
            <button type="button" className="primary" disabled={saving} onClick={log}>
              {saving ? "…" : "log it"}
            </button>
          </div>
          <p className="muted small">
            Backdating is fine — most of these get remembered the next morning.
          </p>
        </section>
      ) : (
        <button type="button" className="logbutton" onClick={() => setLogging(true)}>
          log a moment
        </button>
      )}

      <nav className="drawers" aria-label="What to show">
        {DRAWERS.map((d) => (
          <button
            key={d}
            type="button"
            className={"chip" + (drawer === d ? " active" : "")}
            aria-pressed={drawer === d}
            onClick={() => openDrawer(d)}
          >
            {d}
            {d === "ideas" && summary.readyIdeas.length > 0 && (
              <b>{summary.readyIdeas.length}</b>
            )}
          </button>
        ))}
      </nav>

      {drawer === "lately" && (
        <LatelyPanel upcoming={summary.upcoming} discreet={discreet} onChange={load} />
      )}
      {drawer === "ideas" && <IdeasPanel onChange={load} />}
      {drawer === "occasions" && <OccasionsPanel />}
      {drawer === "reading" && <ReadingPanel onChange={load} />}
    </div>
  );
}
