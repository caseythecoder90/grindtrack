import { useCallback, useEffect, useState } from "react";
import Segmented from "../../components/Segmented";
import { api, jsonInit } from "../../lib/api";
import { todayISO } from "../../lib/dates";
import type { Todo, TodoKind } from "../../lib/types";

type Filter = "all" | TodoKind;

const FILTERS: { value: Filter; label: string; tone?: string }[] = [
  { value: "all", label: "all" },
  { value: "work", label: "work", tone: "work" },
  { value: "personal", label: "personal", tone: "study" },
];

/** Overdue and due-today deserve a nudge; everything else is just a date. */
function dueClass(dueDate: string | null, done: boolean): string {
  if (!dueDate || done) return "muted";
  const today = todayISO();
  if (dueDate < today) return "due overdue";
  if (dueDate === today) return "due today";
  return "muted";
}

function dueLabel(dueDate: string): string {
  const today = todayISO();
  if (dueDate === today) return "today";
  return dueDate.slice(5);
}

/**
 * The todo list. Short-lived items tagged work or personal, so the same list can
 * be narrowed to whichever half of the day you're in — separate from the Plan
 * tab, which is the fixed 4-year roadmap.
 */
export default function TodoPage() {
  const [todos, setTodos] = useState<Todo[]>([]);
  const [filter, setFilter] = useState<Filter>("all");
  const [title, setTitle] = useState("");
  const [kind, setKind] = useState<TodoKind>("work");
  const [due, setDue] = useState("");
  const [error, setError] = useState("");

  const load = useCallback(async (f: Filter) => {
    setError("");
    try {
      const query = f === "all" ? "" : `?kind=${f}`;
      setTodos(await api<Todo[]>(`/api/todos${query}`));
    } catch (e) {
      setError(e instanceof Error ? e.message : "could not load the list");
    }
  }, []);

  useEffect(() => {
    load(filter);
  }, [load, filter]);

  async function add() {
    if (!title.trim()) return;
    setError("");
    try {
      await api("/api/todos", jsonInit("POST", { title, kind, dueDate: due || null }));
      setTitle("");
      setDue("");
      load(filter);
    } catch (e) {
      setError(e instanceof Error ? e.message : "could not add that");
    }
  }

  async function toggle(todo: Todo) {
    // Optimistic: the checkbox should feel instant, and a failed PATCH re-syncs below.
    setTodos((list) => list.map((t) => (t.id === todo.id ? { ...t, done: !t.done } : t)));
    try {
      await api(`/api/todos/${todo.id}`, jsonInit("PATCH", { done: !todo.done }));
    } catch {
      setError("could not save that change");
    }
    load(filter);
  }

  async function remove(id: number) {
    setError("");
    try {
      await api(`/api/todos/${id}`, { method: "DELETE" });
      load(filter);
    } catch (e) {
      setError(e instanceof Error ? e.message : "could not delete that");
    }
  }

  const open = todos.filter((t) => !t.done);
  const done = todos.filter((t) => t.done);

  return (
    <div className="panel">
      <div className="panelhead">
        <h2>todo</h2>
        <Segmented label="Filter todos" value={filter} onChange={setFilter} options={FILTERS} />
      </div>

      <div className="todoadd">
        <input aria-label="What needs doing" value={title} maxLength={300}
          placeholder="What needs doing?"
          onChange={(e) => setTitle(e.target.value)}
          onKeyDown={(e) => { if (e.key === "Enter") add(); }} />
        <Segmented label="Which list" value={kind} onChange={setKind}
          options={[
            { value: "work", label: "work", tone: "work" },
            { value: "personal", label: "personal", tone: "study" },
          ]} />
        <input type="date" aria-label="Due date (optional)" value={due}
          onChange={(e) => setDue(e.target.value)} />
        <button className="primary" onClick={add}>Add</button>
      </div>

      {error && <div className="error">{error}</div>}

      {todos.length === 0 ? (
        <div className="empty">nothing here yet — add the first thing above</div>
      ) : (
        <ul className="todolist">
          {[...open, ...done].map((t) => (
            <li key={t.id} className={t.done ? "done" : ""}>
              <label>
                <input type="checkbox" checked={t.done} onChange={() => toggle(t)} />
                <span className={"tag " + (t.kind === "work" ? "work-ink" : "study-ink")}>
                  {t.kind}
                </span>
                <span className="title">{t.title}</span>
              </label>
              {t.dueDate && (
                <span className={dueClass(t.dueDate, t.done)}>{dueLabel(t.dueDate)}</span>
              )}
              <button className="ghost" onClick={() => remove(t.id)}
                aria-label={`Delete ${t.title}`}>×</button>
            </li>
          ))}
        </ul>
      )}

      {open.length > 0 && (
        <div className="todofoot">{open.length} open · {done.length} done</div>
      )}
    </div>
  );
}
