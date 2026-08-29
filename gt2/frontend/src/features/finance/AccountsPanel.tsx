import { Fragment, useState } from "react";
import { errorMessage } from "../../lib/api";
import { createAccount, deleteAccount, recordBalance, updateAccount } from "./financeApi";
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

/** The shared shape of the add form and the edit form — same fields either way. */
type Draft = {
  name: string;
  institution: Institution;
  accountType: AccountType;
  last4: string;
  counts: boolean;
};

const BLANK_DRAFT: Draft = {
  name: "",
  institution: "CAPITAL_ONE",
  accountType: "CHECKING",
  last4: "",
  counts: false,
};

function draftOf(a: FinanceAccount): Draft {
  return {
    name: a.name,
    institution: a.institution,
    accountType: a.accountType,
    last4: a.last4 ?? "",
    counts: a.countsTowardSavings,
  };
}

/**
 * Only cash can hold a savings goal — the server enforces this, and the form mirrors it so the
 * checkbox is visibly unavailable rather than accepting a click and then failing.
 */
const CASH_TYPES: AccountType[] = ["CHECKING", "SAVINGS"];

/** The fields common to the add and edit forms, so the two never drift apart. */
function DraftFields({
  draft,
  onChange,
}: {
  draft: Draft;
  onChange: (next: Draft) => void;
}) {
  const canHoldSavings = CASH_TYPES.includes(draft.accountType);
  const savingsHint = canHoldSavings
    ? "Tick this for the accounts holding the house fund"
    : "Only checking and savings can hold a goal — this figure is money available for a down payment";

  return (
    <>
      <input
        placeholder="name (e.g. 360 Checking)"
        value={draft.name}
        onChange={(e) => onChange({ ...draft, name: e.target.value })}
      />
      <select
        value={draft.institution}
        onChange={(e) => onChange({ ...draft, institution: e.target.value as Institution })}
      >
        {INSTITUTIONS.map((i) => (
          <option key={i} value={i}>
            {INSTITUTION_LABELS[i]}
          </option>
        ))}
      </select>
      <select
        value={draft.accountType}
        onChange={(e) => {
          // Retyping to a card, loan or 401k clears the flag rather than leaving a stale true
          // behind for the server to reject on save.
          const next = e.target.value as AccountType;
          onChange({
            ...draft,
            accountType: next,
            counts: CASH_TYPES.includes(next) ? draft.counts : false,
          });
        }}
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
        value={draft.last4}
        onChange={(e) => onChange({ ...draft, last4: e.target.value })}
      />
      <label className={`inline${canHoldSavings ? "" : " disabled"}`} title={savingsHint}>
        <input
          type="checkbox"
          disabled={!canHoldSavings}
          checked={canHoldSavings && draft.counts}
          onChange={(e) => onChange({ ...draft, counts: e.target.checked })}
        />
        counts toward savings
      </label>
    </>
  );
}

/**
 * The real accounts, with their last-known balances.
 *
 * <p>Balances are typed in by hand until a statement import sets them. Editing and deleting an
 * account exist for the same reason the add form does: a bank or a type gets picked wrong on the
 * first try, and fixing it should never require touching the database directly — the API has
 * always supported both, this panel just did not expose them yet.
 */
