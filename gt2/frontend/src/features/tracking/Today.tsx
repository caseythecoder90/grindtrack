
import { useEffect, useState } from "react";
import { api, jsonInit } from "../../lib/api";
import { todayISO } from "../../lib/dates";
import { CATEGORIES, type DayLog } from "../../lib/types";

interface Props {
  onSaved: () => void;
}

export default function Today({ onSaved }: Props) {
  const [date, setDate] = useState(todayISO());
  const [hours, setHours] = useState("0");
  const [cats, setCats] = useState<Set<string>>(new Set());
  const [energy, setEnergy] = useState<number | null>(null);
  const [focus, setFocus] = useState("");
  const [did, setDid] = useState("");
  const [wins, setWins] = useState("");
  const [blockers, setBlockers] = useState("");
  const [toast, setToast] = useState(false);

  useEffect(() => {
    api<DayLog | null>(`/api/days/${date}`).then((d) => {
      setHours(String(d?.hours ?? 0));
      setCats(new Set(d?.categories ?? []));
      setEnergy(d?.energy ?? null);
      setFocus(d?.focus ?? "");
      setDid(d?.did ?? "");
      setWins(d?.wins ?? "");
      setBlockers(d?.blockers ?? "");
    });
  }, [date]);

  function toggleCat(c: string) {
    const next = new Set(cats);
    next.has(c) ? next.delete(c) : next.add(c);
    setCats(next);
  }

  async function save() {
    await api(`/api/days/${date}`, jsonInit("PUT", {
      hours: Number(hours), categories: [...cats], focus, did, wins, blockers, energy,
    }));
    setToast(true);
    setTimeout(() => setToast(false), 1600);
    onSaved();
  }

  return (
    <div className="panel">
      <h2>daily log</h2>
      <div className="row3">
        <div>
          <label htmlFor="d-date">Date</label>
          <input id="d-date" type="date" value={date} onChange={(e) => setDate(e.target.value)} />
        </div>
        <div>
          <label htmlFor="d-hours">Hours</label>
          <input id="d-hours" type="number" min={0} max={24} step={0.5} value={hours}
            onChange={(e) => setHours(e.target.value)} />
        </div>
        <div>
          <label>Energy</label>
          <div className="energy">
            {[1, 2, 3, 4, 5].map((n) => (
              <button key={n} className={energy === n ? "on" : ""}
                onClick={() => setEnergy(energy === n ? null : n)}>{n}</button>
            ))}
          </div>
        </div>
      </div>
      <label>Categories</label>
      <div className="chips">
        {CATEGORIES.map((c) => (
          <span key={c} className={"chip" + (cats.has(c) ? " on" : "")} onClick={() => toggleCat(c)}>
            {c}
          </span>
        ))}
      </div>
      <div className="row">
        <div>
          <label htmlFor="d-focus">Planned focus</label>
          <textarea id="d-focus" value={focus} onChange={(e) => setFocus(e.target.value)}
            placeholder="What was this block for? e.g. CKA labs: etcd backup/restore" />
        </div>
        <div>
          <label htmlFor="d-did">What actually happened</label>
          <textarea id="d-did" value={did} onChange={(e) => setDid(e.target.value)} />
        </div>
      </div>
      <div className="row">
        <div>
          <label htmlFor="d-wins">Wins</label>
          <textarea id="d-wins" value={wins} onChange={(e) => setWins(e.target.value)}
            placeholder="Work wins here are Impact Log material" />
        </div>
        <div>
          <label htmlFor="d-blockers">Blockers / notes</label>
          <textarea id="d-blockers" value={blockers} onChange={(e) => setBlockers(e.target.value)} />
        </div>
      </div>
      <div className="actions">
        <button className="primary" onClick={save}>Save day</button>
        <span className={"toast" + (toast ? " show" : "")}>saved ✓</span>
      </div>
    </div>
  );
}
