
import { useCallback, useEffect, useState } from "react";
import WeekTotals from "../../components/WeekTotals";
import { getDays, getWeek, saveWeek } from "./trackingApi";
import { addDays, mondayOf, todayISO } from "../../lib/dates";
import type {DayLog} from "../../lib/types";

const DOW = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

export default function Week() {
  const [weekStart, setWeekStart] = useState(mondayOf(todayISO()));
  const [days, setDays] = useState<DayLog[]>([]);
  const [summary, setSummary] = useState("");
  const [wins, setWins] = useState("");
  const [blockers, setBlockers] = useState("");
  const [adjustments, setAdjustments] = useState("");
  const [nextFocus, setNextFocus] = useState("");
  const [onTrack, setOnTrack] = useState<boolean | null>(null);
  const [toast, setToast] = useState(false);

  const load = useCallback(async () => {
    const end = addDays(weekStart, 6);
    setDays(await getDays(weekStart, end));
    const review = await getWeek(weekStart);
    setSummary(review?.summary ?? "");
    setWins(review?.wins ?? "");
    setBlockers(review?.blockers ?? "");
    setAdjustments(review?.adjustments ?? "");
    setNextFocus(review?.nextFocus ?? "");
    setOnTrack(review?.onTrack ?? null);
  }, [weekStart]);

  useEffect(() => {
    load();
  }, [load]);

  const byDate = new Map(days.map((d) => [d.logDate, d]));

  async function save() {
    await saveWeek(weekStart, { summary, wins, blockers, adjustments, nextFocus, onTrack });
    setToast(true);
    setTimeout(() => setToast(false), 1600);
  }

  return (
    <div className="panel">
      <h2>week view</h2>
      <div className="weeknav">
        <button onClick={() => setWeekStart(addDays(weekStart, -7))}>◀</button>
        <span>{weekStart} → {addDays(weekStart, 6)}</span>
        <button onClick={() => setWeekStart(addDays(weekStart, 7))}>▶</button>
        <button onClick={() => setWeekStart(mondayOf(todayISO()))}>this week</button>
      </div>
      <WeekTotals weekStart={weekStart} />
      <table>
        <thead>
          <tr><th style={{ width: 110 }}>day</th><th style={{ width: 60 }}>hrs</th>
            <th style={{ width: 180 }}>categories</th><th>what happened</th></tr>
        </thead>
        <tbody>
          {DOW.map((name, i) => {
            const date = addDays(weekStart, i);
            const d = byDate.get(date);
            return (
              <tr key={date} className={date === todayISO() ? "today-row" : ""}>
                <td className="num">{name} <span className="muted">{date.slice(5)}</span></td>
                <td className="num">{d ? d.hours.toFixed(1) : "—"}</td>
                <td className="muted">{d?.categories.join(", ") ?? ""}</td>
                <td>{(d?.did || d?.focus || "").slice(0, 140)}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
      <h2 style={{ marginTop: 24 }}>weekly review</h2>
      <div className="row">
        <div><label>Summary of the week</label>
          <textarea value={summary} onChange={(e) => setSummary(e.target.value)} /></div>
        <div><label>Wins</label>
          <textarea value={wins} onChange={(e) => setWins(e.target.value)} /></div>
      </div>
      <div className="row">
        <div><label>Blockers</label>
          <textarea value={blockers} onChange={(e) => setBlockers(e.target.value)} /></div>
        <div><label>Adjustments to the plan</label>
          <textarea value={adjustments} onChange={(e) => setAdjustments(e.target.value)} /></div>
      </div>
      <label>Next week's focus</label>
      <textarea value={nextFocus} onChange={(e) => setNextFocus(e.target.value)} />
      <label>On track for the quarter?</label>
      <div className="chips">
        <button type="button" className="chip" aria-pressed={onTrack === true}
          onClick={() => setOnTrack(onTrack === true ? null : true)}>yes</button>
        <button type="button" className="chip" aria-pressed={onTrack === false}
          onClick={() => setOnTrack(onTrack === false ? null : false)}>no — adjust</button>
      </div>
      <div className="actions">
        <button className="primary" onClick={save}>Save review</button>
        <span className={"toast" + (toast ? " show" : "")}>saved ✓</span>
      </div>
    </div>
  );
}
