import { useState } from "react";
import WorkDay from "./WorkDay";
import WorkSkills from "./WorkSkills";
import WorkWeek from "./WorkWeek";

type WorkView = "day" | "week" | "skills";

interface Props {
  /** Work hours feed the header's combined totals, so a save has to refresh it. */
  onSaved: () => void;
}

/**
 * Day-job tracker: a secondary tab bar over the daily work log, the 40h/week
 * view, and the deliberate skill/competency checklist. Kept apart from the
 * study tracker so paid-work hours never mix with self-improvement hours.
 */
export default function WorkPage({ onSaved }: Props) {
  const [view, setView] = useState<WorkView>("day");
  return (
    <>
      <nav className="tabs sub" aria-label="Work sections">
        {(["day", "week", "skills"] as WorkView[]).map((v) => (
          <button key={v} className={view === v ? "active" : ""}
            aria-current={view === v ? "page" : undefined}
            onClick={() => setView(v)}>
            {v}
          </button>
        ))}
      </nav>
      {view === "day" && <WorkDay onSaved={onSaved} />}
      {view === "week" && <WorkWeek />}
      {view === "skills" && <WorkSkills />}
    </>
  );
}
