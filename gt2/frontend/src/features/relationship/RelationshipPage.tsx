import { useCallback, useEffect, useState } from "react";
import { api, jsonInit } from "../../lib/api";
import { todayISO } from "../../lib/dates";
import type { MomentKind, RelationshipSummary } from "../../lib/types";
import ClosenessCard from "./ClosenessCard";
import IdeasPanel from "./IdeasPanel";
import OccasionsPanel from "./OccasionsPanel";
import ReadingPanel from "./ReadingPanel";
import { daysAgo, inDays, MOMENT_KINDS, MOMENT_LABEL } from "./kinds";

/** Per-device, remembered locally. Laptops get opened on kitchen tables. */
const DISCREET_KEY = "gt-us-discreet";

function storedDiscreet(): boolean {
  try {
    return localStorage.getItem(DISCREET_KEY) === "1";
  } catch {
    return false;
  }
}

/**
 * The us tab.
 *
 * <p>Recency first, because that is the question you arrive with. Then what is coming up, because
 * that is the part with a deadline. Ideas and reading last — they are for browsing, not glancing.
 *
 * <p>There are deliberately no streaks, no targets and no scores anywhere on this page. It exists
 * to settle a question, not to grade an answer.
 */
export default function RelationshipPage() {
  const [summary, setSummary] = useState<RelationshipSummary | null>(null);
  const [error, setError] = useState("");
  const [discreet, setDiscreet] = useState(storedDiscreet);

  const [kind, setKind] = useState<MomentKind>("DATE_NIGHT");
  const [date, setDate] = useState(todayISO());
  const [note, setNote] = useState("");
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    setError("");
    try {
      setSummary(await api<RelationshipSummary>("/api/relationship/summary"));
    } catch (e) {
      setError(e instanceof Error ? e.message : "could not load this tab");
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  function toggleDiscreet() {
    setDiscreet((current) => {
      const next = !current;
      try {
        localStorage.setItem(DISCREET_KEY, next ? "1" : "0");
      } catch {
        /* a browser refusing storage should not break the page */
      }
      return next;
    });
  }

  async function log() {
    setSaving(true);
    setError("");
    try {
      await api(
        "/api/relationship/moments",
        jsonInit("POST", { occurredOn: date, kind, note, feltClose: null }),
      );
      setNote("");
      setDate(todayISO());
      load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "could not log that");
    } finally {
      setSaving(false);
    }
  }

  if (!summary) {
    return (
      <div className="us">
        {error ? <p className="error">{error}</p> : <p className="muted">loading…</p>}
      </div>
    );
  }

  const visibleRecency = summary.recency.filter((r) => r.kind !== "INTIMACY");
  const lately = discreet ? summary.lately.filter((m) => !m.isPrivate) : summary.lately;

  return (
    <div className="us">
      {error && <p className="error">{error}</p>}

      <div className="finance-top">
        {visibleRecency.slice(0, 3).map((r) => (
          <div className="stat" key={r.kind}>
            <span className="k">last {MOMENT_LABEL[r.kind]}</span>
            <span className="v">{daysAgo(r.daysSince)}</span>
          </div>
        ))}
        {summary.upcoming.length > 0 && (
          <div className="stat">
            <span className="k">{summary.upcoming[0].label.toLowerCase()}</span>
            <span className="v">{inDays(summary.upcoming[0].daysAway)}</span>
          </div>
        )}
      </div>

      <ClosenessCard
        closeness={summary.closeness}
        discreet={discreet}
        onToggleDiscreet={toggleDiscreet}
      />

      <section>
        <div className="section-head">
          <h3>log something</h3>
        </div>
        <div className="account-form">
          <select value={kind} onChange={(e) => setKind(e.target.value as MomentKind)}>
            {MOMENT_KINDS.map((k) => (
              <option key={k} value={k}>
                {MOMENT_LABEL[k]}
              </option>
            ))}
          </select>
          <input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
          <input
            placeholder="note (optional)"
            value={note}
            onChange={(e) => setNote(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") log();
            }}
          />
          <button type="button" className="primary" disabled={saving} onClick={log}>
            {saving ? "…" : "log it"}
          </button>
        </div>
        <p className="muted small">
          Backdating is fine — most of these get remembered the next morning.
        </p>
      </section>

      {summary.upcoming.length > 0 && (
        <section>
          <div className="section-head">
            <h3>coming up</h3>
          </div>
          <table className="txn-table">
            <tbody>
              {summary.upcoming.map((u) => (
                <tr key={u.id}>
                  <td>
                    {u.label}
                    <div className="muted small">
                      {u.on} · {inDays(u.daysAway)}
                      {u.ideaCount > 0
                        ? ` · ${u.ideaCount} idea${u.ideaCount === 1 ? "" : "s"} ready`
                        : " · nothing saved yet"}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}

      {summary.readyIdeas.length > 0 && (
        <section>
          <div className="section-head">
            <h3>ready to go</h3>
          </div>
          <p className="muted small">
            Easiest first. These are your own ideas from a day when you had them.
          </p>
          <table className="txn-table">
            <tbody>
              {summary.readyIdeas.slice(0, 5).map((i) => (
                <tr key={i.id}>
                  <td>
                    {i.title}
                    <div className="muted small">
                      {i.kind.toLowerCase()}
                      {i.occasion && ` · ${i.occasion}`}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}

      {lately.length > 0 && (
        <section>
          <div className="section-head">
            <h3>lately</h3>
          </div>
          <table className="txn-table">
            <tbody>
              {lately.map((m) => (
                <tr key={m.id}>
                  <td className="muted small">{m.occurredOn.slice(5)}</td>
                  <td>
                    <span className="tag">{MOMENT_LABEL[m.kind]}</span>
                    {m.note && ` ${m.note}`}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}

      <OccasionsPanel />
      <IdeasPanel onChange={load} />
      <ReadingPanel onChange={load} />
    </div>
  );
}
