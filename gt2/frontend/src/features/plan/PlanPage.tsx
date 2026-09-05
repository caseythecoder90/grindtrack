import { useCallback, useEffect, useState } from "react";
import { errorMessage } from "../../lib/api";
import { getPlan, importPlan, updatePlanItem } from "./planApi";
// A plan item's reading hours are a focus-tracking fact, not a plan fact — so it is a
// second call joined by id here rather than a cross-feature dependency in the backend.
import { getReadingProgress } from "../focus/focusApi";
import type { PlanData, PlanItem, PlanItemType, ReadingSubject } from "../../lib/types";
import ImportControl from "./ImportControl";
import ItemRow from "./ItemRow";
import PlanHeader from "./PlanHeader";
import Reference from "./Reference";
import YearPanel from "./YearPanel";
import { byTarget, NEXT_STATUS, planYears } from "./planModel";

/**
 * Multi-year plan tracker. Loads the imported plan, then delegates rendering to
 * PlanHeader (progress + filters), YearPanel (one per year), and ItemRow;
 * all mutations (status cycling, notes, re-import) live here.
 */
export default function PlanPage() {
  const [data, setData] = useState<PlanData | null>(null);
  const [filter, setFilter] = useState<PlanItemType | null>(null);
  const [view, setView] = useState<"tracker" | "reference">("tracker");
  const [expanded, setExpanded] = useState<number | null>(null);
  const [openQuarters, setOpenQuarters] = useState(false);
  const [reading, setReading] = useState<Map<number, ReadingSubject>>(new Map());
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    try {
      setData(await getPlan());
    } catch (e) {
      setError(errorMessage(e, "could not load plan"));
    }
  }, []);

  useEffect(() => {
    load();
    getReadingProgress()
      .then((p) =>
        setReading(
          new Map(
            p.subjects
              .filter((s): s is ReadingSubject & { planItemId: number } => s.planItemId !== null)
              .map((s) => [s.planItemId, s]),
          ),
        ),
      )
      .catch(() => {
        /* the hours are a bonus on this page; the plan itself still renders */
      });
  }, [load]);

  function applyUpdate(updated: PlanItem) {
    setData((d) =>
      d ? { ...d, items: d.items.map((i) => (i.id === updated.id ? updated : i)) } : d,
    );
  }

  async function cycleStatus(item: PlanItem) {
    try {
      applyUpdate(
        await updatePlanItem(item.id, { status: NEXT_STATUS[item.status] }),
      );
    } catch (e) {
      setError(errorMessage(e, "update failed"));
    }
  }

  async function saveNotes(item: PlanItem, notes: string) {
    if (notes === item.notes) return;
    try {
      applyUpdate(
        await updatePlanItem(item.id, { notes }),
      );
    } catch (e) {
      setError(errorMessage(e, "saving notes failed"));
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
      await importPlan(parsed);
      await load();
    } catch (e) {
      setError(errorMessage(e, "import failed"));
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
  const shown = filter ? items.filter((i) => i.type === filter) : items;
  const ongoing = shown.filter((i) => i.yearNum === null);
  const toggleExpand = (id: number) => setExpanded(expanded === id ? null : id);

  return (
    <>
      <PlanHeader items={items} filter={filter} onFilter={setFilter}
        referenceOpen={view === "reference"}
        onToggleReference={() => setView(view === "reference" ? "tracker" : "reference")}
        error={error} />

      {view === "reference" ? (
        <Reference sheets={data.reference} />
      ) : (
        <>
          {planYears(items, data.quarters).map((year) => (
            <YearPanel key={year} year={year} shown={shown} all={items}
              quarters={data.quarters} quartersOpen={openQuarters}
              onToggleQuarters={() => setOpenQuarters(!openQuarters)}
              expandedId={expanded} onToggleExpand={toggleExpand}
              onCycle={cycleStatus} onSaveNotes={saveNotes} reading={reading} />
          ))}
          {ongoing.length > 0 && (
            <div className="panel yearpanel">
              <h2>ongoing / anytime</h2>
              {[...ongoing].sort(byTarget).map((item) => (
                <ItemRow key={item.id} item={item} expanded={expanded === item.id}
                  onToggle={() => toggleExpand(item.id)}
                  onCycle={() => cycleStatus(item)} onSaveNotes={saveNotes}
                  reading={reading.get(item.id)} />
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
