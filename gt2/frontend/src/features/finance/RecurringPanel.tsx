import { useCallback, useEffect, useState } from "react";
import { errorMessage } from "../../lib/api";
import { createBudgetLine, getRecurring } from "./financeApi";
import type { Recurring, RecurringReport } from "../../lib/types";
import { money, moneyWhole } from "./money";

const CADENCE: Record<Recurring["cadence"], string> = {
  WEEKLY: "weekly",
  FORTNIGHTLY: "every 2 weeks",
  MONTHLY: "monthly",
  QUARTERLY: "quarterly",
  YEARLY: "yearly",
};

/**
 * The charges that come back on a rhythm.
 *
 * <p>Two jobs. It is the fastest way to build a budget that is right the first time — these are
 * bills you already have, at amounts you already pay, so there is no guessing. And it is the
 * subscription audit nobody gets round to doing: something charging you monthly that you do not
 * recognize is exactly what this is for.
 */
export default function RecurringPanel({ onChange }: { onChange: () => void }) {
  const [report, setReport] = useState<RecurringReport | null>(null);
  const [error, setError] = useState("");
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState<string | null>(null);
  const [showLapsed, setShowLapsed] = useState(false);

  const load = useCallback(async () => {
    try {
      setReport(await getRecurring());
    } catch (e) {
      setError(errorMessage(e, "could not look for recurring charges"));
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  /** Rounds up to the nearest 5 so a budget line is a round number, not a past charge. */
  function suggested(item: Recurring): number {
    const base = item.variable ? item.highest : item.monthlyEquivalent;
    return Math.ceil(base / 5) * 5;
  }

  async function budgetIt(item: Recurring) {
    const category = item.category;
    if (!category) {
      setError(
        `"${item.merchant}" has no category yet — file one of its transactions in the review list first, then it can become a budget line.`,
      );
      return;
    }
    setBusy(item.merchant);
    setError("");
    setNote("");
    try {
      await createBudgetLine({
        category,
        monthlyAmount: suggested(item),
        note: `From ${item.merchant}, ${CADENCE[item.cadence]}`,
        sortOrder: 0,
      });
      setNote(`${category} budgeted at ${money(suggested(item))} a month.`);
      onChange();
    } catch (e) {
      setError(errorMessage(e, "could not create that budget line"));
    } finally {
      setBusy(null);
    }
  }

  if (!report || report.items.length === 0) return null;

  const live = report.items.filter((i) => !i.lapsed);
  const lapsed = report.items.filter((i) => i.lapsed);

  return (
    <section className="recurring">
      <div className="section-head">
        <h3>recurring charges</h3>
        {lapsed.length > 0 && (
          <button type="button" onClick={() => setShowLapsed((v) => !v)}>
            {showLapsed ? "hide" : `${lapsed.length} lapsed`}
          </button>
        )}
      </div>

      <p className="muted small">
        <b>{moneyWhole(report.monthlyCommitment)}</b> a month across {report.liveCount} recurring
        charges — the part of your spending that happens whether you think about it or not.
        Quarterly and yearly bills are shown at their monthly equivalent so they add up together.
      </p>

      {error && <p className="error">{error}</p>}
      {note && <p className="muted small">{note}</p>}

      <table className="txn-table recurring-table">
        <tbody>
          {(showLapsed ? lapsed : live).map((item) => (
            <tr key={item.merchant} className={item.lapsed ? "inactive" : ""}>
              <td>
                {item.merchant}
                {item.category && <span className="tag cat"> {item.category}</span>}
                <div className="muted small">
                  {CADENCE[item.cadence]} · {item.occurrences}×
                  {item.variable && (
                    <>
                      {" "}
                      · varies {money(item.lowest)}–{money(item.highest)}
                    </>
                  )}
                  {item.lapsed
                    ? ` · nothing since ${item.lastSeen}`
                    : ` · next around ${item.nextExpected}`}
                </div>
              </td>
              <td className="num">
                {money(item.typicalAmount)}
                {item.cadence !== "MONTHLY" && (
                  <div className="muted small">{money(item.monthlyEquivalent)}/mo</div>
                )}
              </td>
              <td className="num">
                {!item.lapsed && (
                  <button
                    type="button"
                    className="ghost"
                    disabled={busy === item.merchant}
                    title={
                      item.category
                        ? `Create a ${money(suggested(item))} budget line for ${item.category}`
                        : "Needs a category first"
                    }
                    onClick={() => budgetIt(item)}
                  >
                    budget it
                  </button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      {showLapsed && (
        <p className="muted small">
          These stopped arriving. Either you cancelled them, or they are about to reappear — worth
          knowing which before a budget assumes they are gone.
        </p>
      )}
    </section>
  );
}
