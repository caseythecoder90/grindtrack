import { useState } from "react";
import WorkDay from "./WorkDay";
import WorkSkills from "./WorkSkills";
import WorkWeek from "./WorkWeek";

type WorkView = "day" | "week" | "skills";

/**
 * Day-job tracker: a secondary tab bar over the daily work log, the 40h/week
 * view, and the deliberate skill/competency checklist. Kept apart from the
 * study tracker so paid-work hours never mix with self-improvement hours.
 */
export default function WorkPage() {
  const [view, setView] = useState<WorkView>("day");
  return (
    <>
      <nav className="tabs">
        {(["day", "week", "skills"] as WorkView[]).map((v) => (
          <button key={v} className={view === v ? "active" : ""} onClick={() => setView(v)}>
            {v[0].toUpperCase() + v.slice(1)}
          </button>
        ))}
      </nav>
      {view === "day" && <WorkDay />}
      {view === "week" && <WorkWeek />}
      {view === "skills" && <WorkSkills />}
    </>
  );
}
