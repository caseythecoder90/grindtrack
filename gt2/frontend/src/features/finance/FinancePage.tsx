import { useCallback, useEffect, useState } from "react";
import { api, jsonInit } from "../../lib/api";
import { todayISO } from "../../lib/dates";
import type { FinanceSummary, FinanceTransaction, TxnType } from "../../lib/types";
import AccountsPanel from "./AccountsPanel";
import ImportPanel from "./ImportPanel";
import SavingsGoalCard from "./SavingsGoalCard";
import { money, moneyWhole, signed } from "./money";

const TXN_TYPES: TxnType[] = ["SPEND", "INCOME", "TRANSFER", "PAYMENT"];

/**
 * The finance tab.
 *
 * <p>Phase 1: accounts, balances, hand-entered transactions and the savings goal. Statement
 * importing, category rules and the day/week/month/year rollups land in later phases — but the
 * data model underneath already carries transaction type, dedupe fingerprints and sticky
 * categorization, so none of that needs a migration to arrive.
 */
export default function FinancePage() {
  const [summary, setSummary] = useState<FinanceSummary | null>(null);
  const [error, setError] = useState("");

  // goal form
  const [goalName, setGoalName] = useState("");
  const [goalTarget, setGoalTarget] = useState("");
  const [goalNote, setGoalNote] = useState("");
  const [addingGoal, setAddingGoal] = useState(false);

  // transaction form
  const [accountId, setAccountId] = useState<number | "">("");
  const [date, setDate] = useState(todayISO());
  const [amount, setAmount] = useState("");
  const [description, setDescription] = useState("");
  const [txnType, setTxnType] = useState<TxnType | "">("");
  const [recent, setRecent] = useState<FinanceTransaction[]>([]);

  const load = useCallback(async () => {
    setError("");
    try {
      const data = await api<FinanceSummary>("/api/finance/summary");
      setSummary(data);
      if (data.accounts.length > 0) {
        setAccountId((current) => (current === "" ? data.accounts[0].id : current));
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : "could not load your finances");
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const loadRecent = useCallback(async (id: number) => {
    try {
      setRecent(await api<FinanceTransaction[]>(`/api/finance/accounts/${id}/transactions`));
    } catch {
      setRecent([]);
    }
  }, []);

  useEffect(() => {
    if (typeof accountId === "number") loadRecent(accountId);
  }, [accountId, loadRecent]);

  async function addGoal() {
    if (!goalName.trim() || !goalTarget) return;
    setError("");
    try {
      await api(
        "/api/finance/goals",
        jsonInit("POST", {
          name: goalName,
          targetAmount: Number(goalTarget),
          targetDate: null,
          note: goalNote,
          sortOrder: summary?.goals.length ?? 0,
        }),
      );
      setGoalName("");
      setGoalTarget("");
      setGoalNote("");
      setAddingGoal(false);
      load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "could not add that goal");
    }
  }

  async function addTransaction() {
    if (typeof accountId !== "number" || !amount || !description.trim()) return;
    setError("");
    try {
      await api(
        "/api/finance/transactions",
        jsonInit("POST", {
          accountId,
          postedDate: date,
          transactionDate: null,
          amount: Number(amount),
          description,
          // Blank lets the server classify it — the same logic the importers will use.
          txnType: txnType || null,
          notes: "",
        }),
      );
      setAmount("");
      setDescription("");
      setTxnType("");
      loadRecent(accountId);
      load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "could not add that transaction");
    }
  }

  if (!summary) {
    return <div className="finance">{error ? <p className="error">{error}</p> : <p className="muted">loading…</p>}</div>;
  }

  return (
    <div className="finance">
      {error && <p className="error">{error}</p>}

      <div className="finance-top">
        <div className="stat">
          <span className="k">savings</span>
          <span className="v">{moneyWhole(summary.savingsBalance)}</span>
        </div>
        <div className="stat">
          <span className="k">net worth</span>
          <span className={`v${summary.netWorth < 0 ? " negative" : ""}`}>
            {moneyWhole(summary.netWorth)}
          </span>
        </div>
        {summary.uncategorizedCount > 0 && (
          <div className="stat">
            <span className="k">to review</span>
            <span className="v">{summary.uncategorizedCount}</span>
          </div>
        )}
      </div>

      {summary.goals.map((g) => (
        <SavingsGoalCard key={g.id} goal={g} />
      ))}

      <section>
        <div className="section-head">
          <h3>goals</h3>
          <button type="button" onClick={() => setAddingGoal((v) => !v)}>
            {addingGoal ? "cancel" : "+ goal"}
          </button>
        </div>
        {summary.goals.length === 0 && !addingGoal && (
          <p className="muted">
            No goal yet. The house fund is $230k — $100k down on a $500k house, ~$15k closing,
            ~$12k move-in, $100k retained.
          </p>
        )}
        {addingGoal && (
          <div className="account-form">
            <input
              placeholder="name (e.g. House fund)"
              value={goalName}
              onChange={(e) => setGoalName(e.target.value)}
            />
            <input
              type="number"
              step="1000"
              placeholder="target"
              value={goalTarget}
              onChange={(e) => setGoalTarget(e.target.value)}
            />
            <input
              placeholder="why this number"
              value={goalNote}
              onChange={(e) => setGoalNote(e.target.value)}
            />
            <button type="button" onClick={addGoal}>
              add
            </button>
          </div>
        )}
      </section>

      <AccountsPanel accounts={summary.accounts} onChange={load} />

      {summary.accounts.length > 0 && (
        <ImportPanel
          accounts={summary.accounts}
          onImported={() => {
            load();
            if (typeof accountId === "number") loadRecent(accountId);
          }}
        />
      )}

      {summary.accounts.length > 0 && (
        <section>
          <div className="section-head">
            <h3>add a transaction</h3>
          </div>
          <div className="account-form">
            <select
              value={accountId}
              onChange={(e) => setAccountId(Number(e.target.value))}
            >
              {summary.accounts.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.name}
                </option>
              ))}
            </select>
            <input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
            <input
              type="number"
              step="0.01"
              placeholder="amount (negative = out)"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
            />
            <input
              placeholder="description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
            <select value={txnType} onChange={(e) => setTxnType(e.target.value as TxnType | "")}>
              <option value="">auto-classify</option>
              {TXN_TYPES.map((t) => (
                <option key={t} value={t}>
                  {t.toLowerCase()}
                </option>
              ))}
            </select>
            <button type="button" onClick={addTransaction}>
              add
            </button>
          </div>

          {recent.length > 0 && (
            <table className="txn-table">
              <tbody>
                {recent.slice(0, 25).map((t) => (
                  <tr key={t.id}>
                    <td className="muted small">{t.postedDate.slice(5)}</td>
                    <td>
                      {t.merchant ?? t.description}
                      {t.txnType !== "SPEND" && (
                        <span className="tag"> {t.txnType.toLowerCase()}</span>
                      )}
                      {t.category && <span className="tag cat"> {t.category}</span>}
                    </td>
                    <td className={`num ${t.amount < 0 ? "negative" : "positive"}`}>
                      {signed(t.amount)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          {recent.length === 0 && (
            <p className="muted">
              Nothing on this account yet. Transfers and card payments you add here are excluded
              from spending automatically — {money(0)} of double counting by design.
            </p>
          )}
        </section>
      )}
    </div>
  );
}
