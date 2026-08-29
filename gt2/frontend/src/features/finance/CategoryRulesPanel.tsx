import { useCallback, useEffect, useState } from "react";
import { errorMessage } from "../../lib/api";
import {
  applyRules,
  createRule,
  deleteRule,
  getRules,
  reclassifyAll,
  updateRule,
} from "./financeApi";
import type { CategoryRule } from "../../lib/types";
import { categoryOptions } from "./categories";

const MATCH_TYPES: CategoryRule["matchType"][] = ["CONTAINS", "EQUALS", "REGEX"];

/**
 * The rules, and the button that re-runs them over everything.
 *
 * <p>Re-running is safe by construction: a rule can never overwrite a category a person chose, so
 * the worst a bad rule can do is mis-file rows that automation had filed anyway — fix the rule,
 * run it again. That guarantee is what makes this a one-click operation instead of one that needs
 * a confirmation dialog.
 */
export default function CategoryRulesPanel({ onChange }: { onChange: () => void }) {
  const [rules, setRules] = useState<CategoryRule[]>([]);
  const [open, setOpen] = useState(false);
  const [pattern, setPattern] = useState("");
  const [matchType, setMatchType] = useState<CategoryRule["matchType"]>("CONTAINS");
  const [category, setCategory] = useState("");
  const [priority, setPriority] = useState("100");
  const [busy, setBusy] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editCategory, setEditCategory] = useState("");
  const [editPriority, setEditPriority] = useState("100");
  const [error, setError] = useState("");
  const [note, setNote] = useState("");

  const load = useCallback(async () => {
    try {
      setRules(await getRules());
    } catch (e) {
      setError(errorMessage(e, "could not load your rules"));
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  async function add() {
    if (!pattern.trim() || !category.trim()) return;
    setBusy(true);
    setError("");
    setNote("");
    try {
      await createRule({
        pattern,
        matchType,
        category,
        priority: Number(priority) || 100,
      });
      setPattern("");
      setCategory("");
      load();
    } catch (e) {
      setError(errorMessage(e, "could not save that rule"));
    } finally {
      setBusy(false);
    }
  }

  async function saveEdit(rule: CategoryRule) {
    if (!editCategory.trim()) return;
    setError("");
    try {
      await updateRule(rule.id, {
        pattern: rule.pattern,
        matchType: rule.matchType,
        category: editCategory,
        priority: Number(editPriority) || 100,
        active: rule.active,
      });
      setEditingId(null);
      load();
      onChange();
    } catch (e) {
      setError(errorMessage(e, "could not save that rule"));
    }
  }

  async function remove(rule: CategoryRule) {
    setError("");
    try {
      await deleteRule(rule.id);
      load();
    } catch (e) {
      setError(errorMessage(e, "could not delete that rule"));
    }
  }

  async function runReclassify() {
    setBusy(true);
    setError("");
    setNote("");
    try {
      const r = await reclassifyAll();
      setNote(
        `Re-checked ${r.examined} transactions; ${r.changed} changed type. ` +
          "Card payments are excluded from spending, loan payments are not.",
      );
      onChange();
    } catch (e) {
      setError(errorMessage(e, "could not re-classify"));
    } finally {
      setBusy(false);
    }
  }

  async function runAll() {
    setBusy(true);
    setError("");
    setNote("");
    try {
      const result = await applyRules();
      setNote(
        `Filed ${result.categorized} of ${result.examined} rows. ` +
          (result.stillUncategorized > 0
            ? `${result.stillUncategorized} still need a category.`
            : "Nothing left to review."),
      );
      load();
      onChange();
    } catch (e) {
      setError(errorMessage(e, "could not apply the rules"));
    } finally {
      setBusy(false);
    }
  }

  const options = categoryOptions(rules.map((r) => r.category));

  return (
    <section className="rules">
      <div className="section-head">
        <h3>category rules</h3>
        <div>
          <button
            type="button"
            disabled={busy}
            title="Re-decides spend vs payment vs transfer for every row, using the current rules"
            onClick={runReclassify}
          >
            re-check types
          </button>
          {rules.length > 0 && (
            <button type="button" disabled={busy} onClick={runAll}>
              {busy ? "running…" : "re-run on everything"}
            </button>
          )}
          <button type="button" onClick={() => setOpen((v) => !v)}>
            {open ? "cancel" : "+ rule"}
          </button>
        </div>
      </div>

      {error && <p className="error">{error}</p>}
      {note && <p className="muted small">{note}</p>}

      {open && (
        <div className="account-form">
          <input
            placeholder="pattern (e.g. WHOLEFDS)"
            value={pattern}
            onChange={(e) => setPattern(e.target.value)}
          />
          <select
            value={matchType}
            onChange={(e) => setMatchType(e.target.value as CategoryRule["matchType"])}
          >
            {MATCH_TYPES.map((m) => (
              <option key={m} value={m}>
                {m.toLowerCase()}
              </option>
            ))}
          </select>
          <input
            list="rule-category-options"
            placeholder="category"
            value={category}
            onChange={(e) => setCategory(e.target.value)}
          />
          <input
            type="number"
            step="10"
            title="lower runs first, so a specific rule can beat a general one"
            value={priority}
            onChange={(e) => setPriority(e.target.value)}
          />
          <button type="button" disabled={busy} onClick={add}>
            add
          </button>
        </div>
      )}

      {/* Outside the add form on purpose: the inline edit inputs use it too, and it must exist
          whether or not that form is open. */}
      <datalist id="rule-category-options">
        {options.map((c) => (
          <option key={c} value={c} />
        ))}
      </datalist>

      {rules.length === 0 ? (
        <p className="muted small">
          No rules yet. The quickest way to write them is from the review list above — file a row
          with <b>remember the merchant</b> ticked and the rule appears here.
        </p>
      ) : (
        <table className="txn-table rules-table">
          <tbody>
            {rules.map((r) => (
              <tr key={r.id} className={r.active ? "" : "inactive"}>
                <td className="mono small">
                  {editingId === r.id ? (
                    <input
                      type="number"
                      step="10"
                      className="rule-priority"
                      value={editPriority}
                      onChange={(e) => setEditPriority(e.target.value)}
                    />
                  ) : (
                    r.priority
                  )}
                </td>
                <td>
                  {r.pattern}
                  {r.matchType !== "CONTAINS" && (
                    <span className="tag"> {r.matchType.toLowerCase()}</span>
                  )}
                </td>
                <td>
                  {editingId === r.id ? (
                    <span className="rule-edit">
                      <input
                        list="rule-category-options"
                        autoFocus
                        value={editCategory}
                        onChange={(e) => setEditCategory(e.target.value)}
                        onKeyDown={(e) => e.key === "Enter" && saveEdit(r)}
                      />
                      <button type="button" onClick={() => saveEdit(r)}>
                        save
                      </button>
                      <button type="button" className="ghost" onClick={() => setEditingId(null)}>
                        cancel
                      </button>
                    </span>
                  ) : (
                    <span className="tag cat">{r.category}</span>
                  )}
                </td>
                <td
                  className="num muted small"
                  title={
                    r.hitCount === 0
                      ? "This rule has not filed any row yet. If you created it by filing a transaction, that row is already categorized by hand and re-runs skip it — so a merchant you have only seen once will sit at zero and still work on the next import."
                      : "rows this rule has filed"
                  }
                >
                  {r.hitCount === 0 ? "0 so far" : `${r.hitCount}×`}
                </td>
                <td className="num rule-actions">
                  <button
                    type="button"
                    className="ghost"
                    onClick={() => {
                      setEditingId(r.id);
                      setEditCategory(r.category);
                      setEditPriority(String(r.priority));
                    }}
                  >
                    edit
                  </button>
                  <button type="button" className="ghost" onClick={() => remove(r)}>
                    delete
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
