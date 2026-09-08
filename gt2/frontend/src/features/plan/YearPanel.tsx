import type { PlanItem, PlanQuarter, ReadingSubject } from "../../lib/types";
import ItemRow from "./ItemRow";
import { byTarget, doneCount, progressPercent, yearWindow } from "./planModel";

interface Props {
  year: number;
  /** Items passing the current type filter (all years; scoped here). */
  shown: PlanItem[];
  /** All items regardless of filter — year progress always reflects the whole year. */
  all: PlanItem[];
  quarters: PlanQuarter[];
  quartersOpen: boolean;
  onToggleQuarters: () => void;
  /** Whether this year's items are showing. See PlanPage for what decides the default. */
  open: boolean;
  onToggle: () => void;
  /** True for the year today falls in — worth marking even when it is collapsed. */
  current: boolean;
  expandedId: number | null;
  onToggleExpand: (id: number) => void;
  onCycle: (item: PlanItem) => void;
  onSaveNotes: (item: PlanItem, notes: string) => void;
  /** Lunch progress by plan item id; empty until the reading endpoint answers. */
  reading: Map<number, ReadingSubject>;
}

/** One plan year: a header that opens it, a progress bar, the quarter roadmap, and its items. */
export default function YearPanel({
  year, shown, all, quarters, quartersOpen, onToggleQuarters,
  open, onToggle, current,
  expandedId, onToggleExpand, onCycle, onSaveNotes, reading,
}: Props) {
  const yearItems = shown.filter((i) => i.yearNum === year).sort(byTarget);
  const allYear = all.filter((i) => i.yearNum === year);
  const yearDone = doneCount(allYear);
  const yearQuarters = quarters
    .filter((q) => q.yearNum === year)
    .sort((a, b) => a.qtr - b.qtr);

  return (
    <div className={"panel yearpanel" + (current ? " is-current" : "")}>
      {/* The whole header is the control. A chevron alone is a small target for a thumb, and
          the year line is the obvious thing to reach for. */}
      <button type="button" className="yearhead" onClick={onToggle} aria-expanded={open}>
        <span className="chev">{open ? "▾" : "▸"}</span>
        <span className="yearname">
          year {year} · {yearWindow(year)}
          {current && <span className="badge badge-cert">now</span>}
        </span>
        <span className="yearcount">{yearDone}/{allYear.length} done</span>
      </button>
      {/* Outside the collapse on purpose: how far along a year is, is exactly what you want
          to see from a list of closed years. */}
      <div className="progress">
        <i style={{ width: `${progressPercent(yearDone, allYear.length)}%` }} />
      </div>
      {!open ? null : (
      <>
      {yearQuarters.length > 0 && (
        <>
          <button className="linkish" onClick={onToggleQuarters}>
            {quartersOpen ? "▾ hide" : "▸ show"} quarter roadmap
          </button>
          {quartersOpen &&
            yearQuarters.map((q) => (
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
        <ItemRow key={item.id} item={item} expanded={expandedId === item.id}
          onToggle={() => onToggleExpand(item.id)}
          onCycle={() => onCycle(item)} onSaveNotes={onSaveNotes}
          reading={reading.get(item.id)} />
      ))}
      {yearItems.length === 0 && <div className="empty">nothing in this filter</div>}
      </>
      )}
    </div>
  );
}
