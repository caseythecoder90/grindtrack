import { useCallback, useEffect, useState } from "react";
import { api, jsonInit } from "../../lib/api";
import type { CategoryRule, RuleApplyResult } from "../../lib/types";
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
  const [error, setError] = useState("");
  const [note, setNote] = useState("");

  const load = useCallback(async () => {
    try {
      setRules(await api<CategoryRule[]>("/api/finance/rules"));
    } catch (e) {
      setError(e instanceof Error ? e.message : "could not load your rules");
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
      await api(
        "/api/finance/rules",
        jsonInit("POST", {
          pattern,
          matchType,
          category,
          priority: Number(priority) || 100,
        }),
      );
      setPattern("");
      setCategory("");
      load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "could not save that rule");
    } finally {
      setBusy(false);
    }
  }

  async function remove(rule: CategoryRule) {
    setError("");
    try {
      await api(`/api/finance/rules/${rule.id}`, { method: "DELETE" });
      load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "could not delete that rule");
    }
  }

  async function runAll() {
    setBusy(true);
    setError("");
    setNote("");
    try {
      const result = await api<RuleApplyResult>("/api/finance/rules/apply", { method: "POST" });
      setNote(
        `Filed ${result.categorized} of ${result.examined} rows. ` +
          (result.stillUncategorized > 0
            ? `${result.stillUncategorized} still need a category.`
            : "Nothing left to review."),
      );
      load();
      onChange();
    } catch (e) {
      setError(e instanceof Error ? e.message : "could not apply the rules");
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
          <datalist id="rule-category-options">
            {options.map((c) => (
              <option key={c} value={c} />
            ))}
          </datalist>
        </div>
      )}

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
                <td className="mono small">{r.priority}</td>
                <td>
                  {r.pattern}
                  {r.matchType !== "CONTAINS" && (
                    <span className="tag"> {r.matchType.toLowerCase()}</span>
                  )}
                </td>
                <td>
                  <span className="tag cat">{r.category}</span>
                </td>
                <td className="num muted small" title="rows this rule has filed">
                  {r.hitCount === 0 ? "never matched" : `${r.hitCount}×`}
                </td>
                <td className="num">
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
