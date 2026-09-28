/**
 * The calendar's shared vocabulary: how a kind is coloured, how a day's timed blocks share a
 * column, and the draft an event is edited through.
 */
import { minutesOf, timeOf } from "../../lib/dates";
import type { CalendarEvent, EventKind } from "../../lib/types";

/** The CSS class a kind paints with. Study green and work purple mean the same here as everywhere. */
export const KIND_CLASS: Record<EventKind, string> = {
  study_block: "study",
  work_block: "work",
  appointment: "appt",
  personal: "personal",
};

export const KINDS: EventKind[] = [
  "study_block",
  "work_block",
  "appointment",
  "personal",
];

/** Pixels per hour in the time grid. Mirrored by `--hour` in styles.css. */
export const HOUR_PX = 48;

/** A block without an end is drawn an hour long; anything shorter than half an hour, half an hour. */
export const DEFAULT_MINUTES = 60;
export const MIN_DRAW_MINUTES = 30;

/** The lead options the editor offers. "" is the app's default, which depends on the kind. */
export const REMIND_OPTIONS: { value: string; label: string }[] = [
  { value: "", label: "app default" },
  { value: "0", label: "no reminder" },
  { value: "5", label: "5 minutes before" },
  { value: "10", label: "10 minutes before" },
  { value: "15", label: "15 minutes before" },
  { value: "30", label: "30 minutes before" },
  { value: "60", label: "1 hour before" },
  { value: "120", label: "2 hours before" },
  { value: "1440", label: "1 day before" },
];

/** What the reminder line says about an event, given the app's default lead. */
export function reminderLabel(e: CalendarEvent): string {
  if (e.allDay) return "no reminder (all day)";
  const lead = e.remindMinutes ?? (e.kind === "work_block" ? 0 : 10);
  if (lead === 0)
    return e.remindMinutes == null ? "no reminder (work block)" : "no reminder";
  const option = REMIND_OPTIONS.find((o) => o.value === String(lead));
  return (
    (option ? option.label : `${lead} minutes before`) +
    (e.remindMinutes == null ? " (default)" : "")
  );
}

export function startMinutes(e: CalendarEvent): number {
  return e.startTime ? minutesOf(e.startTime) : 0;
}

/** Where the block ends on the grid: its end, or an hour after its start. */
export function endMinutes(e: CalendarEvent): number {
  const start = startMinutes(e);
  return e.endTime ? minutesOf(e.endTime) : start + DEFAULT_MINUTES;
}

export interface Placed {
  event: CalendarEvent;
  /** Left edge and width of the block, as fractions of the column. */
  left: number;
  width: number;
  /** Blocks that sit on top of a longer one are drawn above it. */
  layer: number;
}

/** A quarter hour: a block starting this much after another sits on it rather than beside it. */
const NESTED_AFTER = 15;

/**
 * Lays one day's timed blocks out where they overlap.
 *
 * <p>Two shapes of overlap, and they should not look the same. Two blocks that start together
 * — a call booked over a study block — share the column side by side. A short block that starts
 * well inside a long one — a standup inside the work day — sits on top of it, indented, the way
 * the calendars people already know draw it; slicing an eight-hour block to a third of the
 * column for a fifteen-minute standup would hide the block the day is mostly made of.
 *
 * <p>Blocks are clustered by overlap; within a cluster each takes the leftmost lane free when it
 * starts. A block in a later lane whose every overlap in earlier lanes started at least a quarter
 * hour before it is nested; the rest are side by side, sharing the width with the lanes they
 * touch. Blocks that do not touch each other's time both get the full width. The overlap is the
 * real one: a fifteen-minute standup is drawn half an hour tall, but it does not push the block
 * after it into another lane.
 */
