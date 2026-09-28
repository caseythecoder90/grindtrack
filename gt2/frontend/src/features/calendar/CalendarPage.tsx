import { useCallback, useEffect, useState } from "react";
import DaySheet from "./DaySheet";
import EventEditor from "./EventEditor";
import EventPeek from "./EventPeek";
import MonthGrid from "./MonthGrid";
import TimeGrid from "./TimeGrid";
import UpkeepPanel from "./UpkeepPanel";
import * as calendarApi from "./calendarApi";
import { createBody, draftOf, newDraft, updateBody, type Draft } from "./model";
import { getPlan } from "../plan/planApi";
import { errorMessage } from "../../lib/api";
import {
  addDays,
  addMonths,
  monthGrid,
  monthOf,
  todayISO,
  weekOf,
} from "../../lib/dates";
import type { CalendarEvent, PlanItem, UpkeepTask } from "../../lib/types";
import { useAppResume } from "../../lib/resume";
import WeekPlanCard from "../assistant/WeekPlanCard";

type View = "month" | "week" | "day";
const VIEW_KEY = "gt.calendar.view";
const VIEWS: View[] = ["month", "week", "day"];

const MONTH_NAME = new Intl.DateTimeFormat(undefined, {
  month: "long",
  year: "numeric",
});
const DAY_NAME = new Intl.DateTimeFormat(undefined, {
  weekday: "long",
  day: "numeric",
  month: "long",
});
const SHORT = new Intl.DateTimeFormat(undefined, {
  day: "numeric",
  month: "short",
});

function readView(): View {
  try {
    const v = localStorage.getItem(VIEW_KEY);
    return v === "week" || v === "day" ? v : "month";
  } catch {
    return "month";
  }
}

/** The days a view shows around a date: the six-week grid, the week, or the day itself. */
function spanOf(view: View, date: string): string[] {
  if (view === "month") return monthGrid(monthOf(date));
  if (view === "week") return weekOf(date);
  return [date];
}

/** "September 2026", "28 Sep – 4 Oct", "Monday, 28 September". */
function titleOf(view: View, date: string): string {
  if (view === "month")
    return MONTH_NAME.format(new Date(`${monthOf(date)}-01T00:00:00`));
  if (view === "week") {
    const days = weekOf(date);
    return `${SHORT.format(new Date(days[0] + "T00:00:00"))} – ${SHORT.format(new Date(days[6] + "T00:00:00"))}`;
  }
  return DAY_NAME.format(new Date(date + "T00:00:00"));
}

/** The next full hour, for a block added to today with no time in mind. */
function nextHour(): number {
  const d = new Date();
  return Math.min(23 * 60, (d.getHours() + 1) * 60);
}

/**
 * The calendar tab: a month, a week or a day, the selected day's entries, and what upkeep is due.
 *
 * <p>One request per visible span rather than per day. The view is remembered on the device; the
 * date is not, because a calendar that opens on the day you last looked at instead of today is a
 * calendar you distrust.
 *
 * <p>Two popovers, one at a time: a peek at an event (read it, edit it, delete it) and the editor.
 * Both close on Escape and on a click outside; a save re-reads the span so what is drawn is what
 * the server has.
 */
