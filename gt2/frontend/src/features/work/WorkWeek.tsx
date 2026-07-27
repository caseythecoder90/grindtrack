import { useCallback, useEffect, useState } from "react";
import { api } from "../../lib/api";
import { addDays, mondayOf, todayISO } from "../../lib/dates";
import { WORK_WEEKLY_TARGET, type WorkDay as WorkDayT } from "../../lib/types";

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
      setDays(await api<WorkDayT[]>(`/api/work/days?from=${weekStart}&to=${end}`));
    } catch (e) {
      setError(e instanceof Error ? e.message : "could not load the week");
    }
  }, [weekStart]);

  useEffect(() => {
    load();
  }, [load]);

  const byDate = new Map(days.map((d) => [d.logDate, d]));
  const total = days.reduce((sum, d) => sum + d.hours, 0);
  const pct = Math.min(100, Math.round((total / WORK_WEEKLY_TARGET) * 100));

  return (
    <div className="panel">
      <h2>work week</h2>
      <div className="weeknav">
        <button onClick={() => setWeekStart(addDays(weekStart, -7))}>◀</button>
        <span>{weekStart} → {addDays(weekStart, 6)}</span>
        <button onClick={() => setWeekStart(addDays(weekStart, 7))}>▶</button>
        <button onClick={() => setWeekStart(mondayOf(todayISO()))}>this week</button>
      </div>
      <div className="muted" style={{ fontFamily: "var(--mono)", fontSize: 12 }}>
        {total.toFixed(1)} / {WORK_WEEKLY_TARGET} h
      </div>
      <div className={"progress" + (total >= WORK_WEEKLY_TARGET ? " over" : "")}>
        <i style={{ width: `${pct}%` }} />
      </div>
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
