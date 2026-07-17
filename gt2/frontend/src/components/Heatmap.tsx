
import { addDays, mondayOf, todayISO } from "../lib/dates";

interface Props {
  days: { date: string; hours: number }[];
}

/** 26-week contribution-style heatmap; one cell per day, colored by hours logged. */
export default function Heatmap({ days }: Props) {
  const today = todayISO();
  const start = mondayOf(addDays(today, -7 * 25));
  const byDate = new Map(days.map((d) => [d.date, d.hours]));
  const cells = [];
  for (let w = 0; w < 26; w++) {
    for (let dow = 0; dow < 7; dow++) {
      const date = addDays(start, w * 7 + dow);
      const hours = byDate.get(date) ?? 0;
      let cls = "cell";
      if (hours > 0) cls += hours < 1 ? " h1" : hours < 2 ? " h2" : hours < 3.5 ? " h3" : " h4";
      if (date === today) cls += " today";
      if (date > today) cls += " future";
      cells.push(<span key={date} className={cls} title={`${date}: ${hours}h`} />);
    }
  }
  return (
    <div className="heatwrap">
      <div className="heatlabel">last 26 weeks · one cell per day · green = hours logged</div>
      <div className="heat" aria-label="Daily hours heatmap">{cells}</div>
      <div className="heatkey">
        0 <span className="cell" /><span className="cell h1" /><span className="cell h2" />
        <span className="cell h3" /><span className="cell h4" /> 4h+
      </div>
    </div>
  );
}
