import { useCallback, useEffect, useState } from "react";
import { errorMessage } from "../../lib/api";
import { acceptDayLog, getDayLogDraft, type DayLogDraft } from "./assistantApi";

/**
 * A day's log drafted mid-conversation, waiting on a click.
 *
 * <p>Shows the whole day as it will read after saving, with the parts the conversation changed
 * marked — because "logged two hours on etcd" over a day that already has wins and blockers is a
 * merge, and a card that showed only the two hours would hide what else the button is about to
 * write. Fetched rather than handed the fields, so what renders is what saving will do, even if the
 * day was edited elsewhere since the draft was made.
 */
export default function ProposedLog({
  date,
  onSaved,
}: {
  date: string;
  onSaved: () => void;
}) {
  const [draft, setDraft] = useState<DayLogDraft | null>(null);
  const [gone, setGone] = useState(false);
  const [saved, setSaved] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    try {
      const found = await getDayLogDraft(date);
      if (found) setDraft(found);
      else setGone(true);
    } catch {
      setGone(true);
    }
  }, [date]);

  useEffect(() => {
    load();
  }, [load]);

  async function save() {
    setBusy(true);
    setError("");
    try {
      await acceptDayLog(date);
      setSaved(true);
      onSaved();
    } catch (e) {
      setError(errorMessage(e, "could not save that"));
    } finally {
      setBusy(false);
    }
  }

  if (gone || !draft) return null;
  const { changes, result } = draft;
  const mark = (changed: boolean) => (changed ? "proposal-field changed" : "proposal-field");
  const rows: [string, string, boolean][] = [
    ["hours", `${result.hours}h`, changes.hours !== null],
    ["categories", result.categories.join(", ") || "—", changes.categories !== null],
    ["focus", result.focus || "—", changes.focus !== null],
    ["did", result.did || "—", changes.did !== null],
    ["wins", result.wins || "—", changes.wins !== null],
    ["blockers", result.blockers || "—", changes.blockers !== null],
    ["energy", result.energy === null ? "—" : `${result.energy} / 5`, changes.energy !== null],
  ];

  return (
    <div className="proposal">
      <div className="proposal-head">
        <span className="lbl">drafted · {draft.logDate}</span>
        <span className="proposal-sum">{result.existed ? "updates the day" : "new day"}</span>
      </div>

      <dl className="proposal-day">
        {rows.map(([k, v, changed]) => (
          <div className={mark(changed)} key={k}>
            <dt>{k}</dt>
            <dd>{v}</dd>
          </div>
        ))}
      </dl>

      {saved ? (
        <p className="proposal-done">saved ✓ — it is on the today tab now, editable like any day.</p>
      ) : (
        <div className="proposal-actions">
          <span className="muted small">
            {result.existed ? "marked fields change; the rest stays" : "nothing saved yet"}
          </span>
          <button type="button" className="primary" disabled={busy} onClick={save}>
            {busy ? "saving…" : result.existed ? "Update the day" : "Save the day"}
          </button>
        </div>
      )}

      {error && <div className="error">{error}</div>}
    </div>
  );
}
