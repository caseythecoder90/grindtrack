import { useCallback, useEffect, useState } from "react";
import { api, jsonInit } from "../../lib/api";
import type { PlanData, PlanItem, PlanItemType, PlanStatus } from "../../lib/types";
import Reference from "./Reference";

const TYPE_LABEL: Record<PlanItemType, string> = {
  milestone: "milestones",
  cert: "certs",
  module: "protocol modules",
  book: "books",
  project: "projects",
};
const TYPES = Object.keys(TYPE_LABEL) as PlanItemType[];
const YEAR_WINDOW: Record<number, string> = {
  1: "Jul 2026 – Jun 2027",
  2: "Jul 2027 – Jun 2028",
  3: "Jul 2028 – Jun 2029",
};
const NEXT_STATUS: Record<PlanStatus, PlanStatus> = {
  not_started: "in_progress",
  in_progress: "done",
  done: "not_started",
};
const STATUS_LABEL: Record<PlanStatus, string> = {
  not_started: "not started",
  in_progress: "in progress",
  done: "done ✓",
};

export default function PlanPage() {
  const [data, setData] = useState<PlanData | null>(null);
  const [filter, setFilter] = useState<PlanItemType | null>(null);
  const [view, setView] = useState<"tracker" | "reference">("tracker");
  const [expanded, setExpanded] = useState<number | null>(null);
  const [openQuarters, setOpenQuarters] = useState(false);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    try {
      setData(await api<PlanData>("/api/plan"));
    } catch (e) {
      setError(e instanceof Error ? e.message : "could not load plan");
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  async function cycleStatus(item: PlanItem) {
    try {
      const updated = await api<PlanItem>(
        `/api/plan/items/${item.id}`,
        jsonInit("PATCH", { status: NEXT_STATUS[item.status] }),
      );
      setData((d) =>
        d ? { ...d, items: d.items.map((i) => (i.id === updated.id ? updated : i)) } : d,
      );
    } catch (e) {
      setError(e instanceof Error ? e.message : "update failed");
    }
  }

  async function saveNotes(item: PlanItem, notes: string) {
    if (notes === item.notes) return;
    try {
      const updated = await api<PlanItem>(
        `/api/plan/items/${item.id}`,
        jsonInit("PATCH", { notes }),
      );
      setData((d) =>
        d ? { ...d, items: d.items.map((i) => (i.id === updated.id ? updated : i)) } : d,
      );
    } catch (e) {
      setError(e instanceof Error ? e.message : "saving notes failed");
    }
  }

  async function importFile(file: File) {
    setError("");
    let parsed: unknown;
    try {
      parsed = JSON.parse(await file.text());
    } catch {
      setError("that file is not valid JSON");
      return;
    }
    try {
      await api("/api/plan/import", jsonInit("POST", parsed));
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "import failed");
    }
  }

  if (!data) return error ? <div className="panel error">{error}</div> : null;

  if (data.items.length === 0) {
    return (
      <div className="panel">
        <h2>plan</h2>
        <p className="refpara">
          No plan imported yet. Generate <code>plan.json</code> from the workbook
          (<code>gt2/tools/plan-import/xlsx_to_plan_json.py</code>) and upload it here.
          Re-uploading later updates the plan without losing your progress.
        </p>
        <ImportControl onFile={importFile} />
        {error && <div className="error">{error}</div>}
      </div>
    );
  }

  const items = data.items;
  const done = items.filter((i) => i.status === "done").length;
  const milestones = items.filter((i) => i.type === "milestone");
  const milestonesDone = milestones.filter((i) => i.status === "done").length;
  const shown = filter ? items.filter((i) => i.type === filter) : items;

  return (
    <>
      <div className="panel">
        <h2>3-year plan · {milestonesDone}/{milestones.length} milestones · {done}/{items.length} items done</h2>
        <div className="progress">
          <i style={{ width: `${Math.round((done / Math.max(1, items.length)) * 100)}%` }} />
        </div>
        <div className="chips">
          <span className={"chip" + (filter === null ? " on" : "")} onClick={() => setFilter(null)}>
            all
          </span>
          {TYPES.map((t) => {
            const of = items.filter((i) => i.type === t);
            const d = of.filter((i) => i.status === "done").length;
            return (
              <span key={t} className={"chip" + (filter === t ? " on" : "")}
                onClick={() => setFilter(filter === t ? null : t)}>
                {TYPE_LABEL[t]} {d}/{of.length}
              </span>
            );
          })}
          <span className="spacer" />
          <span className={"chip" + (view === "reference" ? " on" : "")}
            onClick={() => setView(view === "reference" ? "tracker" : "reference")}>
            reference sheets
          </span>
        </div>
        {error && <div className="error" style={{ marginTop: 8 }}>{error}</div>}
      </div>

      {view === "reference" ? (
        <Reference sheets={data.reference} />
      ) : (
        <>
          {[1, 2, 3].map((year) => {
            const yearItems = shown
              .filter((i) => i.yearNum === year)
              .sort(byTarget);
            const allYear = items.filter((i) => i.yearNum === year);
            const yearDone = allYear.filter((i) => i.status === "done").length;
            const quarters = data.quarters
              .filter((q) => q.yearNum === year)
              .sort((a, b) => a.qtr - b.qtr);
            return (
              <div className="panel yearpanel" key={year}>
                <h2>
                  year {year} · {YEAR_WINDOW[year]} · {yearDone}/{allYear.length} done
                </h2>
                <div className="progress">
                  <i style={{ width: `${Math.round((yearDone / Math.max(1, allYear.length)) * 100)}%` }} />
                </div>
                {quarters.length > 0 && (
                  <>
                    <button className="linkish" onClick={() => setOpenQuarters(!openQuarters)}>
                      {openQuarters ? "▾ hide" : "▸ show"} quarter roadmap
                    </button>
                    {openQuarters &&
                      quarters.map((q) => (
                        <div className="qcard" key={q.qtr}>
                          <div className="qhead">Q{q.qtr} · {q.windowLabel}</div>
                          <div><b>Primary:</b> {q.primaryFocus}</div>
                          {q.secondaryFocus && <div><b>Secondary:</b> {q.secondaryFocus}</div>}
                          {q.careerTrack && <div><b>Career track:</b> {q.careerTrack}</div>}
                          {q.deliverables && <div><b>Deliverables:</b> {q.deliverables}</div>}
                        </div>
                      ))}
                  </>
                )}
                {yearItems.map((item) => (
                  <ItemRow key={item.id} item={item} expanded={expanded === item.id}
                    onToggle={() => setExpanded(expanded === item.id ? null : item.id)}
                    onCycle={() => cycleStatus(item)} onSaveNotes={saveNotes} />
                ))}
                {yearItems.length === 0 && <div className="empty">nothing in this filter</div>}
              </div>
            );
          })}
          {shown.some((i) => i.yearNum === null) && (
            <div className="panel yearpanel">
              <h2>ongoing / anytime</h2>
              {shown
                .filter((i) => i.yearNum === null)
                .sort(byTarget)
                .map((item) => (
                  <ItemRow key={item.id} item={item} expanded={expanded === item.id}
                    onToggle={() => setExpanded(expanded === item.id ? null : item.id)}
                    onCycle={() => cycleStatus(item)} onSaveNotes={saveNotes} />
                ))}
            </div>
          )}
          <div className="panel">
            <h2>re-import</h2>
            <p className="refpara">
              Upload a regenerated <code>plan.json</code> after the workbook changes — statuses,
              completion dates, and notes on matching items are preserved.
            </p>
            <ImportControl onFile={importFile} />
          </div>
        </>
      )}
    </>
  );
}

