
import { WEEKLY_TARGET } from "../lib/types";

interface Props {
  streak: number;
  weekHours?: number;
  totalHours: number;
  daysLogged: number;
}

export default function StatBar({ streak, weekHours, totalHours, daysLogged }: Props) {
  return (
    <div className="statbar">
      <div className="stat"><span className="k">streak</span><span className="v magenta">{streak}d</span></div>
      {weekHours !== undefined && (
        <div className="stat">
          <span className="k">this week</span>
          <span className={"v " + (weekHours >= WEEKLY_TARGET ? "green" : "cyan")}>
            {weekHours.toFixed(1)}/{WEEKLY_TARGET}h
          </span>
        </div>
      )}
      <div className="stat"><span className="k">total hours</span><span className="v cyan">{totalHours.toFixed(0)}</span></div>
      <div className="stat"><span className="k">days logged</span><span className="v yellow">{daysLogged}</span></div>
    </div>
  );
}
