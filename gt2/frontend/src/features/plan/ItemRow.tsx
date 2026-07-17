import { useState } from "react";
import type { PlanItem } from "../../lib/types";
import { STATUS_LABEL } from "./planModel";

interface Props {
  item: PlanItem;
  expanded: boolean;
  onToggle: () => void;
  onCycle: () => void;
  onSaveNotes: (item: PlanItem, notes: string) => void;
}

/** One plan item: status chip, title row, and an expandable detail/notes editor. */
export default function ItemRow({ item, expanded, onToggle, onCycle, onSaveNotes }: Props) {
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
