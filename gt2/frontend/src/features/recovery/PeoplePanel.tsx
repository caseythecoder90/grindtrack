import { useState } from "react";
import { errorMessage } from "../../lib/api";
import {
  addPerson,
  archivePerson,
  logContact,
  updatePerson,
  type Person,
  type PersonRole,
} from "./recoveryApi";

interface Props {
  people: Person[];
  onChanged: () => void;
  /** Compact: the today card's one line, not the list. */
  compact?: boolean;
}

const ROLES: { value: PersonRole; label: string }[] = [
  { value: "sponsor", label: "sponsor" },
  { value: "prospect", label: "sponsor to ask" },
  { value: "friend", label: "friend" },
];

/** "3 days over", "due today", "next in 4 days", "still to ask". */
export function dueLine(p: Person, today: string): string {
  if (p.state === "ask") return "still to ask";
  if (p.state === "overdue") return `${p.overdueDays} day${p.overdueDays === 1 ? "" : "s"} over`;
  if (p.state === "due") return "due today";
  if (!p.nextDue) return "";
  const [y, m, d] = p.nextDue.split("-").map(Number);
  const [ty, tm, td] = today.split("-").map(Number);
  const days = Math.round((new Date(y, m - 1, d).getTime() - new Date(ty, tm - 1, td).getTime()) / 86_400_000);
  return `next in ${days} day${days === 1 ? "" : "s"}`;
}

function since(iso: string | null): string {
  if (!iso) return "never";
  const days = Math.floor((Date.now() - new Date(iso).getTime()) / 86_400_000);
  if (days === 0) return "today";
  if (days === 1) return "yesterday";
  return `${days} days ago`;
}

/**
 * The people: a sponsor to call every week, a sponsor still to be asked, friends to check on.
 * One list sorted by who is next, so the rotation is the top of it. Logging a call is a tap and
 * an optional line; for the one still to be asked, the first call is the asking.
 */
