import { useCallback, useEffect, useState } from "react";
import { errorMessage } from "../../lib/api";
import {
  createBudgetExtra,
  createBudgetLine,
  deleteBudgetExtra,
  deleteBudgetLine,
  getBudgetMonth,
  setExpectedIncome,
} from "./financeApi";
import type { BudgetCategoryLine, BudgetMonth, BudgetPace } from "../../lib/types";
import { categoryOptions } from "./categories";
import { money, moneyWhole } from "./money";

/** Shifts a yyyy-MM key by a number of months, without going near Date parsing. */
function shiftMonth(key: string, by: number): string {
  const [y, m] = key.split("-").map(Number);
  const index = y * 12 + (m - 1) + by;
  return `${Math.floor(index / 12)}-${String((index % 12) + 1).padStart(2, "0")}`;
}

function thisMonth(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
}

const PACE_LABEL: Record<BudgetPace, string> = {
  UNDER: "under pace",
  ON_TRACK: "on track",
  AHEAD_OF_PACE: "ahead of pace",
  EXCEEDED: "over",
  WITHIN: "within",
};

/**
 * The monthly budget.
 *
 * <p>Built around one question — what is left — and one distinction: the recurring plan versus the
 * things that only happen this month. A vacation has to reduce what is left in July without
 * convincing August that it owes for a holiday.
 *
 * <p>Mid-month, a limit on its own is not enough. Being at 60% of groceries means something very
 * different on the 5th than the 25th, so every category is measured against how far through the
 * month we actually are.
 */
