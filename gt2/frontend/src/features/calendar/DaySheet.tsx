import { shortTime } from "../../lib/dates";
import { EVENT_KIND_LABEL, type CalendarEvent, type PlanItem } from "../../lib/types";

interface Props {
  date: string;
  events: CalendarEvent[];
  planItems: Map<number, PlanItem>;
  onDelete: (id: number) => void;
}

const LONG_DATE = new Intl.DateTimeFormat(undefined, {
  weekday: "short",
  day: "numeric",
  month: "short",
});

/** One day's entries, all-day first. */
export default function DaySheet({ date, events, planItems, onDelete }: Props) {
  return (
    <div className="daysheet">
      <div className="panelhead">
        <h2>{LONG_DATE.format(new Date(date + "T00:00:00"))}</h2>
        <span className="muted mono small">
          {events.length === 0 ? "nothing on" : `${events.length} item${events.length > 1 ? "s" : ""}`}
        </span>
      </div>

      {events.map((e) => {
        const item = e.planItemId == null ? undefined : planItems.get(e.planItemId);
        return (
          <div key={e.id} className={"dayrow " + e.kind}>
            <div className="daytime mono">{e.allDay ? "—" : shortTime(e.startTime)}</div>
            <div className="daybody">
              <div className="dayrow-head">
                <span className="daytitle">{e.title}</span>
                <button
                  type="button"
                  className="ghost danger"
                  aria-label={`delete ${e.title}`}
                  onClick={() => onDelete(e.id)}
                >
                  ×
                </button>
              </div>
              <div className="daymeta mono">
                <span>{EVENT_KIND_LABEL[e.kind]}</span>
                {!e.allDay && e.endTime && <span>until {shortTime(e.endTime)}</span>}
                {item && <span className={"badge badge-" + item.type}>{item.title}</span>}
              </div>
              {e.notes && <p className="refpara">{e.notes}</p>}
            </div>
          </div>
        );
      })}
    </div>
  );
}
