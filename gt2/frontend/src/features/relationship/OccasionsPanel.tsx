import { useCallback, useEffect, useState } from "react";
import { errorMessage } from "../../lib/api";
import { createOccasion, deleteOccasion, getOccasions } from "./relationshipApi";
import type { Upcoming } from "../../lib/types";
import { inDays } from "./kinds";

/**
 * Anniversaries and birthdays — the only part of this tab with an actual deadline.
 *
 * <p>Lead time is the point. An occasion that surfaces the day before is not much use, and one
 * that sits on screen all year stops being read. Each one shows how many ideas are already waiting
 * against it, because a countdown with no ideas attached is just anxiety.
 */
export default function OccasionsPanel() {
  const [occasions, setOccasions] = useState<Upcoming[]>([]);
  const [adding, setAdding] = useState(false);
  const [label, setLabel] = useState("");
  const [date, setDate] = useState("");
  const [leadDays, setLeadDays] = useState("21");
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    try {
      setOccasions(await getOccasions());
    } catch (e) {
      setError(errorMessage(e, "could not load your dates"));
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  async function add() {
    if (!label.trim() || !date) return;
    setError("");
    try {
      setOccasions(
        await createOccasion({
          label,
          date,
          recurring: true,
          leadDays: Number(leadDays) || 21,
          note: "",
        }),
      );
      setLabel("");
      setDate("");
      setAdding(false);
    } catch (e) {
      setError(errorMessage(e, "could not save that"));
    }
  }

  async function remove(id: number) {
    setError("");
    try {
      await deleteOccasion(id);
      load();
    } catch (e) {
      setError(errorMessage(e, "could not remove that"));
    }
  }

  return (
    <section className="occasions">
      <div className="section-head">
        <h3>dates that matter</h3>
        <button type="button" onClick={() => setAdding((v) => !v)}>
          {adding ? "cancel" : "+ date"}
        </button>
      </div>

      {error && <p className="error">{error}</p>}

      {adding && (
        <div className="account-form">
          <input
            placeholder="what (e.g. anniversary)"
            value={label}
            onChange={(e) => setLabel(e.target.value)}
          />
          <input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
          <input
            type="number"
            step="7"
            title="how many days ahead this should start showing up"
            value={leadDays}
            onChange={(e) => setLeadDays(e.target.value)}
          />
          <button type="button" onClick={add}>
            add
          </button>
        </div>
      )}

      {occasions.length === 0 ? (
        <p className="muted small">
          Nothing yet. Worth adding the anniversary and her birthday — with enough lead time that
          an idea still has somewhere to go.
        </p>
      ) : (
        <table className="txn-table">
          <tbody>
            {occasions.map((o) => (
              <tr key={o.id}>
                <td>
                  {o.label}
                  {o.years != null && o.years > 0 && (
                    <span className="tag"> {ordinal(o.years)}</span>
                  )}
                  <div className="muted small">
                    {o.on} · {inDays(o.daysAway)}
                    {o.ideaCount > 0
                      ? ` · ${o.ideaCount} idea${o.ideaCount === 1 ? "" : "s"} saved`
                      : " · no ideas saved yet"}
                  </div>
                </td>
                <td className="num">
                  <button type="button" className="ghost" onClick={() => remove(o.id)}>
                    ×
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}

function ordinal(n: number): string {
  const suffix = n % 100 >= 11 && n % 100 <= 13 ? "th" : ["th", "st", "nd", "rd"][n % 10] ?? "th";
  return `${n}${suffix}`;
}