export default function PeoplePanel({ people, onChanged, compact = false }: Props) {
  const [open, setOpen] = useState<number | null>(null);
  const [note, setNote] = useState("");
  const [adding, setAdding] = useState(false);
  const [name, setName] = useState("");
  const [role, setRole] = useState<PersonRole>("friend");
  const [cadence, setCadence] = useState(7);
  const [newNote, setNewNote] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const today = new Date().toISOString().slice(0, 10);

  async function called(p: Person) {
    setBusy(true);
    setError("");
    try {
      await logContact(p.id, note.trim() || undefined);
      setNote("");
      setOpen(null);
      onChanged();
    } catch (e) {
      setError(errorMessage(e, "could not log that"));
    } finally {
      setBusy(false);
    }
  }

  async function add() {
    if (!name.trim()) return;
    setBusy(true);
    setError("");
    try {
      await addPerson({ name: name.trim(), role, cadenceDays: cadence, note: newNote.trim() || undefined });
      setName("");
      setNewNote("");
      setAdding(false);
      onChanged();
    } catch (e) {
      setError(errorMessage(e, "could not add them"));
    } finally {
      setBusy(false);
    }
  }

  async function cadenceOf(p: Person, days: number) {
    try {
      await updatePerson(p.id, { cadenceDays: days });
      onChanged();
    } catch (e) {
      setError(errorMessage(e, "could not change that"));
    }
  }

  async function remove(p: Person) {
    if (!window.confirm(`Take ${p.name} off the list? The calls stay on record.`)) return;
    try {
      await archivePerson(p.id);
      onChanged();
    } catch (e) {
      setError(errorMessage(e, "could not remove them"));
    }
  }

  if (compact) {
    const next = people.find((p) => p.state !== "ok");
    if (!next) return null;
    return (
      <div className="rec-card rec-nextcall">
        <div>
          <span className="rec-lbl">{next.state === "ask" ? "someone to ask" : "a call to make"}</span>
          <div className="rec-title">
            {next.name}{" "}
            <span className="muted">
              · {next.role === "prospect" ? "sponsor to ask" : `${next.role} · ${dueLine(next, today)}`}
            </span>
          </div>
        </div>
        <button type="button" className="rec-primary" disabled={busy} onClick={() => called(next)}>
          {next.state === "ask" ? "asked ✓" : "called ✓"}
        </button>
      </div>
    );
  }

  return (
    <div className="rec-card rec-people-card">
      <div className="rec-cardhead">
        <span className="rec-lbl">people</span>
        <span className="rec-note">{people.length === 0 ? "" : `${people.filter((p) => p.state !== "ok").length} to call`}</span>
      </div>
      {error && <div className="error">{error}</div>}
      {people.length === 0 && !adding && (
        <p className="rec-hint">Nobody yet. A sponsor to call every week, a sponsor still to be asked, friends to check on.</p>
      )}
      <ul className="rec-peoplelist">
        {people.map((p) => (
          <li key={p.id} className={"rec-person " + p.state}>
            <div className="rec-person-row">
              <div className="rec-person-who">
                <span className="rec-person-name">{p.name}</span>
                <span className={"rec-chip " + p.role}>{p.role === "prospect" ? "to ask" : p.role}</span>
              </div>
              <span className={"rec-note " + p.state}>{dueLine(p, today)}</span>
            </div>
            <div className="rec-person-row">
              <span className="rec-note">
                {p.state === "ask"
                  ? p.note ?? ""
                  : `last ${since(p.lastContact)}${p.lastNote ? ` · ${p.lastNote}` : ""} · every ${p.cadenceDays} days`}
              </span>
              <div className="rec-person-actions">
                {open === p.id ? null : (
                  <button type="button" className={p.state === "ok" ? "" : "rec-primary"} onClick={() => { setOpen(p.id); setNote(""); }}>
                    {p.state === "ask" ? "asked" : "called"}
                  </button>
                )}
                <button type="button" className="ghost" aria-label={`Remove ${p.name}`} onClick={() => remove(p)}>×</button>
              </div>
            </div>
            {open === p.id && (
              <div className="rec-person-log">
                <input
                  autoFocus
                  value={note}
                  maxLength={300}
                  placeholder={p.state === "ask" ? "how it went (optional)" : "how are they doing? (optional)"}
                  onChange={(e) => setNote(e.target.value)}
                  onKeyDown={(e) => { if (e.key === "Enter") called(p); if (e.key === "Escape") setOpen(null); }}
                />
                <button type="button" className="rec-primary" disabled={busy} onClick={() => called(p)}>
                  {p.state === "ask" ? "asked ✓" : "called ✓"}
                </button>
                <button type="button" className="linkish" onClick={() => setOpen(null)}>cancel</button>
                {p.state !== "ask" && (
                  <label className="rec-note">
                    every{" "}
                    <select value={p.cadenceDays} onChange={(e) => cadenceOf(p, Number(e.target.value))}>
                      {[1, 2, 3, 5, 7, 10, 14, 21, 30].map((n) => <option key={n} value={n}>{n}</option>)}
                    </select>{" "}
                    days
                  </label>
                )}
              </div>
            )}
          </li>
        ))}
      </ul>
      {adding ? (
        <div className="rec-person-add">
          <input value={name} maxLength={80} placeholder="name" aria-label="Name" onChange={(e) => setName(e.target.value)} onKeyDown={(e) => { if (e.key === "Enter") add(); }} />
          <select aria-label="Role" value={role} onChange={(e) => setRole(e.target.value as PersonRole)}>
            {ROLES.map((r) => <option key={r.value} value={r.value}>{r.label}</option>)}
          </select>
          {role !== "prospect" && (
            <label className="rec-note">
              every{" "}
              <input type="number" min={1} max={365} value={cadence} aria-label="Days between calls" onChange={(e) => setCadence(Math.max(1, Number(e.target.value) || 1))} />{" "}
              days
            </label>
          )}
          <input value={newNote} maxLength={300} placeholder="a note (where they are, what to remember)" aria-label="Note" onChange={(e) => setNewNote(e.target.value)} />
          <div className="rec-person-actions">
            <button type="button" className="rec-primary" disabled={busy || !name.trim()} onClick={add}>add</button>
            <button type="button" className="linkish" onClick={() => setAdding(false)}>cancel</button>
          </div>
        </div>
      ) : (
        <button type="button" className="linkish" onClick={() => setAdding(true)}>+ add someone</button>
      )}
    </div>
  );
}
