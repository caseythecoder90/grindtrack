import { useCallback, useEffect, useState } from "react";
import DaySheet from "./DaySheet";
import EventForm from "./EventForm";
import MonthGrid from "./MonthGrid";
import UpkeepPanel from "./UpkeepPanel";
import * as calendarApi from "./calendarApi";
import { getPlan } from "../plan/planApi";
import { errorMessage } from "../../lib/api";
import { addMonths, monthOf, todayISO } from "../../lib/dates";
import type { CalendarEvent, PlanItem, UpkeepTask } from "../../lib/types";

const MONTH_NAME = new Intl.DateTimeFormat(undefined, { month: "long", year: "numeric" });

/**
 * The calendar tab: a month, the selected day, and what upkeep is due.
 *
 * <p>The month is one request and the day sheet reads from it, rather than fetching per
 * day — thirty round trips to paint one screen would be slower than the grid it draws.
 */
export default function CalendarPage() {
  const [month, setMonth] = useState(() => monthOf(todayISO()));
  const [selected, setSelected] = useState(todayISO);
  const [events, setEvents] = useState<CalendarEvent[]>([]);
  const [upkeep, setUpkeep] = useState<UpkeepTask[]>([]);
  const [planItems, setPlanItems] = useState<PlanItem[]>([]);
  const [error, setError] = useState("");

  const loadMonth = useCallback(async (m: string) => {
    try {
      setEvents(await calendarApi.getMonth(m));
    } catch (e) {
      setError(errorMessage(e, "could not load the calendar"));
    }
  }, []);

  const loadUpkeep = useCallback(async () => {
    try {
      setUpkeep((await calendarApi.getUpkeep()).due);
    } catch (e) {
      setError(errorMessage(e, "could not load upkeep"));
    }
  }, []);

  useEffect(() => {
    loadMonth(month);
  }, [month, loadMonth]);

  useEffect(() => {
    loadUpkeep();
    // Only study blocks can link to a plan item, and only in-flight ones are worth
    // offering: a done cert is not something you book a morning against.
    getPlan()
      .then((p) => setPlanItems(p.items.filter((i) => i.status !== "done")))
      .catch(() => {
        /* the link is a bonus; the calendar still works without it */
      });
  }, [loadUpkeep]);

  function move(delta: number) {
    const next = addMonths(month, delta);
    setMonth(next);
    // Keep the selection inside the month being looked at, or the day sheet shows a
    // date the grid no longer has highlighted.
    setSelected(`${next}-01`);
  }

  async function addEvent(body: Record<string, unknown>) {
    try {
      await calendarApi.createEvent(body);
      await loadMonth(month);
    } catch (e) {
      setError(errorMessage(e, "could not add that"));
    }
  }

  async function removeEvent(id: number) {
    try {
      await calendarApi.deleteEvent(id);
      await loadMonth(month);
    } catch (e) {
      setError(errorMessage(e, "could not delete that"));
    }
  }

  async function done(id: number) {
    try {
      await calendarApi.markDone(id);
      await loadUpkeep();
    } catch (e) {
      setError(errorMessage(e, "could not record that"));
    }
  }

  async function removeTask(id: number) {
    try {
      await calendarApi.deleteTask(id);
      await loadUpkeep();
    } catch (e) {
      setError(errorMessage(e, "could not delete that"));
    }
  }

  async function addTask(body: Record<string, unknown>) {
    try {
      await calendarApi.createTask(body);
      await loadUpkeep();
    } catch (e) {
      setError(errorMessage(e, "could not add that"));
    }
  }

  const dayEvents = events.filter((e) => e.date === selected);
  const byId = new Map(planItems.map((p) => [p.id, p]));

  return (
    <>
      <div className="panel">
        <div className="calnav">
          <button type="button" aria-label="previous month" onClick={() => move(-1)}>
            ‹
          </button>
          <h2>{MONTH_NAME.format(new Date(`${month}-01T00:00:00`))}</h2>
          <button type="button" aria-label="next month" onClick={() => move(1)}>
            ›
          </button>
          <div className="spacer" />
          <button
            type="button"
            className="linkish"
            onClick={() => {
              setMonth(monthOf(todayISO()));
              setSelected(todayISO());
            }}
          >
            today
          </button>
        </div>

        <MonthGrid month={month} events={events} selected={selected} onSelect={setSelected} />

        <div className="legend callegend">
          <span>
            <span className="sw study" /> study
          </span>
          <span>
            <span className="sw work" /> work
          </span>
          <span>
            <span className="sw appt" /> appointment
          </span>
          <span>
            <span className="sw personal" /> personal
          </span>
        </div>

        {error && <div className="error">{error}</div>}
      </div>

      <div className="panel">
        <DaySheet
          date={selected}
          events={dayEvents}
          planItems={byId}
          onDelete={removeEvent}
        />
        <EventForm date={selected} planItems={planItems} onCreate={addEvent} />
      </div>

      <UpkeepPanel tasks={upkeep} onDone={done} onDelete={removeTask} onCreate={addTask} />
    </>
  );
}
