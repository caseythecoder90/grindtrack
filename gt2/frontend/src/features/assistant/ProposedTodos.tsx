import { useCallback, useEffect, useState } from "react";
import { errorMessage } from "../../lib/api";
import { acceptTodos, getTodoDraft, type TodoDraft } from "./assistantApi";

/**
 * Todos drafted mid-conversation, waiting on a click.
 *
 * <p>Fetched rather than handed the items, so what renders is what the button will add — a day's
 * batch accumulates across turns, and a card that showed only its own turn's items would hide the
 * rest of what "Add" is about to do. Once added the draft is gone, so the card says so and stops.
 */
export default function ProposedTodos({ date, onAdded }: { date: string; onAdded: () => void }) {
  const [draft, setDraft] = useState<TodoDraft | null>(null);
  const [gone, setGone] = useState(false);
  const [added, setAdded] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    try {
      const found = await getTodoDraft(date);
      if (found) setDraft(found);
      else setGone(true);
    } catch {
      setGone(true);
    }
  }, [date]);

  useEffect(() => {
    load();
  }, [load]);

  async function add() {
    setBusy(true);
    setError("");
    try {
      const result = await acceptTodos(date);
      setAdded(result.added);
      onAdded();
    } catch (e) {
      setError(errorMessage(e, "could not add those"));
    } finally {
      setBusy(false);
    }
  }

  if (gone || !draft) return null;

  return (
    <div className="proposal">
      <div className="proposal-head">
        <span className="lbl">drafted todos</span>
        <span className="proposal-sum">
          {draft.items.length === 1 ? "1 item" : `${draft.items.length} items`}
        </span>
      </div>

      <ul className="proposal-todos">
        {draft.items.map((t, i) => (
          <li key={i}>
            <span className="proposal-todo-title">{t.title}</span>
            <span className="proposal-todo-meta">
              {t.kind}
              {t.dueDate && ` · due ${t.dueDate}`}
            </span>
          </li>
        ))}
      </ul>

      {added !== null ? (
        <p className="proposal-done">
          added {added === 1 ? "1 todo" : `${added} todos`} ✓ — they are on the todos tab now.
        </p>
      ) : (
        <div className="proposal-actions">
          <span className="muted small">nothing added yet</span>
          <button type="button" className="primary" disabled={busy} onClick={add}>
            {busy ? "adding…" : draft.items.length === 1 ? "Add the todo" : "Add these todos"}
          </button>
        </div>
      )}

      {error && <div className="error">{error}</div>}
    </div>
  );
}