export function layoutDay(events: CalendarEvent[]): Placed[] {
  const timed = events
    .filter((e) => !e.allDay)
    .map((e) => ({
      e,
      s: startMinutes(e),
      en: endMinutes(e),
    }))
    .sort((a, b) => a.s - b.s || b.en - a.en);
  type Item = (typeof timed)[number];
  const overlaps = (a: Item, b: Item) => a.s < b.en && b.s < a.en;

  const out: Placed[] = [];
  let cluster: Item[] = [];
  let clusterEnd = -1;

  const flush = () => {
    const laneEnds: number[] = [];
    const placed = cluster.map((item) => {
      let lane = laneEnds.findIndex((end) => end <= item.s);
      if (lane < 0) {
        lane = laneEnds.length;
        laneEnds.push(item.en);
      } else {
        laneEnds[lane] = item.en;
      }
      return { item, lane, nested: false };
    });
    for (const p of placed) {
      const under = placed.filter(
        (q) => q.lane < p.lane && overlaps(q.item, p.item),
      );
      p.nested =
        p.lane > 0 && under.every((q) => p.item.s - q.item.s >= NESTED_AFTER);
    }
    for (const p of placed) {
      if (p.nested) {
        const indent = Math.min(0.14 * p.lane, 0.6);
        out.push({
          event: p.item.e,
          left: indent,
          width: 1 - indent,
          layer: p.lane + 1,
        });
        continue;
      }
      // Side by side with the lanes of the blocks it touches that are not nested on something.
      const lanes = new Set<number>([p.lane]);
      for (const q of placed) {
        if (q !== p && !q.nested && overlaps(q.item, p.item)) lanes.add(q.lane);
      }
      const order = [...lanes].sort((a, b) => a - b);
      out.push({
        event: p.item.e,
        left: order.indexOf(p.lane) / order.length,
        width: 1 / order.length,
        layer: 1,
      });
    }
    cluster = [];
  };

  for (const item of timed) {
    if (cluster.length > 0 && item.s >= clusterEnd) flush();
    cluster.push(item);
    clusterEnd = Math.max(clusterEnd, item.en);
  }
  if (cluster.length > 0) flush();
  return out;
}

/** True on a phone or tablet, where popovers become sheets. Same query as styles.css. */
export function isCoarse(): boolean {
  return (
    typeof window !== "undefined" &&
    window.matchMedia?.("(pointer: coarse)").matches === true
  );
}

/** An event as the editor holds it: strings throughout, because that is what inputs hold. */
export interface Draft {
  id: number | null;
  title: string;
  kind: EventKind;
  date: string;
  allDay: boolean;
  /** HH:mm */
  startTime: string;
  /** HH:mm, or "" for open-ended. */
  endTime: string;
  planItemId: string;
  notes: string;
  /** One of REMIND_OPTIONS' values. */
  remind: string;
}

/** A fresh draft on a day, at a time (in minutes) or at six in the morning. */
export function newDraft(
  date: string,
  startAt?: number,
  kind: EventKind = "study_block",
): Draft {
  const start = startAt ?? 6 * 60;
  return {
    id: null,
    title: "",
    kind,
    date,
    allDay: false,
    startTime: timeOf(start),
    endTime: timeOf(start + DEFAULT_MINUTES),
    planItemId: "",
    notes: "",
    remind: "",
  };
}

export function draftOf(e: CalendarEvent): Draft {
  return {
    id: e.id,
    title: e.title,
    kind: e.kind,
    date: e.date,
    allDay: e.allDay,
    startTime: e.startTime ? e.startTime.slice(0, 5) : "06:00",
    endTime: e.endTime ? e.endTime.slice(0, 5) : "",
    planItemId: e.planItemId == null ? "" : String(e.planItemId),
    notes: e.notes,
    remind: e.remindMinutes == null ? "" : String(e.remindMinutes),
  };
}

/** Why a draft cannot be saved, or "" when it can. */
export function problemWith(d: Draft): string {
  if (!d.title.trim()) return "give it a title";
  if (!d.date) return "pick a day";
  if (!d.allDay) {
    if (!d.startTime) return "when does it start?";
    if (d.endTime && minutesOf(d.endTime) <= minutesOf(d.startTime)) {
      return "the end has to come after the start";
    }
  }
  return "";
}

/** The POST body for a new event. */
export function createBody(d: Draft): Record<string, unknown> {
  return {
    title: d.title.trim(),
    kind: d.kind,
    date: d.date,
    startTime: d.allDay ? null : d.startTime,
    endTime: d.allDay || !d.endTime ? null : d.endTime,
    planItemId:
      d.kind === "study_block" && d.planItemId ? Number(d.planItemId) : null,
    notes: d.notes.trim(),
    remindMinutes: d.remind === "" ? null : Number(d.remind),
  };
}

/**
 * The PATCH body that turns the original into the draft. Every field is sent: the editor showed
 * them all, so what it shows is what is saved, and a cleared end or plan item is cleared rather
 * than silently kept.
 */
export function updateBody(d: Draft): Record<string, unknown> {
  const body: Record<string, unknown> = {
    title: d.title.trim(),
    kind: d.kind,
    date: d.date,
    notes: d.notes.trim(),
  };
  if (d.allDay) {
    body.clearTimes = true;
  } else {
    body.startTime = d.startTime;
    if (d.endTime) body.endTime = d.endTime;
    else body.clearEndTime = true;
  }
  if (d.kind === "study_block" && d.planItemId)
    body.planItemId = Number(d.planItemId);
  else body.clearPlanItem = true;
  if (d.remind === "") body.clearReminder = true;
  else body.remindMinutes = Number(d.remind);
  return body;
}
