import { useCallback, useEffect, useState } from "react";
import { errorMessage } from "../../lib/api";
import { getWorkDay, saveWorkDay } from "./workApi";
import { todayISO } from "../../lib/dates";
import { WORK_CATEGORIES } from "../../lib/types";

/**
 * One day of the actual job: hours, project, categories, and the goals →
 * did → blockers → learnings journal. "What I did" is impact-log material;
 * "Learnings" is where the deliberate system/tooling knowledge gets captured.
 */
export default function WorkDay({ onSaved }: { onSaved: () => void }) {
  const [date, setDate] = useState(todayISO());
  const [hours, setHours] = useState("0");
  const [cats, setCats] = useState<Set<string>>(new Set());
  const [project, setProject] = useState("");
  const [goals, setGoals] = useState("");
  const [did, setDid] = useState("");
  const [blockers, setBlockers] = useState("");
  const [learnings, setLearnings] = useState("");
  const [toast, setToast] = useState(false);
  const [error, setError] = useState("");
  /**
   * Whether the hours box has been touched since it was last loaded. Untouched hours are left
   * out of the save: the focus timer writes work hours too, and re-sending a stale total would
   * undo any work session logged since this form loaded. See DailyLog.setHours.
   */
  const [hoursEdited, setHoursEdited] = useState(false);

  const load = useCallback(async () => {
    try {
      const d = await getWorkDay(date);
      setHours(String(d?.hours ?? 0));
      setCats(new Set(d?.categories ?? []));
      setProject(d?.project ?? "");
      setGoals(d?.goals ?? "");
      setDid(d?.did ?? "");
      setBlockers(d?.blockers ?? "");
      setLearnings(d?.learnings ?? "");
      setHoursEdited(false);
    } catch {
      setError("could not load this day");
    }
  }, [date]);

  useEffect(() => {
    load();
  }, [load]);

  function toggleCat(c: string) {
    const next = new Set(cats);
    next.has(c) ? next.delete(c) : next.add(c);
    setCats(next);
  }

  async function save() {
    setError("");
    try {
      await saveWorkDay(date, {
        ...(hoursEdited ? { hours: Number(hours) } : {}),
        categories: [...cats],
        project,
        goals,
        did,
        blockers,
        learnings,
      });
      // Re-read: the stored total may include focus minutes this form never saw.
      await load();
      setToast(true);
      setTimeout(() => setToast(false), 1600);
      onSaved();
    } catch (e) {
      setError(errorMessage(e, "save failed"));
    }
  }

  return (
    <div className="panel">
      <h2>work log</h2>
      <div className="row3">
        <div>
          <label htmlFor="w-date">Date</label>
          <input id="w-date" type="date" value={date} onChange={(e) => setDate(e.target.value)} />
        </div>
        <div>
          <label htmlFor="w-hours">Hours</label>
          <input id="w-hours" type="number" min={0} max={24} step={0.5} value={hours}
            onChange={(e) => { setHours(e.target.value); setHoursEdited(true); }} />
        </div>
        <div>
          <label htmlFor="w-project">Project / initiative</label>
          <input id="w-project" type="text" value={project} maxLength={120}
            onChange={(e) => setProject(e.target.value)}
            placeholder="e.g. modernization, microbatching engine" />
        </div>
      </div>
      <label>Categories</label>
      <div className="chips">
        {WORK_CATEGORIES.map((c) => (
          <button key={c} type="button" className="chip" aria-pressed={cats.has(c)}
            onClick={() => toggleCat(c)}>
            {c}
          </button>
        ))}
      </div>
      <div className="row">
        <div>
          <label htmlFor="w-goals">Goals for the day</label>
          <textarea id="w-goals" value={goals} onChange={(e) => setGoals(e.target.value)}
            placeholder="What did you set out to accomplish?" />
        </div>
        <div>
          <label htmlFor="w-did">What I did</label>
          <textarea id="w-did" value={did} onChange={(e) => setDid(e.target.value)}
            placeholder="Accomplishments — this is your impact-log / promo material" />
        </div>
      </div>
      <div className="row">
        <div>
          <label htmlFor="w-blockers">Blockers</label>
          <textarea id="w-blockers" value={blockers} onChange={(e) => setBlockers(e.target.value)}
            placeholder="What slowed you down or needs a decision?" />
        </div>
        <div>
          <label htmlFor="w-learnings">Learnings</label>
          <textarea id="w-learnings" value={learnings} onChange={(e) => setLearnings(e.target.value)}
            placeholder="What did you learn about the system, tooling, or domain today?" />
        </div>
      </div>
      <div className="actions">
        <button className="primary" onClick={save}>Save day</button>
        <span className={"toast" + (toast ? " show" : "")}>saved ✓</span>
      </div>
      {error && <div className="error" style={{ marginTop: 8 }}>{error}</div>}
    </div>
  );
}
