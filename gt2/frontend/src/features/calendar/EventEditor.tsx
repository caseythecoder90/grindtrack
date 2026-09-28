import { useEffect, useRef, useState, type KeyboardEvent } from "react";
import Popover from "./Popover";
import {
  KINDS,
  KIND_CLASS,
  REMIND_OPTIONS,
  problemWith,
  type Draft,
} from "./model";
import { minutesOf, timeOf } from "../../lib/dates";
import {
  EVENT_KIND_LABEL,
  type EventKind,
  type PlanItem,
} from "../../lib/types";

interface Props {
  draft: Draft;
  planItems: PlanItem[];
  /** Saves; resolves when the calendar has re-read itself. Rejects with the server's message. */
  onSave: (d: Draft) => Promise<void>;
  onDelete: ((id: number) => Promise<void>) | null;
  onClose: () => void;
}

/** "1h" sets the end an hour after the start. The five most booked lengths. */
const DURATIONS: { label: string; minutes: number }[] = [
  { label: "30m", minutes: 30 },
  { label: "1h", minutes: 60 },
  { label: "1h30", minutes: 90 },
  { label: "2h", minutes: 120 },
  { label: "3h", minutes: 180 },
];

/**
 * Adding or changing one event, title first.
 *
 * <p>The title takes focus and Enter saves, so the common case — a block at the time you clicked —
 * is one line of typing. Everything else is on the card but below: the kind as chips because there
 * are four, the times with the length beside them because "two hours" is how a block is thought
 * of, the plan item only for a study block (the rule the backend enforces), the reminder, notes.
 * Enter in any field but the notes saves, as does Ctrl+Enter anywhere; Escape closes without
 * saving.
 */
export default function EventEditor({
  draft,
  planItems,
  onSave,
  onDelete,
  onClose,
}: Props) {
  const [d, setD] = useState<Draft>(draft);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const titleRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    titleRef.current?.focus();
  }, []);

  const set = <K extends keyof Draft>(key: K, value: Draft[K]) =>
    setD((prev) => ({ ...prev, [key]: value }));

  const currentMinutes =
    d.endTime && d.startTime
      ? minutesOf(d.endTime) - minutesOf(d.startTime)
      : 0;

  async function save() {
    const problem = problemWith(d);
    if (problem) {
      setError(problem);
      return;
    }
    if (busy) return;
    setBusy(true);
    setError("");
    try {
      await onSave(d);
    } catch (e) {
      setError(e instanceof Error ? e.message : "could not save that");
      setBusy(false);
    }
  }

  async function remove() {
    if (!onDelete || d.id == null || busy) return;
    setBusy(true);
    try {
      await onDelete(d.id);
    } catch (e) {
      setError(e instanceof Error ? e.message : "could not delete that");
      setBusy(false);
    }
  }

  function onKey(e: KeyboardEvent<HTMLDivElement>) {
    if (e.key !== "Enter") return;
    const tag = (e.target as HTMLElement).tagName;
    if (e.ctrlKey || e.metaKey || tag === "INPUT" || tag === "SELECT") {
      e.preventDefault();
      void save();
    }
  }

  return (
    <Popover
      anchor={null}
      label={d.id == null ? "new event" : "edit event"}
      onClose={onClose}
      className="editor"
    >
      <div onKeyDown={onKey}>
        <input
          ref={titleRef}
          id="ev-title"
          className="editor-title"
          value={d.title}
          placeholder="Add title"
          aria-label="title"
          onChange={(e) => set("title", e.target.value)}
        />

        <div className="chips editor-kinds" role="group" aria-label="kind">
          {KINDS.map((k: EventKind) => (
            <button
              key={k}
              type="button"
              className={"chip " + KIND_CLASS[k]}
              aria-pressed={d.kind === k}
              onClick={() => set("kind", k)}
            >
              {EVENT_KIND_LABEL[k]}
            </button>
          ))}
        </div>

        <div className="editor-when">
          <div>
            <label htmlFor="ev-date">Day</label>
            <input
              id="ev-date"
              type="date"
              value={d.date}
              onChange={(e) => set("date", e.target.value)}
            />
          </div>
          <label className="inline-check editor-allday">
            <input
              type="checkbox"
              checked={d.allDay}
              onChange={(e) => set("allDay", e.target.checked)}
            />
            all day
          </label>
        </div>

        {!d.allDay && (
          <>
            <div className="editor-times">
              <div>
                <label htmlFor="ev-start">From</label>
                <input
                  id="ev-start"
                  type="time"
                  value={d.startTime}
                  onChange={(e) => {
                    // Moving the start keeps the length: a two-hour block moved to seven is still
                    // two hours, which is what dragging one in any calendar does.
                    const next = e.target.value;
                    const keep =
                      currentMinutes > 0 && next
                        ? timeOf(minutesOf(next) + currentMinutes)
                        : d.endTime;
                    setD((prev) => ({
                      ...prev,
                      startTime: next,
                      endTime: keep,
                    }));
                  }}
                />
              </div>
              <div>
                <label htmlFor="ev-end">To</label>
                <input
                  id="ev-end"
                  type="time"
                  value={d.endTime}
                  onChange={(e) => set("endTime", e.target.value)}
                />
              </div>
            </div>
            <div
              className="chips editor-durations"
              role="group"
              aria-label="length"
            >
              {DURATIONS.map((opt) => (
                <button
                  key={opt.minutes}
                  type="button"
                  className="chip"
                  aria-pressed={currentMinutes === opt.minutes}
                  disabled={!d.startTime}
                  onClick={() =>
                    set("endTime", timeOf(minutesOf(d.startTime) + opt.minutes))
                  }
                >
                  {opt.label}
                </button>
              ))}
              <button
                type="button"
                className="chip"
                aria-pressed={d.endTime === ""}
                onClick={() => set("endTime", "")}
              >
                open-ended
              </button>
            </div>
          </>
        )}

        {d.kind === "study_block" && (
          <>
            <label htmlFor="ev-plan">Against a plan item</label>
            <select
              id="ev-plan"
              value={d.planItemId}
              onChange={(e) => set("planItemId", e.target.value)}
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

        {!d.allDay && (
          <>
            <label htmlFor="ev-remind">Reminder</label>
            <select
              id="ev-remind"
              value={d.remind}
              onChange={(e) => set("remind", e.target.value)}
            >
              {REMIND_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.value === ""
                    ? d.kind === "work_block"
                      ? "app default · none for work"
                      : "app default · 10 minutes before"
                    : o.label}
                </option>
              ))}
            </select>
          </>
        )}

        <label htmlFor="ev-notes">Notes</label>
        <textarea
          id="ev-notes"
          value={d.notes}
          placeholder="room, link, what to bring"
          onChange={(e) => set("notes", e.target.value)}
        />

        {error && <div className="error">{error}</div>}

        <div className="editor-actions">
          {onDelete && d.id != null && (
            <button
              type="button"
              className="ghost danger"
              onClick={remove}
              disabled={busy}
            >
              delete
            </button>
          )}
          <span className="spacer" />
          <button type="button" onClick={onClose} disabled={busy}>
            cancel
          </button>
          <button
            type="button"
            className="primary"
            onClick={save}
            disabled={busy}
          >
            {d.id == null ? "add" : "save"}
          </button>
        </div>
      </div>
    </Popover>
  );
}
