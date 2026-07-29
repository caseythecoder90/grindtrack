import { useEffect, useState } from "react";
import { api } from "../lib/api";
import { addDays } from "../lib/dates";
import { TARGETS, type DayLog, type WorkDay } from "../lib/types";
import Meter from "./Meter";

interface Props {
  weekStart: string;
}

/**
 * The three numbers for a week — study, work, and the two together — shown above
 * both week tabs so either one answers "what did this week actually look like".
 *
 * Fetches both ranges itself rather than taking them as props: the two week views
 * each already load only their own side, and this keeps them from having to know
 * about each other.
 */
export default function WeekTotals({ weekStart }: Props) {
  const [study, setStudy] = useState(0);
  const [work, setWork] = useState(0);

  useEffect(() => {
    let ignore = false;
    const end = addDays(weekStart, 6);
    const sum = <T extends { hours: number }>(rows: T[] | null) =>
      (rows ?? []).reduce((total, r) => total + r.hours, 0);

    Promise.all([
      api<DayLog[]>(`/api/days?from=${weekStart}&to=${end}`).catch(() => null),
      api<WorkDay[]>(`/api/work/days?from=${weekStart}&to=${end}`).catch(() => null),
    ]).then(([days, workDays]) => {
      if (ignore) return;
      setStudy(sum(days));
      setWork(sum(workDays));
    });

    return () => {
      ignore = true;
    };
  }, [weekStart]);

  const total = study + work;

  return (
    <div className="weektotals">
      <div className="wt">
        <span className="k">study</span>
        <span className="v study-ink">{study.toFixed(1)}<small>/{TARGETS.study}h</small></span>
      </div>
      <div className="wt">
        <span className="k">work</span>
        <span className="v work-ink">{work.toFixed(1)}<small>/{TARGETS.work}h</small></span>
      </div>
      <div className="wt">
        <span className="k">total</span>
        <span className="v">{total.toFixed(1)}<small>/{TARGETS.all}h</small></span>
      </div>
      <div className="wtmeter">
        <Meter study={study} work={work} target={TARGETS.all} scope="all" large />
      </div>
    </div>
  );
}