export default function CalendarPage() {
  const [view, setView] = useState<View>(readView);
  const [date, setDate] = useState(todayISO);
  const [events, setEvents] = useState<CalendarEvent[]>([]);
  const [upkeep, setUpkeep] = useState<UpkeepTask[]>([]);
  const [planItems, setPlanItems] = useState<PlanItem[]>([]);
  const [error, setError] = useState("");
  const [peek, setPeek] = useState<{
    event: CalendarEvent;
    anchor: DOMRect | null;
  } | null>(null);
  const [editing, setEditing] = useState<Draft | null>(null);

  const span = spanOf(view, date);
  const from = span[0];
  const to = span[span.length - 1];

  const loadSpan = useCallback(async () => {
    try {
      setEvents(await calendarApi.getRange(from, to));
    } catch (e) {
      setError(errorMessage(e, "could not load the calendar"));
    }
  }, [from, to]);

  const loadUpkeep = useCallback(async () => {
    try {
      setUpkeep((await calendarApi.getUpkeep()).due);
    } catch (e) {
      setError(errorMessage(e, "could not load upkeep"));
    }
  }, []);

  // Anything added or ticked off on another device since this screen loaded.
  useAppResume(() => {
    void loadSpan();
    void loadUpkeep();
  });

  useEffect(() => {
    void loadSpan();
  }, [loadSpan]);

  useEffect(() => {
    void loadUpkeep();
    // Only study blocks can link to a plan item, and only in-flight ones are worth
    // offering: a done cert is not something you book a morning against.
    getPlan()
      .then((p) => setPlanItems(p.items.filter((i) => i.status !== "done")))
      .catch(() => {
        /* the link is a bonus; the calendar still works without it */
      });
  }, [loadUpkeep]);

  function pickView(v: View) {
    setView(v);
    try {
      localStorage.setItem(VIEW_KEY, v);
    } catch {
      /* a private window forgets; nothing to do */
    }
  }

  function move(delta: number) {
    if (view === "month") {
      const next = addMonths(monthOf(date), delta);
      // Back on this month, back on today; anywhere else, the first of the month, so the
      // selection stays inside what the grid highlights.
      setDate(next === monthOf(todayISO()) ? todayISO() : `${next}-01`);
    } else {
      setDate(addDays(date, view === "week" ? 7 * delta : delta));
    }
  }

  function openEditor(draft: Draft) {
    setPeek(null);
    setEditing(draft);
  }

  function add(day: string, minutes?: number) {
    const at = minutes ?? (day === todayISO() ? nextHour() : undefined);
    openEditor(newDraft(day, at));
  }

  async function save(d: Draft) {
    if (d.id == null) await calendarApi.createEvent(createBody(d));
    else await calendarApi.updateEvent(d.id, updateBody(d));
    await loadSpan();
    setEditing(null);
    setDate(d.date);
  }

  async function remove(id: number) {
    await calendarApi.deleteEvent(id);
    await loadSpan();
    setEditing(null);
    setPeek(null);
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

  const byId = new Map(planItems.map((p) => [p.id, p]));
  const open = (event: CalendarEvent, anchor: DOMRect) =>
    setPeek({ event, anchor });

  // The Monday of next week: planning is always forward-looking, and "next week" is the only
  // week a proposal can still change.
  const nextMonday = (() => {
    const d = new Date(todayISO() + "T00:00:00");
    d.setDate(d.getDate() + ((8 - d.getDay()) % 7 || 7));
    return d.toISOString().slice(0, 10);
  })();

  return (
    <>
      <WeekPlanCard weekStart={nextMonday} onBooked={() => void loadSpan()} />
      <div className={"panel calpage view-" + view}>
        <div className="calnav">
          <button
            type="button"
            aria-label={`previous ${view}`}
            onClick={() => move(-1)}
          >
            ‹
          </button>
          <button
            type="button"
            aria-label={`next ${view}`}
            onClick={() => move(1)}
          >
            ›
          </button>
          <h2>{titleOf(view, date)}</h2>
          <button
            type="button"
            className="linkish"
            onClick={() => setDate(todayISO())}
          >
            today
          </button>
          <div className="spacer" />
          <div className="seg" role="group" aria-label="view">
            {VIEWS.map((v) => (
              <button
                key={v}
                type="button"
                aria-pressed={view === v}
                onClick={() => pickView(v)}
              >
                {v}
              </button>
            ))}
          </div>
          <button
            type="button"
            className="primary calnew"
            onClick={() => add(date)}
          >
            + new
          </button>
        </div>

        {view === "month" ? (
          <div className="cal-month">
            <MonthGrid
              month={monthOf(date)}
              events={events}
              selected={date}
              onSelect={setDate}
              onOpen={open}
              onAdd={(d) => add(d)}
            />
            <DaySheet
              date={date}
              events={events.filter((e) => e.date === date)}
              planItems={byId}
              onOpen={open}
              onAdd={(d) => add(d)}
            />
          </div>
        ) : (
          <TimeGrid
            days={span}
            events={events}
            onSlot={(d, minutes) => add(d, minutes)}
            onOpen={open}
            onPickDay={(d) => {
              setDate(d);
              pickView("day");
            }}
          />
        )}

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

      <UpkeepPanel
        tasks={upkeep}
        onDone={done}
        onDelete={removeTask}
        onCreate={addTask}
      />

      {peek && (
        <EventPeek
          event={peek.event}
          anchor={peek.anchor}
          planItem={
            peek.event.planItemId == null
              ? undefined
              : byId.get(peek.event.planItemId)
          }
          onEdit={() => openEditor(draftOf(peek.event))}
          onDelete={() =>
            void remove(peek.event.id).catch((e) =>
              setError(errorMessage(e, "could not delete that")),
            )
          }
          onClose={() => setPeek(null)}
        />
      )}
      {editing && (
        <EventEditor
          draft={editing}
          planItems={planItems}
          onSave={save}
          onDelete={editing.id == null ? null : remove}
          onClose={() => setEditing(null)}
        />
      )}
    </>
  );
}