export default function BudgetPanel({ onChange }: { onChange: () => void }) {
  const [month, setMonth] = useState(thisMonth());
  const [view, setView] = useState<BudgetMonth | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const [addingLine, setAddingLine] = useState(false);
  const [lineCategory, setLineCategory] = useState("");
  const [lineAmount, setLineAmount] = useState("");

  const [addingExtra, setAddingExtra] = useState(false);
  const [extraLabel, setExtraLabel] = useState("");
  const [extraAmount, setExtraAmount] = useState("");
  const [extraCategory, setExtraCategory] = useState("");

  const [editingIncome, setEditingIncome] = useState(false);
  const [incomeDraft, setIncomeDraft] = useState("");

  const load = useCallback(async (key: string) => {
    setError("");
    try {
      setView(await getBudgetMonth(key));
    } catch (e) {
      setError(errorMessage(e, "could not load your budget"));
    }
  }, []);

  useEffect(() => {
    load(month);
  }, [load, month]);

  async function addLine() {
    if (!lineCategory.trim() || !lineAmount) return;
    setBusy(true);
    setError("");
    try {
      await createBudgetLine({
        category: lineCategory,
        monthlyAmount: Math.abs(Number(lineAmount)),
        note: "",
        sortOrder: view?.categories.length ?? 0,
      });
      setLineCategory("");
      setLineAmount("");
      setAddingLine(false);
      load(month);
      onChange();
    } catch (e) {
      setError(errorMessage(e, "could not save that budget line"));
    } finally {
      setBusy(false);
    }
  }

  async function removeLine(line: BudgetCategoryLine) {
    setError("");
    try {
      await deleteBudgetLine(line.budgetId);
      load(month);
      onChange();
    } catch (e) {
      setError(errorMessage(e, "could not remove that line"));
    }
  }

  async function addExtra() {
    if (!extraLabel.trim() || !extraAmount) return;
    setBusy(true);
    setError("");
    try {
      await createBudgetExtra({
        month,
        label: extraLabel,
        // Typed as a plain number and treated as a cost, which is what an "extra" nearly always
        // is. A refund is entered with a minus, and the form says so.
        amount: -Math.abs(Number(extraAmount)),
        category: extraCategory || null,
        note: "",
      });
      setExtraLabel("");
      setExtraAmount("");
      setExtraCategory("");
      setAddingExtra(false);
      load(month);
    } catch (e) {
      setError(errorMessage(e, "could not add that item"));
    } finally {
      setBusy(false);
    }
  }

  async function removeExtra(id: number) {
    setError("");
    try {
      await deleteBudgetExtra(id);
      load(month);
    } catch (e) {
      setError(errorMessage(e, "could not remove that item"));
    }
  }

  async function saveIncome() {
    setBusy(true);
    setError("");
    try {
      await setExpectedIncome(incomeDraft.trim() === "" ? null : Number(incomeDraft));
      setEditingIncome(false);
      load(month);
    } catch (e) {
      setError(errorMessage(e, "could not save that"));
    } finally {
      setBusy(false);
    }
  }

  if (error && !view) {
    return (
      <section>
        <div className="section-head">
          <h3>budget</h3>
        </div>
        <p className="error">{error}</p>
      </section>
    );
  }
  if (!view) return null;

  const options = categoryOptions([
    ...view.categories.map((c) => c.category),
    ...view.unbudgeted.map((u) => u.category),
  ]);
  const overspent = view.leftToSpend < 0;
  const paceRatio = view.planned > 0 ? view.spent / view.planned : 0;
  const timeRatio = view.dayOfMonth / view.daysInMonth;

  return (
    <section className="budget">
      <div className="section-head">
        <h3>budget · {view.monthLabel.toLowerCase()}</h3>
        <div>
          <button type="button" onClick={() => setMonth(shiftMonth(month, -1))}>
            ←
          </button>
          <button
            type="button"
            disabled={month === thisMonth()}
            onClick={() => setMonth(thisMonth())}
          >
            this month
          </button>
          <button
            type="button"
            disabled={month >= thisMonth()}
            onClick={() => setMonth(shiftMonth(month, 1))}
          >
            →
          </button>
        </div>
      </div>

      {error && <p className="error">{error}</p>}

      {view.categories.length === 0 ? (
        <p className="muted small">
          No budget lines yet. The fastest way to start is the recurring charges below — they are
          the bills you already have, with the amounts you already pay.
        </p>
      ) : (
        <>
          <div className="finance-top">
            <div className="stat">
              <span className="k">left this month</span>
              <span className={`v${overspent ? " negative" : ""}`}>
                {moneyWhole(view.leftToSpend)}
              </span>
            </div>
            <div className="stat">
              <span className="k">planned</span>
              <span className="v">{moneyWhole(view.planned)}</span>
            </div>
            <div className="stat">
              <span className="k">spent</span>
              <span className="v">{moneyWhole(view.spent)}</span>
            </div>
            <div className="stat">
              <span className="k">projected net</span>
              <span className={`v${view.projectedNet < 0 ? " negative" : " positive"}`}>
                {moneyWhole(view.projectedNet)}
              </span>
            </div>
          </div>

          {view.currentMonth && (
            <p className="muted small">
              Day {view.dayOfMonth} of {view.daysInMonth}. An even month would be at{" "}
              {moneyWhole(view.expectedSpentByNow)} by now — you are at {moneyWhole(view.spent)},{" "}
              {paceRatio > timeRatio ? "ahead of" : "behind"} that pace.
            </p>
          )}

          <table className="txn-table budget-table">
            <tbody>
              {view.categories.map((c) => (
                <tr key={c.budgetId}>
                  <td>
                    {c.category}
                    {c.extra > 0 && (
                      <span className="tag" title={c.extraLabels.join(", ")}>
                        +{money(c.extra)} this month
                      </span>
                    )}
                  </td>
                  <td className="bar-cell">
                    <span className="meter">
                      <i
                        className={c.pace === "EXCEEDED" ? "over" : "money"}
                        style={{ width: `${Math.min(c.percentUsed, 100)}%` }}
                      />
                      {view.currentMonth && c.pace !== "EXCEEDED" && (
                        <b className="pace-mark" style={{ left: `${timeRatio * 100}%` }} />
                      )}
                    </span>
                  </td>
                  <td className="num">
                    {money(c.spent)} <span className="muted">/ {money(c.planned)}</span>
                  </td>
                  <td className={`num ${c.left < 0 ? "negative" : ""}`}>{money(c.left)}</td>
                  <td className="num">
                    <span className={`pace pace-${c.pace.toLowerCase()}`}>{PACE_LABEL[c.pace]}</span>
                  </td>
                  <td className="num">
                    <button type="button" className="ghost" onClick={() => removeLine(c)}>
                      ×
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}

      <div className="section-head">
        <h4>just this month</h4>
        <button type="button" onClick={() => setAddingExtra((v) => !v)}>
          {addingExtra ? "cancel" : "+ one-off"}
        </button>
      </div>
      <p className="muted small">
        A vacation, a car repair, a bill that only came once. These reduce what is left this month
        and leave every other month alone. Tag a category and it raises that category instead of
        floating outside all of them.
      </p>

      {addingExtra && (
        <div className="account-form">
          <input
            placeholder="what is it (e.g. flights to Denver)"
            value={extraLabel}
            onChange={(e) => setExtraLabel(e.target.value)}
          />
          <input
            type="number"
            step="10"
            placeholder="cost"
            value={extraAmount}
            onChange={(e) => setExtraAmount(e.target.value)}
          />
          <input
            list="budget-category-options"
            placeholder="category (optional)"
            value={extraCategory}
            onChange={(e) => setExtraCategory(e.target.value)}
          />
          <button type="button" disabled={busy} onClick={addExtra}>
            add
          </button>
        </div>
      )}

      {view.extras.length > 0 && (
        <table className="txn-table">
          <tbody>
            {view.extras.map((x) => (
              <tr key={x.id}>
                <td>
                  {x.label}
                  {x.category && <span className="tag cat"> {x.category}</span>}
                </td>
                <td className={`num ${x.amount < 0 ? "negative" : "positive"}`}>
                  {money(Math.abs(x.amount))}
                  <span className="muted small"> {x.amount < 0 ? "cost" : "in"}</span>
                </td>
                <td className="num">
                  <button type="button" className="ghost" onClick={() => removeExtra(x.id)}>
                    ×
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {view.unbudgeted.length > 0 && (
        <>
          <div className="section-head">
            <h4>spent outside the budget</h4>
          </div>
          <p className="muted small">
            Real money that no budget line covers. Either give these a line or accept them — but
            they are the difference between the plan and what happened.
          </p>
          <table className="txn-table">
            <tbody>
              {view.unbudgeted.slice(0, 8).map((u) => (
                <tr key={u.category ?? "uncategorized"}>
                  <td className={u.category ? "" : "muted"}>
                    {u.category ?? "uncategorized"}
                    <span className="muted small"> · {u.count}</span>
                  </td>
                  <td className="num">{money(u.spent)}</td>
                  <td className="num">
                    <button
                      type="button"
                      className="ghost"
                      onClick={() => {
                        setLineCategory(u.category ?? "");
                        setLineAmount(String(Math.ceil(u.spent)));
                        setAddingLine(true);
                      }}
                    >
                      budget it
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}

      <div className="section-head">
        <h4>the recurring plan</h4>
        <button type="button" onClick={() => setAddingLine((v) => !v)}>
          {addingLine ? "cancel" : "+ line"}
        </button>
      </div>

      {addingLine && (
        <div className="account-form">
          <input
            list="budget-category-options"
            placeholder="category"
            value={lineCategory}
            onChange={(e) => setLineCategory(e.target.value)}
          />
          <input
            type="number"
            step="25"
            placeholder="per month"
            value={lineAmount}
            onChange={(e) => setLineAmount(e.target.value)}
          />
          <button type="button" disabled={busy} onClick={addLine}>
            add
          </button>
        </div>
      )}

      <p className="muted small income-line">
        Expected income{" "}
        {editingIncome ? (
          <>
            <input
              type="number"
              step="100"
              placeholder="leave blank to estimate"
              value={incomeDraft}
              onChange={(e) => setIncomeDraft(e.target.value)}
            />
            <button type="button" className="ghost" disabled={busy} onClick={saveIncome}>
              save
            </button>
            <button type="button" className="ghost" onClick={() => setEditingIncome(false)}>
              cancel
            </button>
          </>
        ) : (
          <>
            <b>{moneyWhole(view.expectedIncome)}</b>
            {view.incomeIsEstimated
              ? " — averaged from the last three months of deposits"
              : " — set by you"}
            .{" "}
            <button
              type="button"
              className="ghost"
              onClick={() => {
                setIncomeDraft(view.incomeIsEstimated ? "" : String(view.expectedIncome));
                setEditingIncome(true);
              }}
            >
              change
            </button>
            {view.incomeSoFar > 0 && (
              <span className="muted"> {moneyWhole(view.incomeSoFar)} has arrived so far.</span>
            )}
          </>
        )}
      </p>

      <datalist id="budget-category-options">
        {options.map((c) => (
          <option key={c} value={c} />
        ))}
      </datalist>
    </section>
  );
}
