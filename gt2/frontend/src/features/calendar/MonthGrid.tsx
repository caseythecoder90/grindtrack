import { KIND_CLASS } from "./model";
import { monthGrid, shortTime, todayISO } from "../../lib/dates";
import type { CalendarEvent } from "../../lib/types";

interface Props {
  month: string;
  events: CalendarEvent[];
  selected: string;
  onSelect: (date: string) => void;
  onOpen: (event: CalendarEvent, anchor: DOMRect) => void;
  onAdd: (date: string) => void;
}

/** How many entries a cell names before it says "+N more". */
const SHOWN = 3;

/**
 * The month, six rows deep.
 *
 * <p>Always six weeks, never five-or-six: a grid that changes height when you page from
 * September to October shifts what is under it, and on a phone that means the thing you were
 * reading jumps out from under your thumb.
 *
 * <p>On a mouse screen each cell names its entries, the way a wall calendar does, and a "+"
 * appears on hover to add one to that day; on a phone the cells are too small for words, so
 * they carry dots and the day sheet under the grid does the naming. Both are rendered here and
 * styles.css shows one or the other by pointer.
 */
export default function MonthGrid({
  month,
  events,
  selected,
  onSelect,
  onOpen,
  onAdd,
}: Props) {
  const today = todayISO();
  const byDate = new Map<string, CalendarEvent[]>();
  for (const e of events) {
    const list = byDate.get(e.date);
    if (list) list.push(e);
    else byDate.set(e.date, [e]);
  }

  return (
    <div className="monthgrid" role="grid">
      <div className="monthgrid-dows" aria-hidden="true">
        {["mon", "tue", "wed", "thu", "fri", "sat", "sun"].map((d) => (
          <span key={d}>{d}</span>
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
          const more = dayEvents.length - SHOWN;

          return (
            <div
              key={date}
              role="gridcell"
              className={classes.join(" ")}
              aria-selected={date === selected}
              onClick={() => onSelect(date)}
            >
              <div className="dayhead">
                <button
                  type="button"
                  className="daynum"
                  aria-current={date === today ? "date" : undefined}
                  aria-label={date}
                  onClick={(e) => {
                    e.stopPropagation();
                    onSelect(date);
                  }}
                >
                  {Number(date.slice(8))}
                </button>
                <button
                  type="button"
                  className="dayadd"
                  aria-label={`add to ${date}`}
                  onClick={(e) => {
                    e.stopPropagation();
                    onAdd(date);
                  }}
                >
                  +
                </button>
              </div>
              <span className="daydots" aria-hidden="true">
                {/* Three at most. A fourth dot says nothing a third does not, and the
                    row it would force is the day number's. */}
                {dayEvents.slice(0, 3).map((e) => (
                  <i key={e.id} className={KIND_CLASS[e.kind]} />
                ))}
              </span>
              <div className="daychips">
                {dayEvents.slice(0, SHOWN).map((e) => (
                  <button
                    key={e.id}
                    type="button"
                    className={
                      "daychip " +
                      KIND_CLASS[e.kind] +
                      (e.allDay ? " allday" : "")
                    }
                    onClick={(ev) => {
                      ev.stopPropagation();
                      onOpen(e, ev.currentTarget.getBoundingClientRect());
                    }}
                  >
                    {!e.allDay && (
                      <span className="mono">{shortTime(e.startTime)}</span>
                    )}{" "}
                    {e.title}
                  </button>
                ))}
                {more > 0 && <span className="daymore mono">+{more} more</span>}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