function byTarget(a: PlanItem, b: PlanItem): number {
  if (a.targetDate && b.targetDate && a.targetDate !== b.targetDate) {
    return a.targetDate < b.targetDate ? -1 : 1;
  }
  if (!!a.targetDate !== !!b.targetDate) return a.targetDate ? -1 : 1;
  return a.sortOrder - b.sortOrder;
}

function ItemRow({ item, expanded, onToggle, onCycle, onSaveNotes }: {
  item: PlanItem;
  expanded: boolean;
  onToggle: () => void;
  onCycle: () => void;
  onSaveNotes: (item: PlanItem, notes: string) => void;
}) {
  const [notes, setNotes] = useState(item.notes);
  return (
    <div className={"plan-item" + (item.status === "done" ? " is-done" : "")}>
      <div className="plan-row">
        <button className={"status-chip " + item.status} onClick={onCycle}
          title="click to cycle status">
          {STATUS_LABEL[item.status]}
        </button>
        <span className={"badge badge-" + item.type}>{item.type}</span>
        <span className="plan-title" onClick={onToggle}>
          {item.title}
          {item.tier && <span className="muted"> · {item.tier}</span>}
        </span>
        <span className="plan-target">{item.targetLabel}</span>
        <button className="linkish" onClick={onToggle}>{expanded ? "▾" : "▸"}</button>
      </div>
      {expanded && (
        <div className="plan-detail">
          {item.details && <div className="refpara pre">{item.details}</div>}
          {item.completedAt && (
            <div className="muted">completed {item.completedAt.slice(0, 10)}</div>
          )}
          <label>My notes</label>
          <textarea value={notes} onChange={(e) => setNotes(e.target.value)}
            placeholder="e.g. passed with 92% · scheduled for Aug 14" />
          <div className="actions">
            <button onClick={() => onSaveNotes(item, notes)}>Save notes</button>
          </div>
        </div>
      )}
    </div>
  );
}

function ImportControl({ onFile }: { onFile: (f: File) => void }) {
  return (
    <input type="file" accept=".json,application/json"
      onChange={(e) => {
        const f = e.target.files?.[0];
        if (f) onFile(f);
        e.target.value = "";
      }} />
  );
}