export default function AccountsPanel({
  accounts,
  onChange,
}: {
  accounts: FinanceAccount[];
  onChange: () => void;
}) {
  const [adding, setAdding] = useState(false);
  const [draft, setDraft] = useState<Draft>(BLANK_DRAFT);

  const [editingId, setEditingId] = useState<number | null>(null);
  const [editDraft, setEditDraft] = useState<Draft>(BLANK_DRAFT);

  const [confirmingDeleteId, setConfirmingDeleteId] = useState<number | null>(null);

  const [balanceEditId, setBalanceEditId] = useState<number | null>(null);
  const [balance, setBalance] = useState("");

  const [error, setError] = useState("");

  async function add() {
    if (!draft.name.trim()) return;
    setError("");
    try {
      await createAccount({
        name: draft.name,
        institution: draft.institution,
        accountType: draft.accountType,
        last4: draft.last4 || null,
        countsTowardSavings: draft.counts,
        sortOrder: accounts.length,
      });
      setDraft(BLANK_DRAFT);
      setAdding(false);
      onChange();
    } catch (e) {
      setError(errorMessage(e, "could not add that account"));
    }
  }

  function startEdit(a: FinanceAccount) {
    setEditingId(a.id);
    setEditDraft(draftOf(a));
    setConfirmingDeleteId(null);
  }

  async function saveEdit(a: FinanceAccount) {
    if (!editDraft.name.trim()) return;
    setError("");
    try {
      await updateAccount(a.id, {
        name: editDraft.name,
        institution: editDraft.institution,
        accountType: editDraft.accountType,
        last4: editDraft.last4 || null,
        countsTowardSavings: editDraft.counts,
        active: a.active,
        sortOrder: a.sortOrder,
      });
      setEditingId(null);
      onChange();
    } catch (e) {
      setError(errorMessage(e, "could not save that account"));
    }
  }

  async function confirmDelete(a: FinanceAccount) {
    setError("");
    try {
      await deleteAccount(a.id);
      setConfirmingDeleteId(null);
      onChange();
    } catch (e) {
      setError(errorMessage(e, "could not delete that account"));
    }
  }

  async function saveBalance(account: FinanceAccount) {
    setError("");
    try {
      await recordBalance(account.id, { balance: Number(balance), asOf: null });
      setBalanceEditId(null);
      setBalance("");
      onChange();
    } catch (e) {
      setError(errorMessage(e, "could not save that balance"));
    }
  }

  return (
    <section className="accounts">
      <div className="section-head">
        <h3>accounts</h3>
        <button
          type="button"
          onClick={() => {
            setDraft(BLANK_DRAFT);
            setAdding((v) => !v);
          }}
        >
          {adding ? "cancel" : "+ account"}
        </button>
      </div>

      {error && <p className="error">{error}</p>}

      {adding && (
        <div className="account-form">
          <DraftFields draft={draft} onChange={setDraft} />
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
              <Fragment key={a.id}>
                <tr className={a.countsTowardSavings ? "is-savings" : undefined}>
                  <td>
                    <strong>{a.name}</strong>
                    {a.last4 && <span className="muted"> ·{a.last4}</span>}
                    <div className="muted small">
                      {INSTITUTION_LABELS[a.institution]} · {ACCOUNT_TYPE_LABELS[a.accountType]}
                      {a.transactionCount > 0 && ` · ${a.transactionCount} txns`}
                    </div>
                  </td>
                  <td className="num">
                    {balanceEditId === a.id ? (
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
                          setBalanceEditId(a.id);
                          setBalance(String(a.currentBalance));
                          setEditingId(null);
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
                  <td className="num account-row-actions">
                    <button type="button" className="ghost" onClick={() => startEdit(a)}>
                      edit
                    </button>
                    {confirmingDeleteId === a.id ? (
                      <>
                        <button
                          type="button"
                          className="ghost danger"
                          onClick={() => confirmDelete(a)}
                        >
                          confirm
                        </button>
                        <button
                          type="button"
                          className="ghost"
                          onClick={() => setConfirmingDeleteId(null)}
                        >
                          cancel
                        </button>
                      </>
                    ) : (
                      <button
                        type="button"
                        className="ghost"
                        onClick={() => setConfirmingDeleteId(a.id)}
                      >
                        delete
                      </button>
                    )}
                  </td>
                </tr>
                {confirmingDeleteId === a.id && (
                  <tr>
                    <td colSpan={3} className="delete-warning">
                      {a.transactionCount > 0
                        ? `This removes ${a.name} and all ${a.transactionCount} of its transactions. This cannot be undone.`
                        : `This removes ${a.name}. It has no transactions yet, so nothing else is affected.`}
                    </td>
                  </tr>
                )}
                {editingId === a.id && (
                  <tr>
                    <td colSpan={3}>
                      <div className="account-form">
                        <DraftFields draft={editDraft} onChange={setEditDraft} />
                        <button type="button" onClick={() => saveEdit(a)}>
                          save
                        </button>
                        <button
                          type="button"
                          className="ghost"
                          onClick={() => setEditingId(null)}
                        >
                          cancel
                        </button>
                      </div>
                    </td>
                  </tr>
                )}
              </Fragment>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
