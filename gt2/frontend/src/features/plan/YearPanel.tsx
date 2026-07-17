import type { PlanItem, PlanQuarter } from "../../lib/types";
import ItemRow from "./ItemRow";
import { byTarget, doneCount, progressPercent, YEAR_WINDOW } from "./planModel";

interface Props {
  year: number;
  /** Items passing the current type filter (all years; scoped here). */
  shown: PlanItem[];
  /** All items regardless of filter — year progress always reflects the whole year. */
  all: PlanItem[];
  quarters: PlanQuarter[];
  quartersOpen: boolean;
  onToggleQuarters: () => void;
  expandedId: number | null;
  onToggleExpand: (id: number) => void;
  onCycle: (item: PlanItem) => void;
  onSaveNotes: (item: PlanItem, notes: string) => void;
}

/** One plan year: progress bar, collapsible quarter roadmap, and its item rows. */
export default function YearPanel({
  year, shown, all, quarters, quartersOpen, onToggleQuarters,
  expandedId, onToggleExpand, onCycle, onSaveNotes,
}: Props) {
  const yearItems = shown.filter((i) => i.yearNum === year).sort(byTarget);
  const allYear = all.filter((i) => i.yearNum === year);
  const yearDone = doneCount(allYear);
  const yearQuarters = quarters
    .filter((q) => q.yearNum === year)
    .sort((a, b) => a.qtr - b.qtr);

  return (
    <div className="panel yearpanel">
      <h2>
        year {year} · {YEAR_WINDOW[year]} · {yearDone}/{allYear.length} done
      </h2>
      <div className="progress">
        <i style={{ width: `${progressPercent(yearDone, allYear.length)}%` }} />
      </div>
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
          onCycle={() => onCycle(item)} onSaveNotes={onSaveNotes} />
      ))}
      {yearItems.length === 0 && <div className="empty">nothing in this filter</div>}
    </div>
  );
}
