import type { CSSProperties } from "react";
import { addDays, mondayOf, todayISO } from "../lib/dates";
import type { Scope } from "../lib/types";

interface Props {
  study: { date: string; hours: number }[];
  work: { date: string; hours: number }[];
  scope: Scope;
}

const WEEKS = 26;

/**
 * Intensity thresholds per scope. A workday is 6–9h, so reusing the study
 * thresholds would push every single work cell into the top bucket and render a
 * solid slab — the scale has to match the shape of the data it's showing.
 */
const BUCKETS: Record<Scope, [number, number, number]> = {
  study: [1, 2, 3.5],
  work: [2, 5, 7],
  all: [2, 4, 7],
};

/** Ramp steps as CSS custom properties, palest → most saturated. */
const RAMP = {
  study: ["--s1", "--s2", "--s3", "--s4"],
  work: ["--w1", "--w2", "--w3", "--w4"],
};

const LEGEND_MAX: Record<Scope, string> = { study: "4h+", work: "8h+", all: "7h+" };

const DOW = ["Mon", "", "Wed", "", "Fri", "", ""];

/** 0 for an empty day, else 1–4 by intensity within the scope's thresholds. */
function step(hours: number, scope: Scope): number {
  if (hours <= 0) return 0;
  const [a, b, c] = BUCKETS[scope];
  return hours < a ? 1 : hours < b ? 2 : hours < c ? 3 : 4;
}

function monthLabel(date: string): string {
  return new Date(`${date}T00:00:00`).toLocaleString(undefined, { month: "short" });
}

/**
 * 26-week contribution heatmap, coloured by hours logged.
 *
 * In the combined scope a day that holds both kinds is drawn as a hard-stop
 * split — study on the left, work on the right, in proportion — so the ratio is
 * readable without losing the intensity encoding.
 */
export default function Heatmap({ study, work, scope }: Props) {
  const today = todayISO();
  const start = mondayOf(addDays(today, -7 * (WEEKS - 1)));
  const studyBy = new Map(study.map((d) => [d.date, d.hours]));
  const workBy = new Map(work.map((d) => [d.date, d.hours]));

  const cells = [];
  const months = [];
  let lastMonth = "";

  for (let w = 0; w < WEEKS; w++) {
    const weekStart = addDays(start, w * 7);
    const label = monthLabel(weekStart);
    // Label a column only where the month turns over, so ticks don't repeat.
    months.push(
      <span key={weekStart} className="mtick">{label === lastMonth ? "" : label}</span>,
    );
    lastMonth = label;

    for (let dow = 0; dow < 7; dow++) {
      const date = addDays(start, w * 7 + dow);
      const s = studyBy.get(date) ?? 0;
      const k = workBy.get(date) ?? 0;
      const shown = scope === "study" ? s : scope === "work" ? k : s + k;
      const level = step(shown, scope);

      let cls = `cell l${level}`;
      let style: CSSProperties | undefined;

      if (level > 0) {
        if (scope === "all" && s > 0 && k > 0) {
          // Hard stop at the study share; both halves use the same intensity step
          // so the cell still reads as "how much" as well as "which".
          const cut = Math.round((s / (s + k)) * 100);
          const sc = `var(${RAMP.study[level - 1]})`;
          const wc = `var(${RAMP.work[level - 1]})`;
          style = { background: `linear-gradient(90deg, ${sc} ${cut}%, ${wc} ${cut}%)` };
        } else {
          const ramp = scope === "work" || (scope === "all" && k > 0) ? RAMP.work : RAMP.study;
          style = { background: `var(${ramp[level - 1]})` };
        }
      }

      if (date === today) cls += " today";
      if (date > today) cls += " future";

      const parts = [];
      if (scope !== "work" && s > 0) parts.push(`${s.toFixed(1)}h study`);
      if (scope !== "study" && k > 0) parts.push(`${k.toFixed(1)}h work`);
      cells.push(
        <span key={date} className={cls} style={style}
          title={`${date}: ${parts.length ? parts.join(" + ") : "nothing logged"}`} />,
      );
    }
  }

  const ramp = scope === "work" ? RAMP.work : RAMP.study;

  return (
    <div className="heatwrap">
      <div className="heathead">
        <span className="heatlabel">last {WEEKS} weeks</span>
        <span className="heatkey">
          less
          <span className="cell" />
          {ramp.map((v) => <span key={v} className="cell" style={{ background: `var(${v})` }} />)}
          {LEGEND_MAX[scope]}
        </span>
      </div>
      <div className="heatgrid">
        <span />
        <div className="heatmonths">{months}</div>
        <div className="heatdows">
          {DOW.map((d, i) => <span key={i}>{d}</span>)}
        </div>
        <div className="heat" aria-label={`Daily hours heatmap, ${scope}`}>{cells}</div>
      </div>
      {scope === "all" && (
        <div className="heatnote">
          split cells show the study / work share of that day
        </div>
      )}
    </div>
  );
}
