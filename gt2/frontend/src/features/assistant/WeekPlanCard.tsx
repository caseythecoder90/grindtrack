import { useCallback, useEffect, useState } from "react";
import { errorMessage } from "../../lib/api";
import {
  acceptWeekPlan,
  getAssistantStatus,
  getWeekPlan,
  proposeWeekPlan,
  type WeekPlan,
} from "./assistantApi";

interface Props {
  /** The Monday of the week being planned. */
  weekStart: string;
  /** Booked blocks are calendar events, so the calendar has to re-read itself. */
  onBooked: () => void;
}

const DOW = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

/** "Mon 15" — enough to read the shape of a week without a date parser in your head. */
function dayLabel(date: string): string {
  const d = new Date(date + "T00:00:00");
  return `${DOW[d.getDay()]} ${d.getDate()}`;
}

/**
 * The assistant's proposed study week, above the calendar.
 *
 * <p>Two clicks, and they are different in kind: proposing spends money and books nothing,
 * accepting books and spends nothing. The card says which is which, because a button that silently
 * writes five events to a calendar is the one thing this feature must never be.
 */
export default function WeekPlanCard({ weekStart, onBooked }: Props) {
  const [configured, setConfigured] = useState<boolean | null>(null);
  const [plan, setPlan] = useState<WeekPlan | null>(null);
  const [busy, setBusy] = useState<"" | "proposing" | "booking">("");
  const [booked, setBooked] = useState(0);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setBooked(0);
    setPlan(await getWeekPlan(weekStart).catch(() => null));
  }, [weekStart]);

  useEffect(() => {
    getAssistantStatus()
      .then((s) => setConfigured(s.configured))
      .catch(() => setConfigured(false));
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  async function propose() {
    setBusy("proposing");
    setError("");
    try {
      setPlan(await proposeWeekPlan(weekStart));
      setBooked(0);
    } catch (e) {
      setError(errorMessage(e, "could not plan that week"));
    } finally {
      setBusy("");
    }
  }

  async function accept() {
    setBusy("booking");
    setError("");
    try {
      const result = await acceptWeekPlan(weekStart);
      setBooked(result.blocksBooked);
      onBooked();
    } catch (e) {
      setError(errorMessage(e, "could not book those blocks"));
    } finally {
      setBusy("");
    }
  }

  if (!configured) return null;

  return (
    <div className="panel weekplan">
      <div className="panelhead">
        <h2>plan this week</h2>
        <button type="button" onClick={propose} disabled={busy !== ""}>
          {busy === "proposing"
            ? "planning — about half a minute…"
            : plan
              ? "re-plan"
              : "plan it for me"}
        </button>
      </div>

      {!plan && !error && (
        <p className="refpara">
          The assistant reads your plan, what you logged recently and what is already booked, then
          proposes morning blocks against real plan items. Nothing reaches the calendar until you
          say so.
        </p>
      )}

      {plan && (
        <>
          <p className="wp-rationale">{plan.rationale}</p>
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
          <div className="ai-draft-cost">
            {plan.model} · {plan.inputTokens} in / {plan.outputTokens} out · proposed{" "}
            {plan.generatedAt.slice(0, 16).replace("T", " ")}
          </div>
          <div className="actions">
            <button type="button" className="primary" onClick={accept} disabled={busy !== ""}>
              {busy === "booking"
                ? "booking…"
                : `Book ${plan.blocks.length} block${plan.blocks.length === 1 ? "" : "s"}`}
            </button>
            {booked > 0 && (
              <span className="toast show">
                {booked} booked ✓ — edit or delete any of them below
              </span>
            )}
          </div>
        </>
      )}

      {error && <div className="error">{error}</div>}
    </div>
  );
}
