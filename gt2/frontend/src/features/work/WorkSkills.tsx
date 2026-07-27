import { useCallback, useEffect, useState } from "react";
import { api, jsonInit } from "../../lib/api";
import type { WorkSkill, WorkSkillStatus } from "../../lib/types";

const NEXT: Record<WorkSkillStatus, WorkSkillStatus> = {
  not_started: "in_progress",
  in_progress: "proficient",
  proficient: "not_started",
};

const STATUS_LABEL: Record<WorkSkillStatus, string> = {
  not_started: "not started",
  in_progress: "in progress",
  proficient: "proficient ✓",
};

/**
 * The deliberate competency checklist: operational + domain skills to build on
 * the job that the role won't force (observability tooling, prod-DB querying,
 * tracing the existing system, the new design). Fully user-managed — nothing is
 * seeded, so no employer specifics ever reach the public repo.
 */
export default function WorkSkills() {
  const [skills, setSkills] = useState<WorkSkill[]>([]);
  const [name, setName] = useState("");
  const [category, setCategory] = useState("");
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    try {
      setSkills(await api<WorkSkill[]>("/api/work/skills"));
    } catch (e) {
      setError(e instanceof Error ? e.message : "could not load skills");
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  function replace(updated: WorkSkill) {
    setSkills((list) => list.map((s) => (s.id === updated.id ? updated : s)));
  }

  async function add() {
    if (!name.trim()) return;
    setError("");
    try {
      const created = await api<WorkSkill>(
        "/api/work/skills",
        jsonInit("POST", { name: name.trim(), category: category.trim() }),
      );
      setSkills((list) => [...list, created]);
      setName("");
      setCategory("");
    } catch (e) {
      setError(e instanceof Error ? e.message : "could not add the skill");
    }
  }

  async function cycle(skill: WorkSkill) {
    try {
      replace(
        await api<WorkSkill>(
          `/api/work/skills/${skill.id}`,
          jsonInit("PATCH", { status: NEXT[skill.status] }),
        ),
      );
    } catch (e) {
      setError(e instanceof Error ? e.message : "update failed");
    }
  }

  async function saveNotes(skill: WorkSkill, notes: string) {
    if (notes === skill.notes) return;
    try {
      replace(
        await api<WorkSkill>(`/api/work/skills/${skill.id}`, jsonInit("PATCH", { notes })),
      );
    } catch (e) {
      setError(e instanceof Error ? e.message : "saving notes failed");
    }
  }

  async function remove(skill: WorkSkill) {
    try {
      await api(`/api/work/skills/${skill.id}`, { method: "DELETE" });
      setSkills((list) => list.filter((s) => s.id !== skill.id));
    } catch (e) {
      setError(e instanceof Error ? e.message : "delete failed");
    }
  }

  const done = skills.filter((s) => s.status === "proficient").length;

  return (
    <div className="panel">
      <h2>work skills · {done}/{skills.length} proficient</h2>
      <p className="refpara">
        Operational and domain skills to build on purpose — the experience the job won't force but
        that makes your résumé and your exit credible. Click a status chip to cycle it.
      </p>

      <div className="row3" style={{ alignItems: "end" }}>
        <div>
          <label htmlFor="s-name">New skill</label>
          <input id="s-name" type="text" value={name} maxLength={200}
            onChange={(e) => setName(e.target.value)}
            placeholder="e.g. Splunk, Grafana, query prod DBs" />
        </div>
        <div>
          <label htmlFor="s-cat">Category</label>
          <input id="s-cat" type="text" value={category} maxLength={40}
            onChange={(e) => setCategory(e.target.value)}
            placeholder="e.g. Observability, Domain, Ops" />
        </div>
        <div>
          <button className="primary" onClick={add}>Add skill</button>
        </div>
      </div>

      {skills.length === 0 && <div className="empty">no skills yet — add your first above</div>}
      {skills.map((skill) => (
        <SkillRow key={skill.id} skill={skill} onCycle={() => cycle(skill)}
          onSaveNotes={saveNotes} onRemove={() => remove(skill)} />
      ))}

      {error && <div className="error" style={{ marginTop: 8 }}>{error}</div>}
    </div>
  );
}

interface RowProps {
  skill: WorkSkill;
  onCycle: () => void;
  onSaveNotes: (skill: WorkSkill, notes: string) => void;
  onRemove: () => void;
}

/** One skill row: status chip, name/category, and an expandable notes editor. */
function SkillRow({ skill, onCycle, onSaveNotes, onRemove }: RowProps) {
  const [expanded, setExpanded] = useState(false);
  const [notes, setNotes] = useState(skill.notes);

  // Re-sync the editor when the underlying skill changes (e.g. after a status
  // cycle re-fetches it), unless the user is mid-edit with unsaved text.
  useEffect(() => {
    setNotes((current) => (current === "" || current === skill.notes ? skill.notes : current));
  }, [skill.notes]);

  return (
    <div className={"plan-item" + (skill.status === "proficient" ? " is-done" : "")}>
      <div className="plan-row">
        <button className={"status-chip " + skill.status} onClick={onCycle}
          title="click to cycle status">
          {STATUS_LABEL[skill.status]}
        </button>
        <span className="plan-title" onClick={() => setExpanded(!expanded)}>
          {skill.name}
          {skill.category && <span className="muted"> · {skill.category}</span>}
        </span>
        <button className="linkish" onClick={() => setExpanded(!expanded)}>
          {expanded ? "▾" : "▸"}
        </button>
      </div>
      {expanded && (
        <div className="plan-detail">
          {skill.detail && <div className="refpara pre">{skill.detail}</div>}
          <label>My notes</label>
          <textarea value={notes} onChange={(e) => setNotes(e.target.value)}
            placeholder="e.g. built my first dashboard · ran a prod query with the on-call" />
          <div className="actions">
            <button onClick={() => onSaveNotes(skill, notes)}>Save notes</button>
            <button onClick={onRemove}>Delete</button>
          </div>
        </div>
      )}
    </div>
  );
}
