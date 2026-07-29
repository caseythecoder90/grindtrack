import type { PlanItem, PlanItemType } from "../../lib/types";
import { doneCount, progressPercent, TYPE_LABEL, TYPES } from "./planModel";

interface Props {
  items: PlanItem[];
  filter: PlanItemType | null;
  onFilter: (t: PlanItemType | null) => void;
  referenceOpen: boolean;
  onToggleReference: () => void;
  error: string;
}

/** Overall progress bar plus the type-filter chips and the reference-sheets toggle. */
export default function PlanHeader({
  items, filter, onFilter, referenceOpen, onToggleReference, error,
}: Props) {
  const done = doneCount(items);
  const milestones = items.filter((i) => i.type === "milestone");
  const milestonesDone = doneCount(milestones);

  return (
    <div className="panel">
      <h2>4-year plan · {milestonesDone}/{milestones.length} milestones · {done}/{items.length} items done</h2>
      <div className="progress">
        <i style={{ width: `${progressPercent(done, items.length)}%` }} />
      </div>
      <div className="chips">
        <button type="button" className="chip" aria-pressed={filter === null}
          onClick={() => onFilter(null)}>
          all
        </button>
        {TYPES.map((t) => {
          const of = items.filter((i) => i.type === t);
          return (
            <button key={t} type="button" className="chip" aria-pressed={filter === t}
              onClick={() => onFilter(filter === t ? null : t)}>
              {TYPE_LABEL[t]} {doneCount(of)}/{of.length}
            </button>
          );
        })}
        <span className="spacer" />
        <button type="button" className="chip" aria-pressed={referenceOpen}
          onClick={onToggleReference}>
          reference sheets
        </button>
      </div>
      {error && <div className="error" style={{ marginTop: 8 }}>{error}</div>}
    </div>
  );
}
