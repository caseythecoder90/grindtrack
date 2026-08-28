import type { Closeness } from "../../lib/types";
import { daysAgo } from "./kinds";

/**
 * The card this feature was really asked for.
 *
 * <p>Its job is to answer "when did we last…" before you have finished doing the arithmetic
 * yourself, because that arithmetic done in a bad mood is what it is correcting for.
 *
 * <p>Three things and nothing else: the last few times as plain dates, a count against your own
 * trailing average, and a sentence. Deliberately absent — and to stay absent — are a streak, a
 * target, a rate per week, a chart, and any red state at all. The same number reassures or winds
 * you up entirely depending on how it is framed, and the framing is the whole feature.
 */
export default function ClosenessCard({
  closeness,
  discreet,
  onToggleDiscreet,
}: {
  closeness: Closeness;
  discreet: boolean;
  onToggleDiscreet: () => void;
}) {
  const { perspective, recentDates, lastThirtyDays, typicalPerMonth } = closeness;

  if (discreet) {
    return (
      <div className="closeness hidden">
        <span className="muted small">hidden</span>
        <button type="button" className="ghost" onClick={onToggleDiscreet}>
          show
        </button>
      </div>
    );
  }

  return (
    <div className={`closeness tone-${perspective.tone.toLowerCase()}`}>
      <div className="closeness-head">
        <strong>{perspective.headline}</strong>
        <button
          type="button"
          className="ghost"
          title="Hide this on this device"
          onClick={onToggleDiscreet}
        >
          hide
        </button>
      </div>

      <p className="detail">{perspective.detail}</p>

      {recentDates.length > 0 && (
        <p className="recent">
          {recentDates.slice(0, 4).map((d, i) => (
            <span key={d + i}>
              {i > 0 && " · "}
              {daysAgo(daysBetween(d))}
            </span>
          ))}
        </p>
      )}

      {typicalPerMonth !== null && (
        <p className="muted small">
          {lastThirtyDays} in the last 30 days · your usual is about {typicalPerMonth} a month
        </p>
      )}
    </div>
  );
}

/** Whole days between a yyyy-MM-dd and today, computed in local time. */
function daysBetween(iso: string): number {
  const [y, m, d] = iso.split("-").map(Number);
  const then = new Date(y, m - 1, d);
  const now = new Date();
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  return Math.round((today.getTime() - then.getTime()) / 86_400_000);
}
