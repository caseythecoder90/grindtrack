import { useState } from "react";
import type { SavingsGoal } from "../../lib/types";
import { money, moneyWhole, monthsFromNow, monthsToGoal } from "./money";

/** Default monthly contribution used for the projection until imports can measure the real one. */
const DEFAULT_PER_MONTH = 2000;

const RATE_KEY = "gt-finance-per-month";

function storedRate(): number {
  const raw = Number(localStorage.getItem(RATE_KEY));
  return Number.isFinite(raw) && raw > 0 ? raw : DEFAULT_PER_MONTH;
}

/**
 * The house fund, and the whole reason the finance tab exists.
 *
 * <p>Shows the number, the gap, and — the part that actually matters — when you get there
 * at your current rate. A progress bar alone tells you where you are; the projected date
 * tells you whether the plan is working.
 */
export default function SavingsGoalCard({ goal }: { goal: SavingsGoal }) {
  const [perMonth, setPerMonth] = useState<number>(storedRate);

  const pct = Math.min(100, Math.max(0, goal.progressPercent));
  const months = monthsToGoal(goal.remaining, perMonth);
  const reached = goal.remaining <= 0;

  function changeRate(next: number) {
    setPerMonth(next);
    if (next > 0) localStorage.setItem(RATE_KEY, String(next));
  }

  return (
    <section className="goal-card">
      <div className="goal-head">
        <h3>{goal.name}</h3>
        {goal.targetDate && <span className="muted">target {goal.targetDate}</span>}
      </div>

      <div className="goal-numbers">
        <strong>{moneyWhole(goal.currentAmount)}</strong>
        <span className="muted"> of {moneyWhole(goal.targetAmount)}</span>
      </div>

      {/* Reuses the app's split-meter device so the money tab reads as the same product. */}
      <div
        className="meter large"
        role="img"
        aria-label={`${money(goal.currentAmount)} of ${money(goal.targetAmount)}`}
      >
        {pct > 0 && <i className="money" style={{ width: `${pct}%` }} />}
      </div>

      <div className="goal-meta">
        <span>{pct.toFixed(1)}%</span>
        {reached ? (
          <span className="goal-reached">reached</span>
        ) : (
          <span className="muted">{moneyWhole(goal.remaining)} to go</span>
        )}
      </div>

      {!reached && (
        <div className="goal-projection">
          <label>
            saving{" "}
            <input
              type="number"
              min={0}
              step={100}
              value={perMonth}
              onChange={(e) => changeRate(Number(e.target.value))}
            />{" "}
            /mo
          </label>
          {months === null ? (
            <span className="muted">set a monthly amount to project a date</span>
          ) : (
            <span>
              → <strong>{monthsFromNow(months)}</strong>{" "}
              <span className="muted">
                ({months} {months === 1 ? "month" : "months"})
              </span>
            </span>
          )}
        </div>
      )}

      {goal.note && <p className="goal-note muted">{goal.note}</p>}

      {!reached && months !== null && (
        <p className="goal-note muted">
          Bonuses and interest are not in this projection — it is contributions only, so the
          real date lands earlier. {money(perMonth * 12)}/yr at this rate.
        </p>
      )}
    </section>
  );
}
