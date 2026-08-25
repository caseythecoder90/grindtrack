import { useState } from "react";
import { api, jsonInit } from "../../lib/api";
import {
  ACCOUNT_TYPE_LABELS,
  INSTITUTION_LABELS,
  type AccountType,
  type FinanceAccount,
  type Institution,
} from "../../lib/types";
import { money } from "./money";

const INSTITUTIONS = Object.keys(INSTITUTION_LABELS) as Institution[];
const ACCOUNT_TYPES = Object.keys(ACCOUNT_TYPE_LABELS) as AccountType[];

/**
 * The seven real accounts, with their last-known balances.
 *
 * <p>Balances are typed in by hand for now; statement imports will maintain them from
 * phase 2. `balanceAsOf` is shown deliberately so a stale figure looks stale rather than
 * quietly wrong.
 */
export default function AccountsPanel({
  accounts,
  onChange,
}: {
  accounts: FinanceAccount[];
  onChange: () => void;
}) {
  const [adding, setAdding] = useState(false);
  const [name, setName] = useState("");
  const [institution, setInstitution] = useState<Institution>("CAPITAL_ONE");
  const [accountType, setAccountType] = useState<AccountType>("CHECKING");
  const [last4, setLast4] = useState("");
  const [counts, setCounts] = useState(false);
  const [editing, setEditing] = useState<number | null>(null);
  const [balance, setBalance] = useState("");
  const [error, setError] = useState("");

  async function add() {
    if (!name.trim()) return;
    setError("");
    try {
      await api(
        "/api/finance/accounts",
        jsonInit("POST", {
          name,
          institution,
          accountType,
          last4: last4 || null,
          countsTowardSavings: counts,
          sortOrder: accounts.length,
        }),
      );
      setName("");
      setLast4("");
      setCounts(false);
      setAdding(false);
      onChange();
    } catch (e) {
      setError(e instanceof Error ? e.message : "could not add that account");
    }
  }

  async function saveBalance(account: FinanceAccount) {
    setError("");
    try {
      await api(
        `/api/finance/accounts/${account.id}/balance`,
        jsonInit("PATCH", { balance: Number(balance), asOf: null }),
      );
      setEditing(null);
      setBalance("");
      onChange();
    } catch (e) {
      setError(e instanceof Error ? e.message : "could not save that balance");
    }
  }

  return (
    <section className="accounts">
      <div className="section-head">
        <h3>accounts</h3>
        <button type="button" onClick={() => setAdding((v) => !v)}>
          {adding ? "cancel" : "+ account"}
        </button>
      </div>

      {error && <p className="error">{error}</p>}

      {adding && (
        <div className="account-form">
          <input
            placeholder="name (e.g. 360 Checking)"
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
          <select
            value={institution}
            onChange={(e) => setInstitution(e.target.value as Institution)}
          >
            {INSTITUTIONS.map((i) => (
              <option key={i} value={i}>
                {INSTITUTION_LABELS[i]}
              </option>
            ))}
          </select>
          <select
            value={accountType}
            onChange={(e) => setAccountType(e.target.value as AccountType)}
          >
            {ACCOUNT_TYPES.map((t) => (
              <option key={t} value={t}>
                {ACCOUNT_TYPE_LABELS[t]}
              </option>
            ))}
          </select>
          <input
            placeholder="last 4"
            maxLength={4}
            value={last4}
            onChange={(e) => setLast4(e.target.value)}
          />
          <label className="inline">
            <input type="checkbox" checked={counts} onChange={(e) => setCounts(e.target.checked)} />
            counts toward savings
          </label>
          <button type="button" onClick={add}>
            add
          </button>
        </div>
      )}

      {accounts.length === 0 ? (
        <p className="muted">
          No accounts yet. Add the seven real ones — Capital One checking, savings and three
          cards, Chase, Wells Fargo, Bank of America — then tick “counts toward savings” on the
          ones holding the house fund.
        </p>
      ) : (
        <table className="account-table">
          <tbody>
            {accounts.map((a) => (
              <tr key={a.id} className={a.countsTowardSavings ? "is-savings" : undefined}>
                <td>
                  <strong>{a.name}</strong>
                  {a.last4 && <span className="muted"> ·{a.last4}</span>}
                  <div className="muted small">
                    {INSTITUTION_LABELS[a.institution]} · {ACCOUNT_TYPE_LABELS[a.accountType]}
                    {a.transactionCount > 0 && ` · ${a.transactionCount} txns`}
                  </div>
                </td>
                <td className="num">
                  {editing === a.id ? (
                    <span className="balance-edit">
                      <input
                        type="number"
                        step="0.01"
                        autoFocus
                        value={balance}
                        onChange={(e) => setBalance(e.target.value)}
                        onKeyDown={(e) => e.key === "Enter" && saveBalance(a)}
                      />
                      <button type="button" onClick={() => saveBalance(a)}>
                        save
                      </button>
                    </span>
                  ) : (
                    <button
                      type="button"
                      className="linkish"
                      title="click to update"
                      onClick={() => {
                        setEditing(a.id);
                        setBalance(String(a.currentBalance));
                      }}
                    >
                      <span className={a.currentBalance < 0 ? "negative" : undefined}>
                        {money(a.currentBalance)}
                      </span>
                      <div className="muted small">
                        {a.balanceAsOf ? `as of ${a.balanceAsOf}` : "never updated"}
                      </div>
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
