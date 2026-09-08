import { useState } from "react";
import { EVENT_KIND_LABEL, type EventKind, type PlanItem } from "../../lib/types";

interface Props {
  date: string;
  planItems: PlanItem[];
  onCreate: (body: Record<string, unknown>) => Promise<void>;
}

const KINDS: EventKind[] = ["study_block", "work_block", "appointment", "personal"];

/**
 * Adding one entry to the selected day.
 *
 * <p>The plan-item select only appears for a study block, matching the rule the backend
 * enforces. Showing it for an appointment and then silently dropping what was chosen is
 * how a form teaches you not to trust it.
 */
export default function EventForm({ date, planItems, onCreate }: Props) {
  const [open, setOpen] = useState(false);
  const [title, setTitle] = useState("");
  const [kind, setKind] = useState<EventKind>("study_block");
  const [startTime, setStartTime] = useState("06:00");
  const [endTime, setEndTime] = useState("08:00");
  const [allDay, setAllDay] = useState(false);
  const [planItemId, setPlanItemId] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit() {
    if (!title.trim() || busy) return;
    setBusy(true);
    try {
      await onCreate({
        title: title.trim(),
        kind,
        date,
        startTime: allDay ? null : startTime,
        endTime: allDay || !endTime ? null : endTime,
        planItemId: kind === "study_block" && planItemId ? Number(planItemId) : null,
      });
      setTitle("");
      setPlanItemId("");
      setOpen(false);
    } finally {
      setBusy(false);
    }
  }

  if (!open) {
    return (
      <button type="button" className="addrow" onClick={() => setOpen(true)}>
        + add to this day
      </button>
    );
  }

  return (
    <div className="eventform">
      <label htmlFor="ev-title">What</label>
      <input
        id="ev-title"
        value={title}
        onChange={(e) => setTitle(e.target.value)}
        placeholder="CKA course + labs"
      />

      <label htmlFor="ev-kind">Kind</label>
      <select id="ev-kind" value={kind} onChange={(e) => setKind(e.target.value as EventKind)}>
        {KINDS.map((k) => (
          <option key={k} value={k}>
            {EVENT_KIND_LABEL[k]}
          </option>
        ))}
      </select>

      {kind === "study_block" && (
        <>
          <label htmlFor="ev-plan">Against a plan item</label>
          <select
            id="ev-plan"
            value={planItemId}
            onChange={(e) => setPlanItemId(e.target.value)}
          >
            <option value="">— none —</option>
            {planItems.map((p) => (
              <option key={p.id} value={p.id}>
                {p.title}
              </option>
            ))}
          </select>
        </>
      )}

      <label className="inline-check">
        <input type="checkbox" checked={allDay} onChange={(e) => setAllDay(e.target.checked)} />
        all day
      </label>

      {!allDay && (
        <div className="row">
          <div>
            <label htmlFor="ev-start">From</label>
            <input
              id="ev-start"
              type="time"
              value={startTime}
              onChange={(e) => setStartTime(e.target.value)}
            />
          </div>
          <div>
            <label htmlFor="ev-end">To</label>
            <input
              id="ev-end"
              type="time"
              value={endTime}
              onChange={(e) => setEndTime(e.target.value)}
            />
          </div>
        </div>
      )}

      <div className="actions">
        <button type="button" className="primary" onClick={submit} disabled={busy}>
          add
        </button>
        <button type="button" onClick={() => setOpen(false)}>
          cancel
        </button>
      </div>
    </div>
  );
}
