import { useCallback, useEffect, useState } from "react";
import { errorMessage } from "../../lib/api";
import { addDays, todayISO } from "../../lib/dates";
import { getMonthlyTotals, getSpending } from "./financeApi";
import type { MonthTotal, SpendSummary } from "../../lib/types";
import { money, moneyWhole } from "./money";

type Window = { label: string; days: number };

/**
 * Windows worth comparing, not every window imaginable. A month is the unit bills arrive in, three
 * months smooths the lumpy ones, and a year is what a budget is actually set against.
 */
const WINDOWS: Window[] = [
  { label: "30 days", days: 30 },
  { label: "90 days", days: 90 },
  { label: "12 months", days: 365 },
];

/**
 * Where the money actually went.
 *
 * <p>Transfers and card payments are excluded by the query behind this, which is the whole reason
 * the number can be trusted: counting a $500 purchase and the $500 card payment that settles it
 * would report roughly twice the truth.
 */
export default function SpendingPanel() {
  const [window, setWindow] = useState<Window>(WINDOWS[0]);
  const [data, setData] = useState<SpendSummary | null>(null);
  const [months, setMonths] = useState<MonthTotal[]>([]);
  const [error, setError] = useState("");

  const load = useCallback(async (days: number) => {
    setError("");
    try {
      setData(await getSpending(addDays(todayISO(), -days), todayISO()));
    } catch (e) {
      setError(errorMessage(e, "could not load your spending"));
    }
  }, []);

  useEffect(() => {
    load(window.days);
  }, [load, window]);

  useEffect(() => {
    getMonthlyTotals(6)
      .then(setMonths)
      .catch(() => setMonths([]));
  }, []);

  if (error) {
    return (
      <section>
        <div className="section-head">
          <h3>spending</h3>
        </div>
        <p className="error">{error}</p>
      </section>
    );
  }

  if (!data || data.transactionCount === 0) {
    return null;
  }

  // Spending is stored negative. Flip once, here, at the point of display.
  const spend = Math.abs(data.totalSpend);
  const largest = Math.max(...data.byCategory.map((c) => Math.abs(c.total)), 1);
  const uncategorized = data.byCategory.find((c) => !c.label);

  return (
    <section className="spending">
      <div className="section-head">
        <h3>spending</h3>
        <div className="seg">
          {WINDOWS.map((w) => (
            <button
              key={w.days}
              type="button"
              className="money"
              aria-pressed={w.days === window.days}
              onClick={() => setWindow(w)}
            >
              {w.label}
            </button>
          ))}
        </div>
      </div>

      <div className="finance-top">
        <div className="stat">
          <span className="k">spent</span>
          <span className="v negative">{moneyWhole(spend)}</span>
        </div>
        <div className="stat">
          <span className="k">income</span>
          <span className="v positive">{moneyWhole(data.totalIncome)}</span>
        </div>
        <div className="stat">
          <span className="k">net</span>
          <span className={`v${data.net < 0 ? " negative" : " positive"}`}>
            {moneyWhole(data.net)}
          </span>
        </div>
        <div className="stat">
          <span className="k">per month</span>
          <span className="v">{moneyWhole((spend / window.days) * 30.4)}</span>
        </div>
      </div>

      {months.length > 1 && (
        <>
          <div className="section-head">
            <h4>month by month</h4>
          </div>
          <div className="months">
            {months.map((m) => {
              const peak = Math.max(...months.map((x) => x.spend), 1);
              return (
                <div key={m.month} className={`month${m.partial ? " partial" : ""}`}>
                  <span className="bar" style={{ height: `${(m.spend / peak) * 100}%` }} />
                  <span className="amt">{moneyWhole(m.spend)}</span>
                  <span className="lbl">{m.month.slice(5)}</span>
                </div>
              );
            })}
          </div>
          <p className="muted small">
            A single window tells you what you spent; a run of months tells you whether that is
            normal. The last bar is the month in progress, so it is short by design.
          </p>
        </>
      )}

      {uncategorized && Math.abs(uncategorized.total) > spend * 0.15 && (
        <p className="warn">
          {moneyWhole(Math.abs(uncategorized.total))} of this — {" "}
          {Math.round((Math.abs(uncategorized.total) / spend) * 100)}% — has no category yet. The
          breakdown below is only as good as that number is small.
        </p>
      )}

      <table className="txn-table cat-table">
        <tbody>
          {data.byCategory.map((c) => (
            <tr key={c.label ?? "uncategorized"}>
              <td className={c.label ? "" : "muted"}>
                {c.label ?? "uncategorized"}
                <span className="muted small"> · {c.count}</span>
              </td>
              <td className="bar-cell">
                <span className="meter">
                  <i
                    className="money"
                    style={{ width: `${(Math.abs(c.total) / largest) * 100}%` }}
                  />
                </span>
              </td>
              <td className="num">{money(Math.abs(c.total))}</td>
              <td className="num muted small">
                {Math.round((Math.abs(c.total) / spend) * 100)}%
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      {data.topMerchants.length > 0 && (
        <>
          <div className="section-head">
            <h3>biggest merchants</h3>
          </div>
          <table className="txn-table">
            <tbody>
              {data.topMerchants.slice(0, 10).map((m) => (
                <tr key={m.label ?? "unknown"}>
                  <td>
                    {m.label ?? <span className="muted">unknown</span>}
                    <span className="muted small"> · {m.count}</span>
                  </td>
                  <td className="num">{money(Math.abs(m.total))}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}
    </section>
  );
}
