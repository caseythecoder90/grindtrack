import { useEffect, useRef, useState, type MouseEvent } from "react";
import {
  HOUR_PX,
  KIND_CLASS,
  MIN_DRAW_MINUTES,
  endMinutes,
  layoutDay,
  startMinutes,
} from "./model";
import { shortTime, todayISO } from "../../lib/dates";
import type { CalendarEvent } from "../../lib/types";

interface Props {
  /** The days across the top: seven for a week, one for a day. */
  days: string[];
  events: CalendarEvent[];
  /** An empty half hour was clicked. */
  onSlot: (date: string, minutes: number) => void;
  onOpen: (event: CalendarEvent, anchor: DOMRect) => void;
  /** A day's header was clicked. */
  onPickDay: (date: string) => void;
}

const DOW = new Intl.DateTimeFormat(undefined, { weekday: "short" });
const HOURS = Array.from({ length: 24 }, (_, h) => h);

function nowMinutes(): number {
  const d = new Date();
  return d.getHours() * 60 + d.getMinutes();
}

/**
 * Hours down, days across: the week and day views.
 *
 * <p>One scrolling body with a sticky head, rather than the page scrolling, so the day names stay
 * put while you look at the afternoon. It opens on six in the morning — where the study blocks are
 * — or on the first block of the range if that is earlier. A click on an empty half hour is how an
 * event starts; the blocks stop the click, so a click on one opens it instead. Overlapping blocks
 * share the column side by side.
 */
export default function TimeGrid({
  days,
  events,
  onSlot,
  onOpen,
  onPickDay,
}: Props) {
  const today = todayISO();
  const body = useRef<HTMLDivElement>(null);
  const [now, setNow] = useState(nowMinutes);

  useEffect(() => {
    const t = window.setInterval(() => setNow(nowMinutes()), 60_000);
    return () => window.clearInterval(t);
  }, []);

  const key = days.join(",");
  useEffect(() => {
    const el = body.current;
    if (!el) return;
    const firstBlock = Math.min(
      6 * 60,
      ...events.filter((e) => !e.allDay).map(startMinutes),
    );
    el.scrollTop = Math.max(0, (firstBlock - 30) / 60) * HOUR_PX;
    // Only when the days change: re-scrolling every time an event lands would pull the grid out
    // from under the one you just made.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key]);

  const byDate = new Map<string, CalendarEvent[]>();
  for (const e of events) {
    const list = byDate.get(e.date);
    if (list) list.push(e);
    else byDate.set(e.date, [e]);
  }
  const hasAllDay = days.some((d) =>
    (byDate.get(d) ?? []).some((e) => e.allDay),
  );
  const columns = {
    gridTemplateColumns: `var(--gutter) repeat(${days.length}, minmax(0, 1fr))`,
  };

  function slot(e: MouseEvent<HTMLDivElement>, date: string) {
    const rect = e.currentTarget.getBoundingClientRect();
    const minutes = Math.floor(((e.clientY - rect.top) / HOUR_PX) * 60);
    onSlot(date, Math.floor(minutes / 30) * 30);
  }

  return (
    <div className={"tg" + (days.length === 1 ? " tg-one" : "")}>
      <div className="tg-head" style={columns}>
        <div className="tg-gutter" />
        {days.map((d) => (
          <button
            key={d}
            type="button"
            className={"tg-day" + (d === today ? " is-today" : "")}
            aria-current={d === today ? "date" : undefined}
            onClick={() => onPickDay(d)}
          >
            <span className="tg-dow">
              {DOW.format(new Date(d + "T00:00:00"))}
            </span>
            <span className="tg-num">{Number(d.slice(8))}</span>
          </button>
        ))}
      </div>

      {hasAllDay && (
        <div className="tg-allday" style={columns}>
          <div className="tg-gutter mono">all day</div>
          {days.map((d) => (
            <div key={d} className="tg-allday-col">
              {(byDate.get(d) ?? [])
                .filter((e) => e.allDay)
                .map((e) => (
                  <button
                    key={e.id}
                    type="button"
                    className={"tg-chip " + KIND_CLASS[e.kind]}
                    onClick={(ev) =>
                      onOpen(e, ev.currentTarget.getBoundingClientRect())
                    }
                  >
                    {e.title}
                  </button>
                ))}
            </div>
          ))}
        </div>
      )}

      <div className="tg-body" ref={body}>
        <div className="tg-canvas" style={columns}>
          <div className="tg-hours">
            {HOURS.map((h) => (
              <div key={h} className="tg-hour mono">
                {h === 0 ? "" : `${String(h).padStart(2, "0")}:00`}
              </div>
            ))}
          </div>
          {days.map((d) => (
            <div
              key={d}
              className={"tg-col" + (d === today ? " is-today" : "")}
              onClick={(e) => slot(e, d)}
            >
              {layoutDay(byDate.get(d) ?? []).map(
                ({ event, left, width, layer }) => {
                  const start = startMinutes(event);
                  const length = Math.max(
                    endMinutes(event) - start,
                    MIN_DRAW_MINUTES,
                  );
                  return (
                    <button
                      key={event.id}
                      type="button"
                      className={
                        "tg-ev " +
                        KIND_CLASS[event.kind] +
                        (event.endTime ? "" : " open")
                      }
                      style={{
                        top: (start / 60) * HOUR_PX,
                        height: (length / 60) * HOUR_PX - 2,
                        left: `calc(${left * 100}% + 1px)`,
                        width: `calc(${width * 100}% - 3px)`,
                        zIndex: layer,
                      }}
                      title={`${event.title} · ${shortTime(event.startTime)}${event.endTime ? "–" + shortTime(event.endTime) : ""}`}
                      onClick={(ev) => {
                        ev.stopPropagation();
                        onOpen(event, ev.currentTarget.getBoundingClientRect());
                      }}
                    >
                      <span className="tg-ev-title">{event.title}</span>
                      <span className="tg-ev-time mono">
                        {shortTime(event.startTime)}
                        {event.endTime ? ` – ${shortTime(event.endTime)}` : ""}
                      </span>
                    </button>
                  );
                },
              )}
              {d === today && (
                <div
                  className="tg-now"
                  style={{ top: (now / 60) * HOUR_PX }}
                  aria-hidden="true"
                />
              )}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
