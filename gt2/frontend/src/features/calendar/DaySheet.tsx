import { KIND_CLASS, endMinutes, startMinutes } from "./model";
import { durationLabel, shortTime } from "../../lib/dates";
import {
  EVENT_KIND_LABEL,
  type CalendarEvent,
  type PlanItem,
} from "../../lib/types";

interface Props {
  date: string;
  events: CalendarEvent[];
  planItems: Map<number, PlanItem>;
  onOpen: (event: CalendarEvent, anchor: DOMRect) => void;
  onAdd: (date: string) => void;
}

const LONG_DATE = new Intl.DateTimeFormat(undefined, {
  weekday: "long",
  day: "numeric",
  month: "short",
});

/** One day's entries, all-day first. A row opens the event; the button at the bottom adds one. */
export default function DaySheet({
  date,
  events,
  planItems,
  onOpen,
  onAdd,
}: Props) {
  return (
    <div className="daysheet">
      <div className="panelhead">
        <h2>{LONG_DATE.format(new Date(date + "T00:00:00"))}</h2>
        <span className="muted mono small">
          {events.length === 0
            ? "nothing on"
            : `${events.length} item${events.length > 1 ? "s" : ""}`}
        </span>
      </div>

      {events.map((e) => {
        const item =
          e.planItemId == null ? undefined : planItems.get(e.planItemId);
        return (
          <button
            key={e.id}
            type="button"
            className={"dayrow " + KIND_CLASS[e.kind]}
            onClick={(ev) =>
              onOpen(e, ev.currentTarget.getBoundingClientRect())
            }
          >
            <div className="daytime mono">
              {e.allDay ? "all day" : shortTime(e.startTime)}
              {!e.allDay && e.endTime && <small>{shortTime(e.endTime)}</small>}
            </div>
            <div className="daybody">
              <div className="daytitle">{e.title}</div>
              <div className="daymeta mono">
                <span>{EVENT_KIND_LABEL[e.kind]}</span>
                {!e.allDay && e.endTime && (
                  <span>{durationLabel(endMinutes(e) - startMinutes(e))}</span>
                )}
                {item && (
                  <span className={"badge badge-" + item.type}>
                    {item.title}
                  </span>
                )}
              </div>
              {e.notes && <p className="daynotes">{e.notes}</p>}
            </div>
          </button>
        );
      })}

      <button type="button" className="addrow" onClick={() => onAdd(date)}>
        + add to this day
      </button>
    </div>
  );
}
