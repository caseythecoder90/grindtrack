import { useCallback, useEffect, useState } from "react";
import WeekTotals from "../../components/WeekTotals";
import { errorMessage } from "../../lib/api";
import { getWorkDays } from "./workApi";
import { addDays, mondayOf, todayISO } from "../../lib/dates";
import type { WorkDay as WorkDayT } from "../../lib/types";

const DOW = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

/** A work week at a glance: hours logged vs the 40h target, day by day. */
export default function WorkWeek() {
  const [weekStart, setWeekStart] = useState(mondayOf(todayISO()));
  const [days, setDays] = useState<WorkDayT[]>([]);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setError("");
    try {
      const end = addDays(weekStart, 6);
      setDays(await getWorkDays(weekStart, end));
    } catch (e) {
      setError(errorMessage(e, "could not load the week"));
    }
  }, [weekStart]);

  useEffect(() => {
    load();
  }, [load]);

  const byDate = new Map(days.map((d) => [d.logDate, d]));

  return (
    <div className="panel">
      <h2>work week</h2>
      <div className="weeknav">
        <button onClick={() => setWeekStart(addDays(weekStart, -7))}>◀</button>
        <span>{weekStart} → {addDays(weekStart, 6)}</span>
        <button onClick={() => setWeekStart(addDays(weekStart, 7))}>▶</button>
        <button onClick={() => setWeekStart(mondayOf(todayISO()))}>this week</button>
      </div>
      <WeekTotals weekStart={weekStart} />
      <table>
        <thead>
          <tr>
            <th style={{ width: 110 }}>day</th>
            <th style={{ width: 60 }}>hrs</th>
            <th style={{ width: 160 }}>project</th>
            <th>what I did</th>
          </tr>
        </thead>
        <tbody>
          {DOW.map((name, i) => {
            const date = addDays(weekStart, i);
            const d = byDate.get(date);
            return (
              <tr key={date} className={date === todayISO() ? "today-row" : ""}>
                <td className="num">{name} <span className="muted">{date.slice(5)}</span></td>
                <td className="num">{d ? d.hours.toFixed(1) : "—"}</td>
                <td className="muted">{d?.project ?? ""}</td>
                <td>{(d?.did || d?.goals || "").slice(0, 140)}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
      {error && <div className="error" style={{ marginTop: 8 }}>{error}</div>}
    </div>
  );
}
