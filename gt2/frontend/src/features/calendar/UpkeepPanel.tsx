import { useState } from "react";
import { TASK_CATEGORIES, type UpkeepState, type UpkeepTask } from "../../lib/types";

interface Props {
  tasks: UpkeepTask[];
  onDone: (id: number) => void;
  onDelete: (id: number) => void;
  onCreate: (body: Record<string, unknown>) => Promise<void>;
}

const GROUPS: { state: UpkeepState; label: string }[] = [
  { state: "overdue", label: "overdue" },
  { state: "due_soon", label: "this week" },
  { state: "later", label: "later" },
];

/** How late, in words. The server sends one number; this is the only place it is read. */
function whenLabel(task: UpkeepTask): string {
  const d = task.daysOverdue;
  if (d === 0) return "today";
  if (d > 0) return `+${d}d`;
  return `in ${-d}d`;
}

/**
 * What is due, grouped by urgency.
 *
 * <p>Grouped rather than one sorted list, because "what do I need to do" is the question
 * and a heading answers it faster than reading down a column of dates. Red is kept for
 * genuinely late and yellow for due now — the same vocabulary the todo list uses, so red
 * still means something when it appears.
 */
export default function UpkeepPanel({ tasks, onDone, onDelete, onCreate }: Props) {
  const [adding, setAdding] = useState(false);
  const [title, setTitle] = useState("");
  const [category, setCategory] = useState("home");
  const [intervalDays, setIntervalDays] = useState("30");
  const [busy, setBusy] = useState(false);

  async function submit() {
    if (!title.trim() || busy) return;
    setBusy(true);
    try {
      await onCreate({
        title: title.trim(),
        category,
        intervalDays: Number(intervalDays) || 30,
      });
      setTitle("");
      setAdding(false);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="panel upkeep">
      <div className="panelhead">
        <h2>upkeep</h2>
        <span className="muted mono small">{tasks.length} tracked</span>
      </div>

      {tasks.length === 0 && (
        <div className="empty">
          Nothing tracked yet. The dog's flea and tick, the HVAC filter, an oil change —
          anything where the question is "when did I last".
        </div>
      )}

      {GROUPS.map(({ state, label }) => {
        const group = tasks.filter((t) => t.state === state);
        if (group.length === 0) return null;
        return (
          <section key={state} className={"upkeep-group " + state}>
            <div className="upkeep-head lbl">{label}</div>
            {group.map((t) => (
              <div key={t.id} className={"upkeep-row " + t.state}>
                <div className="upkeep-body">
                  <div className="upkeep-title">{t.title}</div>
                  <div className="upkeep-meta mono">
                    {t.lastDoneOn ? `last done ${t.lastDoneOn}` : "never done"} · every{" "}
                    {t.intervalDays}d
                  </div>
                </div>
                <span className={"due " + t.state}>{whenLabel(t)}</span>
                <button
                  type="button"
                  className="upkeep-done"
                  aria-label={`mark ${t.title} done today`}
                  onClick={() => onDone(t.id)}
                >
                  ✓
                </button>
                <button
                  type="button"
                  className="ghost danger"
                  aria-label={`delete ${t.title}`}
                  onClick={() => onDelete(t.id)}
                >
                  ×
                </button>
              </div>
            ))}
          </section>
        );
      })}

      {adding ? (
        <div className="eventform">
          <label htmlFor="up-title">What</label>
          <input
            id="up-title"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="Dog — flea &amp; tick"
          />
          <div className="row">
            <div>
              <label htmlFor="up-cat">Category</label>
              <select id="up-cat" value={category} onChange={(e) => setCategory(e.target.value)}>
                {TASK_CATEGORIES.map((c) => (
                  <option key={c} value={c}>
                    {c}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label htmlFor="up-int">Every (days)</label>
              <input
                id="up-int"
                type="number"
                min={1}
                max={3650}
                value={intervalDays}
                onChange={(e) => setIntervalDays(e.target.value)}
              />
            </div>
          </div>
          <div className="actions">
            <button type="button" className="primary" onClick={submit} disabled={busy}>
              add
            </button>
            <button type="button" onClick={() => setAdding(false)}>
              cancel
            </button>
          </div>
        </div>
      ) : (
        <button type="button" className="addrow" onClick={() => setAdding(true)}>
          + add recurring task
        </button>
      )}
    </div>
  );
}
