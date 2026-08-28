import { useCallback, useEffect, useState } from "react";
import { api, jsonInit } from "../../lib/api";
import type { CategoryRule, FinanceTransaction } from "../../lib/types";
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
  const [busy, setBusy] = useState<number | null>(null);
  const [error, setError] = useState("");
  const [note, setNote] = useState("");

  const load = useCallback(async () => {
    try {
      const [pending, rules] = await Promise.all([
        api<FinanceTransaction[]>("/api/finance/transactions/uncategorized"),
        api<CategoryRule[]>("/api/finance/rules"),
      ]);
      setRows(pending);
      setKnown(rules.map((r) => r.category));
    } catch (e) {
      setError(e instanceof Error ? e.message : "could not load the review list");
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  async function file(txn: FinanceTransaction) {
    const category = (draft[txn.id] ?? "").trim();
    if (!category) return;

    setBusy(txn.id);
    setError("");
    setNote("");
    try {
      const result = await api<{ rule: CategoryRule | null; ruleExisted: boolean }>(
        `/api/finance/transactions/${txn.id}/categorize`,
        jsonInit("POST", { category, createRule: remember }),
      );
      if (result.rule) {
        setNote(
          `Filed, and anything matching "${result.rule.pattern}" now goes to ${result.rule.category}.`,
        );
      }
      setRows((current) => current.filter((r) => r.id !== txn.id));
      setDraft((current) => {
        const next = { ...current };
        delete next[txn.id];
        return next;
      });
      onChange();
    } catch (e) {
      setError(e instanceof Error ? e.message : "could not file that one");
    } finally {
      setBusy(null);
    }
  }

  const options = categoryOptions([...known, ...rows.map((r) => r.category)]);

  if (rows.length === 0) {
    return null;
  }

  return (
    <section className="review-inbox">
      <div className="section-head">
        <h3>to review</h3>
        <label className="remember">
          <input
            type="checkbox"
            checked={remember}
            onChange={(e) => setRemember(e.target.checked)}
          />
          remember the merchant
        </label>
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
          {rows.slice(0, 40).map((t) => (
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

      {rows.length > 40 && (
        <p className="muted small">
          Showing the newest 40 of {rows.length}. Each rule you write here removes every other row
          it matches from this list too.
        </p>
      )}
    </section>
  );
}
