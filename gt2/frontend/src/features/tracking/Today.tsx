
import { useCallback, useEffect, useState } from "react";
import { errorMessage } from "../../lib/api";
import { getDay, saveDay } from "./trackingApi";
import { todayISO } from "../../lib/dates";
import { CATEGORIES } from "../../lib/types";
import { useAppResume } from "../../lib/resume";
import MorningBrief from "../assistant/MorningBrief";

interface Props {
  onSaved: () => void;
}

export default function Today({ onSaved }: Props) {
  const [date, setDate] = useState(todayISO());
  const [hours, setHours] = useState("0");
  const [cats, setCats] = useState<Set<string>>(new Set());
  const [energy, setEnergy] = useState<number | null>(null);
  const [focus, setFocus] = useState("");
  const [did, setDid] = useState("");
  const [wins, setWins] = useState("");
  const [blockers, setBlockers] = useState("");
  const [toast, setToast] = useState(false);
  /**
   * Whether the hours box has been touched since it was last loaded.
   *
   * Untouched hours are left out of the save entirely, because this form is not the only writer:
   * the focus timer adds to the same day. Re-sending a number loaded ten minutes ago would
   * silently undo any session logged since — including one logged on the other device.
   */
  const [hoursEdited, setHoursEdited] = useState(false);
  const [loadError, setLoadError] = useState("");

  /**
   * A failed load left every field showing the previous day's values with nothing to
   * say why -- and threw, uncaught, on the way out. An unreachable server is worth a
   * line of text; it is not worth silently pretending the day is empty.
   */
  const load = useCallback(async () => {
    setLoadError("");
    try {
      const d = await getDay(date);
      setHours(String(d?.hours ?? 0));
      setCats(new Set(d?.categories ?? []));
      setEnergy(d?.energy ?? null);
      setFocus(d?.focus ?? "");
      setDid(d?.did ?? "");
      setWins(d?.wins ?? "");
      setBlockers(d?.blockers ?? "");
      setHoursEdited(false);
    } catch (e) {
      setLoadError(errorMessage(e, "could not load this day"));
    }
  }, [date]);

  // Anything logged on another device since this screen loaded.
  useAppResume(() => load());

  useEffect(() => {
    load();
  }, [load]);

  function toggleCat(c: string) {
    const next = new Set(cats);
    next.has(c) ? next.delete(c) : next.add(c);
    setCats(next);
  }

  async function save() {
    await saveDay(date, {
      ...(hoursEdited ? { hours: Number(hours) } : {}),
      categories: [...cats],
      focus,
      did,
      wins,
      blockers,
      energy,
    });
    // Re-read rather than trusting the form: the stored total may include focus minutes this
    // form never saw, and showing a stale number is how the overwrite happened in the first place.
    await load();
    setToast(true);
    setTimeout(() => setToast(false), 1600);
    onSaved();
  }

  return (
    <>
      <MorningBrief />
      <div className="panel">
        <h2>daily log</h2>
      <div className="row3">
        <div>
          <label htmlFor="d-date">Date</label>
          <input id="d-date" type="date" value={date} onChange={(e) => setDate(e.target.value)} />
        </div>
        <div>
          <label htmlFor="d-hours">Hours</label>
          <input id="d-hours" type="number" min={0} max={24} step={0.5} value={hours}
            onChange={(e) => { setHours(e.target.value); setHoursEdited(true); }} />
        </div>
        <div>
          <label>Energy</label>
          <div className="energy">
            {[1, 2, 3, 4, 5].map((n) => (
              <button key={n} className={energy === n ? "on" : ""}
                onClick={() => setEnergy(energy === n ? null : n)}>{n}</button>
            ))}
          </div>
        </div>
      </div>
      <label>Categories</label>
      <div className="chips">
        {CATEGORIES.map((c) => (
          <button key={c} type="button" className="chip" aria-pressed={cats.has(c)}
            onClick={() => toggleCat(c)}>
            {c}
          </button>
        ))}
      </div>
      <div className="row">
        <div>
          <label htmlFor="d-focus">Planned focus</label>
          <textarea id="d-focus" value={focus} onChange={(e) => setFocus(e.target.value)}
            placeholder="What was this block for? e.g. CKA labs: etcd backup/restore" />
        </div>
        <div>
          <label htmlFor="d-did">What actually happened</label>
          <textarea id="d-did" value={did} onChange={(e) => setDid(e.target.value)} />
        </div>
      </div>
      {/* Folded away, and open when either has content so nothing is ever hidden.
          These two get written on a Friday, not on a Tuesday morning — and four
          stacked textareas is most of a phone screen for the two you use daily. */}
      <details className="fold" open={Boolean(wins || blockers)}>
        <summary>wins &amp; blockers</summary>
        <div className="row">
          <div>
            <label htmlFor="d-wins">Wins</label>
            <textarea id="d-wins" value={wins} onChange={(e) => setWins(e.target.value)}
              placeholder="Work wins here are Impact Log material" />
          </div>
          <div>
            <label htmlFor="d-blockers">Blockers / notes</label>
            <textarea id="d-blockers" value={blockers} onChange={(e) => setBlockers(e.target.value)} />
          </div>
        </div>
      </details>
      {loadError && <div className="error">{loadError}</div>}
      <div className="actions">
        <button className="primary" onClick={save}>Save day</button>
        <span className={"toast" + (toast ? " show" : "")}>saved ✓</span>
      </div>
    </div>
    </>
  );
}
