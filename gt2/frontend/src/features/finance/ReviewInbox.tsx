import { useCallback, useEffect, useState } from "react";
import { errorMessage } from "../../lib/api";
import { categorizeAndLearn, getRules, getUncategorized, reclassifyAll } from "./financeApi";
import type { FinanceTransaction } from "../../lib/types";
import { categoryOptions } from "./categories";
import { signed } from "./money";

/**
 * The rows nothing could categorize, and the one place they get fixed.
 *
 * <p>Categorizing a row by hand is tedious. Categorizing it *and* teaching the app the merchant is
 * a workflow — that is what the "remember this" box does. It creates a rule from the normalized
 * merchant, so the next import files it automatically and this list gets shorter every time you
 * use it rather than growing forever.
 */
export default function ReviewInbox({ onChange }: { onChange: () => void }) {
  const [rows, setRows] = useState<FinanceTransaction[]>([]);
  const [known, setKnown] = useState<string[]>([]);
  const [draft, setDraft] = useState<Record<number, string>>({});
  const [remember, setRemember] = useState(true);
  // Biggest-first by default. One rule for a landlord clears eight rows; one for a coffee shop
  // clears one -- so the order that empties this list fastest is by amount, not by date.
  const [sort, setSort] = useState<"amount" | "date">("amount");
  const [page, setPage] = useState(0);
  const [rechecking, setRechecking] = useState(false);
  const [busy, setBusy] = useState<number | null>(null);
  const [error, setError] = useState("");
  const [note, setNote] = useState("");

  const load = useCallback(async () => {
    try {
      const [pending, rules] = await Promise.all([getUncategorized(), getRules()]);
      setRows(pending);
      setKnown(rules.map((r) => r.category));
    } catch (e) {
      setError(errorMessage(e, "could not load the review list"));
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  /**
   * Re-decides spend / payment / transfer for every row.
   *
   * <p>Lives at the top of this tab rather than beside the rules, because the thing it fixes shows
   * up somewhere else entirely: a loan payment filed as a card payment is missing from the budget,
   * and nothing on the budget page hints that the cause is a transaction type.
   */
  async function recheckTypes() {
    setRechecking(true);
    setError("");
    setNote("");
    try {
      const r = await reclassifyAll();
      setNote(
        r.changed === 0
          ? `Re-checked ${r.examined} transactions; nothing needed changing.`
          : `Re-checked ${r.examined} transactions and re-typed ${r.changed}. Card payments are out of spending; loan payments are now in it.`,
      );
      await load();
      onChange();
    } catch (e) {
      setError(errorMessage(e, "could not re-check types"));
    } finally {
      setRechecking(false);
    }
  }

  async function file(txn: FinanceTransaction) {
    const category = (draft[txn.id] ?? "").trim();
    if (!category) return;

    setBusy(txn.id);
    setError("");
    setNote("");
    try {
      const result = await categorizeAndLearn(txn.id, category, remember);
      if (result.rule) {
        setNote(
          `Filed, and anything matching "${result.rule.pattern}" now goes to ${result.rule.category}.`,
        );
      }
      setRows((current) => {
        const next = current.filter((r) => r.id !== txn.id);
        // Filing the last row on the final page would otherwise leave you staring at an empty one.
        const pages = Math.max(1, Math.ceil(next.length / PER_PAGE));
        setPage((p) => Math.min(p, pages - 1));
        return next;
      });
      setDraft((current) => {
        const next = { ...current };
        delete next[txn.id];
        return next;
      });
      onChange();
    } catch (e) {
      setError(errorMessage(e, "could not file that one"));
    } finally {
      setBusy(null);
    }
  }

  const options = categoryOptions([...known, ...rows.map((r) => r.category)]);

  const PER_PAGE = 40;
  const ordered =
    sort === "amount"
      ? [...rows].sort((a, b) => Math.abs(b.amount) - Math.abs(a.amount))
      : rows;
  const totalPages = Math.max(1, Math.ceil(ordered.length / PER_PAGE));
  const safePage = Math.min(page, totalPages - 1);
  const shown = ordered.slice(safePage * PER_PAGE, safePage * PER_PAGE + PER_PAGE);

  if (rows.length === 0) {
    return null;
  }

  return (
    <section className="review-inbox">
      <div className="section-head">
        <h3>to review</h3>
        <div>
          <div className="seg">
            <button
              type="button"
              aria-pressed={sort === "amount"}
              onClick={() => {
                setSort("amount");
                setPage(0);
              }}
              title="Clears the list fastest"
            >
              biggest
            </button>
            <button
              type="button"
              aria-pressed={sort === "date"}
              onClick={() => {
                setSort("date");
                setPage(0);
              }}
            >
              newest
            </button>
          </div>
          <button
            type="button"
            disabled={rechecking}
            title="Re-decides spend vs payment vs transfer for every transaction. Run this after a classification fix — it is what moves loan payments back into spending."
            onClick={recheckTypes}
          >
            {rechecking ? "checking…" : "re-check types"}
          </button>
          <label className="remember">
            <input
              type="checkbox"
              checked={remember}
              onChange={(e) => setRemember(e.target.checked)}
            />
            remember the merchant
          </label>
        </div>
      </div>

      <p className="muted small">
        {rows.length} row{rows.length === 1 ? "" : "s"} no rule could place. With{" "}
        <b>remember the merchant</b> ticked, filing one writes a rule so the next import does it for
        you.
      </p>

      {error && <p className="error">{error}</p>}
      {note && <p className="muted small">{note}</p>}

      <datalist id="category-options">
        {options.map((c) => (
          <option key={c} value={c} />
        ))}
      </datalist>

      <table className="txn-table">
        <tbody>
          {shown.map((t) => (
            <tr key={t.id}>
              <td className="muted small">{t.postedDate.slice(5)}</td>
              <td>
                <div>{t.merchant ?? t.description}</div>
                {t.merchant && t.merchant !== t.description && (
                  <div className="muted small raw">{t.description}</div>
                )}
                {t.issuerCategory && (
                  <span className="tag"> bank said: {t.issuerCategory}</span>
                )}
              </td>
              <td className={`num ${t.amount < 0 ? "negative" : "positive"}`}>{signed(t.amount)}</td>
              <td className="file-cell">
                <input
                  list="category-options"
                  placeholder="category"
                  value={draft[t.id] ?? ""}
                  onChange={(e) => setDraft({ ...draft, [t.id]: e.target.value })}
                  onKeyDown={(e) => {
                    if (e.key === "Enter") file(t);
                  }}
                />
                <button
                  type="button"
                  className="ghost"
                  disabled={busy === t.id || !(draft[t.id] ?? "").trim()}
                  onClick={() => file(t)}
                >
                  file
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      {ordered.length > PER_PAGE && (
        <div className="pager">
          <button type="button" disabled={safePage === 0} onClick={() => setPage(safePage - 1)}>
            ← prev
          </button>
          <span className="muted small">
            {safePage * PER_PAGE + 1}–{Math.min((safePage + 1) * PER_PAGE, ordered.length)} of{" "}
            {ordered.length} · page {safePage + 1} of {totalPages}
          </span>
          <button
            type="button"
            disabled={safePage >= totalPages - 1}
            onClick={() => setPage(safePage + 1)}
          >
            next →
          </button>
        </div>
      )}
      <p className="muted small">
        Each rule you write here removes every other row it matches, from every page — so the list
        shrinks faster than you page through it.
      </p>
    </section>
  );
}
