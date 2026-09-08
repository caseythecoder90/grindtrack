import { monthGrid, todayISO } from "../../lib/dates";
import type { CalendarEvent, EventKind } from "../../lib/types";

interface Props {
  month: string;
  events: CalendarEvent[];
  selected: string;
  onSelect: (date: string) => void;
}

/** Which colour a day's dot gets. Study green and work purple mean the same here as everywhere. */
const DOT: Record<EventKind, string> = {
  study_block: "study",
  work_block: "work",
  appointment: "appt",
  personal: "personal",
};

/**
 * The month, six rows deep.
 *
 * <p>Always six weeks, never five-or-six: a grid that changes height when you page from
 * September to October shifts the day sheet underneath it, and on a phone that means the
 * thing you were reading jumps out from under your thumb.
 */
export default function MonthGrid({ month, events, selected, onSelect }: Props) {
  const today = todayISO();
  const byDate = new Map<string, CalendarEvent[]>();
  for (const e of events) {
    const list = byDate.get(e.date);
    if (list) list.push(e);
    else byDate.set(e.date, [e]);
  }

  return (
    <div className="monthgrid">
      <div className="monthgrid-dows" aria-hidden="true">
        {["m", "t", "w", "t", "f", "s", "s"].map((d, i) => (
          <span key={i}>{d}</span>
        ))}
      </div>
      <div className="monthgrid-days">
        {monthGrid(month).map((date) => {
          const dayEvents = byDate.get(date) ?? [];
          // Days from the neighbouring months keep their place in the grid but recede:
          // they are context for where the week starts, not somewhere to navigate to.
          const outside = date.slice(0, 7) !== month;
          const classes = ["daycell"];
          if (outside) classes.push("outside");
          if (date === selected) classes.push("selected");
          if (date === today) classes.push("is-today");

          return (
            <button
              key={date}
              type="button"
              className={classes.join(" ")}
              aria-current={date === today ? "date" : undefined}
              aria-pressed={date === selected}
              onClick={() => onSelect(date)}
            >
              <span className="daynum">{Number(date.slice(8))}</span>
              <span className="daydots">
                {/* Three at most. A fourth dot says nothing a third does not, and the
                    row it would force is the day number's. */}
                {dayEvents.slice(0, 3).map((e) => (
                  <i key={e.id} className={DOT[e.kind]} />
                ))}
              </span>
            </button>
          );
        })}
      </div>
    </div>
  );
}
