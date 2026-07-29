import Meter from "../../components/Meter";
import { SCOPE_LABELS, TARGETS, type Scope, type Stats } from "../../lib/types";

interface Props {
  stats: Stats;
  scope: Scope;
}

/**
 * Category bars. One series, so no legend — the heading names it — and the whole
 * ramp is one hue per the scope, since this is magnitude, not identity.
 */
function CategoryBars({ rows, tone }: { rows: { label: string; hours: number }[]; tone: string }) {
  if (rows.length === 0) {
    return <div className="empty">no categorised hours yet — tag a day to see the split</div>;
  }
  const max = Math.max(...rows.map((r) => r.hours), 1);
  return (
    <div className="bars">
      {rows.map((r) => (
        <div className="bar" key={r.label}>
          <span className="blabel">{r.label}</span>
          <span className="track">
            <i className={tone} style={{ width: `${Math.max(2, (r.hours / max) * 100)}%` }} />
          </span>
          <span className="bval">{r.hours.toFixed(1)}h</span>
        </div>
      ))}
    </div>
  );
}

export default function StatsPage({ stats, scope }: Props) {
  const target = TARGETS[scope];
  // Weeks are the same 12 Mondays in every scope, so zipping by index is safe.
  const weeks = stats.study.weeks.map((w, i) => ({
    weekStart: w.weekStart,
    study: w.hours,
    work: stats.work.weeks[i]?.hours ?? 0,
  }));

  const categories = stats[scope].categories.map((c) => ({ label: c.category, hours: c.hours }));

  return (
    <div className="panel">
      <div className="panelhead">
        <h2>hours per week · last 12</h2>
        {scope === "all" && (
          <div className="legend">
            <span><i className="sw study" />study</span>
            <span><i className="sw work" />work</span>
          </div>
        )}
      </div>

      <div className="bars">
        {weeks.map((w) => {
          const total = (scope === "work" ? 0 : w.study) + (scope === "study" ? 0 : w.work);
          return (
            <div className="bar" key={w.weekStart}>
              <span className="blabel">{w.weekStart.slice(5)}</span>
              <Meter study={w.study} work={w.work} target={target} scope={scope} />
              <span className={"bval" + (total >= target ? " on-target" : "")}>
                {total.toFixed(1)}h
              </span>
            </div>
          );
        })}
      </div>
      <p className="axisnote">target {target}h/wk · the marker shows where the target falls
        on a week that beat it</p>

      <h2 style={{ marginTop: 28 }}>
        hours by category · {SCOPE_LABELS[scope]} · all time
      </h2>
      <CategoryBars rows={categories} tone={scope === "work" ? "work" : "study"} />
    </div>
  );
}
