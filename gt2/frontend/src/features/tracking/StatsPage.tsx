

import { useEffect, useState } from "react";
import { api } from "../../lib/api";
import { WEEKLY_TARGET, type Stats } from "../../lib/types";

const CAT_COLORS = ["#7fd1ff", "#ff2e93", "#7a5cff", "#3fbf5f", "#ffd166", "#ff8c66",
  "#66e0d0", "#c2f06e", "#e08cff", "#ffa3c0", "#8fb8ff", "#d7e3f4"];

function Bars({ rows, max, color }: {
  rows: { label: string; hours: number }[];
  max: number;
  color: (i: number, hours: number) => string;
}) {
  if (rows.length === 0) {
    return <div className="empty">nothing logged yet — the first entry starts the streak</div>;
  }
  return (
    <div className="bars">
      {rows.map((r, i) => (
        <div className="bar" key={r.label}>
          <span className="muted">{r.label}</span>
          <span className="track">
            <i style={{ width: `${Math.max(2, Math.round((r.hours / max) * 100))}%`,
              background: color(i, r.hours) }} />
          </span>
          <span>{r.hours.toFixed(1)}h</span>
        </div>
      ))}
    </div>
  );
}

export default function StatsPage() {
  const [stats, setStats] = useState<Stats | null>(null);

  useEffect(() => {
    api<Stats>("/api/stats").then(setStats);
  }, []);

  if (!stats) return null;
  const weekRows = stats.weeks.map((w) => ({ label: w.weekStart.slice(5), hours: w.hours }));
  const weekMax = Math.max(WEEKLY_TARGET, ...weekRows.map((r) => r.hours), 1);
  const catRows = stats.categories.map((c) => ({ label: c.category, hours: c.hours }));
  const catMax = Math.max(...catRows.map((r) => r.hours), 1);

  return (
    <div className="panel">
      <h2>hours per week · last 12</h2>
      <Bars rows={weekRows} max={weekMax}
        color={(_i, h) => (h >= WEEKLY_TARGET ? "var(--green)" : "var(--cyan)")} />
      <h2 style={{ marginTop: 24 }}>hours by category · all time</h2>
      <Bars rows={catRows} max={catMax} color={(i) => CAT_COLORS[i % CAT_COLORS.length]} />
    </div>
  );
}
