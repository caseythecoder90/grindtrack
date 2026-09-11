import { useCallback, useEffect, useState } from "react";
import { errorMessage } from "../../lib/api";
import { acceptWeekPlan, getWeekPlan, type WeekPlan } from "./assistantApi";

const DOW = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

/** "Mon 15" — enough to read the shape of a week without a date parser in your head. */
function dayLabel(date: string): string {
  const d = new Date(date + "T00:00:00");
  return `${DOW[d.getDay()]} ${d.getDate()}`;
}

function hours(plan: WeekPlan): string {
  const minutes = plan.blocks.reduce((total, b) => {
    const [sh, sm] = b.startTime.split(":").map(Number);
    const [eh, em] = b.endTime.split(":").map(Number);
    return total + (eh * 60 + em - (sh * 60 + sm));
  }, 0);
  return (Math.round((minutes / 60) * 10) / 10).toString();
}

/**
 * A week the assistant drafted mid-conversation, waiting on a click.
 *
 * <p>The card is in the thread rather than on another tab because that is where the asking
 * happened — being told "I've drafted it, go to the calendar tab" is a worse answer than showing
 * it. It is the same draft either way: one row, one accept endpoint, one set of validation.
 *
 * <p>It fetches rather than being handed the blocks. The turn only carries which week was drafted,
 * so what renders here is whatever the server currently holds — which is also what accepting will
 * book. A card showing blocks the server no longer has is the bug this avoids.
 */
export default function ProposedWeek({
  weekStart,
  onBooked,
}: {
  weekStart: string;
  onBooked: () => void;
}) {
  const [plan, setPlan] = useState<WeekPlan | null>(null);
  const [booked, setBooked] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [gone, setGone] = useState(false);

  const load = useCallback(async () => {
    try {
      const found = await getWeekPlan(weekStart);
      if (found) setPlan(found);
      else setGone(true);
    } catch {
      // The reply above still stands on its own; a card that cannot load is not worth an error.
      setGone(true);
    }
  }, [weekStart]);

  useEffect(() => {
    load();
  }, [load]);

  async function book() {
    setBusy(true);
    setError("");
    try {
      const result = await acceptWeekPlan(weekStart);
      setBooked(result.blocksBooked);
      onBooked();
    } catch (e) {
      setError(errorMessage(e, "could not book those blocks"));
    } finally {
      setBusy(false);
    }
  }

  if (gone || !plan) return null;

  return (
    <div className="proposal">
      <div className="proposal-head">
        <span className="lbl">drafted · week of {dayLabel(plan.weekStart)}</span>
        <span className="proposal-sum">
          {plan.blocks.length} block{plan.blocks.length === 1 ? "" : "s"} · {hours(plan)}h
        </span>
      </div>

      <div className="wp-blocks">
        {plan.blocks.map((b, i) => (
          <div className="wp-block" key={i}>
            <span className="wp-when">
              {dayLabel(b.date)} · {b.startTime}–{b.endTime}
            </span>
            <span className="wp-title">{b.title}</span>
          </div>
        ))}
      </div>

      {booked > 0 ? (
        <p className="proposal-done">
          {booked} booked ✓ — they are on the calendar now, and editable there like anything else.
        </p>
      ) : (
        <div className="proposal-actions">
          {/* Saying what is true rather than what is reassuring: nothing has happened yet. */}
          <span className="muted small">nothing booked yet</span>
          <button type="button" className="primary" disabled={busy} onClick={book}>
            {busy ? "booking…" : `Book ${plan.blocks.length}`}
          </button>
        </div>
      )}

      {error && <div className="error">{error}</div>}
    </div>
  );
}
