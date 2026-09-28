import Popover from "./Popover";
import { KIND_CLASS, endMinutes, reminderLabel, startMinutes } from "./model";
import { durationLabel, shortTime } from "../../lib/dates";
import {
  EVENT_KIND_LABEL,
  type CalendarEvent,
  type PlanItem,
} from "../../lib/types";

interface Props {
  event: CalendarEvent;
  anchor: DOMRect | null;
  planItem: PlanItem | undefined;
  onEdit: () => void;
  onDelete: () => void;
  onClose: () => void;
}

const LONG_DATE = new Intl.DateTimeFormat(undefined, {
  weekday: "long",
  day: "numeric",
  month: "long",
});

/**
 * One event, read in full: when, how long, what kind, what it is against, whether the phone will
 * say so, and the notes. The two things you can do to it are at the bottom.
 */
export default function EventPeek({
  event,
  anchor,
  planItem,
  onEdit,
  onDelete,
  onClose,
}: Props) {
  const when = event.allDay
    ? "all day"
    : event.endTime
      ? `${shortTime(event.startTime)} – ${shortTime(event.endTime)} · ${durationLabel(endMinutes(event) - startMinutes(event))}`
      : `${shortTime(event.startTime)} · open-ended`;

  return (
    <Popover
      anchor={anchor}
      label={event.title}
      onClose={onClose}
      className="peek"
    >
      <div className={"peek-head " + KIND_CLASS[event.kind]}>
        <div className="peek-title">{event.title}</div>
        <button
          type="button"
          className="ghost"
          aria-label="close"
          onClick={onClose}
        >
          ×
        </button>
      </div>
      <dl className="peek-facts">
        <dt>when</dt>
        <dd>
          {LONG_DATE.format(new Date(event.date + "T00:00:00"))}
          <br />
          <span className="mono">{when}</span>
        </dd>
        <dt>kind</dt>
        <dd>
          <span className={"peek-kind " + KIND_CLASS[event.kind]}>
            {EVENT_KIND_LABEL[event.kind]}
          </span>
        </dd>
        {planItem && (
          <>
            <dt>against</dt>
            <dd>
              <span className={"badge badge-" + planItem.type}>
                {planItem.title}
              </span>
            </dd>
          </>
        )}
        <dt>reminder</dt>
        <dd className="mono small">{reminderLabel(event)}</dd>
        {event.notes && (
          <>
            <dt>notes</dt>
            <dd className="peek-notes">{event.notes}</dd>
          </>
        )}
      </dl>
      <div className="peek-actions">
        <button type="button" className="ghost danger" onClick={onDelete}>
          delete
        </button>
        <span className="spacer" />
        <button type="button" className="primary" onClick={onEdit}>
          edit
        </button>
      </div>
    </Popover>
  );
}
