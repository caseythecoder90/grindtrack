import { mondayOf, todayISO } from "../lib/dates";
import { SCOPE_LABELS, SCOPES, TARGETS, type Scope, type Stats } from "../lib/types";
import Meter from "./Meter";
import Segmented from "./Segmented";

interface Props {
  stats: Stats;
  scope: Scope;
  onScopeChange: (scope: Scope) => void;
}

function hoursThisWeek(weeks: { weekStart: string; hours: number }[]): number {
  const monday = mondayOf(todayISO());
  return weeks.find((w) => w.weekStart === monday)?.hours ?? 0;
}

/**
 * The header: which side of the day you're looking at, and whether this week is
 * on track. The week tile is deliberately the loudest thing on the page — it's
 * the only number that can still be acted on today.
 */
export default function StatBar({ stats, scope, onScopeChange }: Props) {
  const current = stats[scope];
  const studyWeek = hoursThisWeek(stats.study.weeks);
  const workWeek = hoursThisWeek(stats.work.weeks);
  const week = scope === "study" ? studyWeek : scope === "work" ? workWeek : studyWeek + workWeek;
  const target = TARGETS[scope];

  return (
    <section className="statbar" aria-label="Progress summary">
      <div className="scopebar">
        <Segmented
          label="Which hours to show"
          value={scope}
          onChange={onScopeChange}
          options={SCOPES.map((s) => ({ value: s, label: SCOPE_LABELS[s], tone: s }))}
        />
      </div>

      <div className="stats">
        <div className="stat primary">
          <span className="k">this week</span>
          <span className={"v hero " + (week >= target ? "on-target" : "")}>
            {week.toFixed(1)}
            <small>/{target}h</small>
          </span>
          <Meter study={studyWeek} work={workWeek} target={target} scope={scope} large />
          {scope === "all" ? (
            <span className="split">
              <b className="study-ink">{studyWeek.toFixed(1)}h</b> study
              <span className="sep">·</span>
              <b className="work-ink">{workWeek.toFixed(1)}h</b> work
            </span>
          ) : (
            <span className="split muted">{SCOPE_LABELS[scope]} · target {target}h</span>
          )}
        </div>

        {/* A work streak would reset every Saturday, so that scope counts days instead. */}
        {scope === "work" ? (
          <div className="stat">
            <span className="k">days this month</span>
            <span className="v">{current.daysThisMonth}</span>
          </div>
        ) : (
          <div className="stat">
            <span className="k">streak</span>
            <span className="v">{current.streak}d</span>
          </div>
        )}

        <div className="stat">
          <span className="k">total hours</span>
          <span className="v">{current.totalHours.toFixed(0)}</span>
        </div>

        <div className="stat">
          <span className="k">days logged</span>
          <span className="v">{current.daysLogged}</span>
        </div>
      </div>
    </section>
  );
}
