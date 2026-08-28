import { useCallback, useEffect, useState } from "react";
import { errorMessage } from "../../lib/api";
import { completeIdea, createIdea, deleteIdea, getIdeas } from "./relationshipApi";
import type { Effort, Idea, IdeaKind } from "../../lib/types";
import { EFFORT_LABEL, IDEA_LABEL } from "./kinds";

const KINDS: IdeaKind[] = ["GESTURE", "DATE", "GIFT"];
const EFFORTS: Effort[] = ["SMALL", "MEDIUM", "BIG"];

/**
 * Ideas you had on a good day, kept for a day when you have none.
 *
 * <p>Nobody is short of goodwill on a Tuesday evening; they are short of a specific thing to do.
 * So the list leads with whatever takes the least effort, and acting on something logs it and
 * takes it off the list — which is what stops this becoming a graveyard of things already given.
 */
export default function IdeasPanel({ onChange }: { onChange: () => void }) {
  const [ideas, setIdeas] = useState<Idea[]>([]);
  const [filter, setFilter] = useState<IdeaKind | "ALL">("ALL");
  const [adding, setAdding] = useState(false);
  const [title, setTitle] = useState("");
  const [kind, setKind] = useState<IdeaKind>("GESTURE");
  const [effort, setEffort] = useState<Effort>("SMALL");
  const [occasion, setOccasion] = useState("");
  const [cost, setCost] = useState("");
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    try {
      setIdeas(await getIdeas());
    } catch (e) {
      setError(errorMessage(e, "could not load your ideas"));
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  async function add() {
    if (!title.trim()) return;
    setError("");
    try {
      await createIdea({
        kind,
        title,
        detail: "",
        occasion: occasion || null,
        estCost: cost ? Number(cost) : null,
        effort,
      });
      setTitle("");
      setOccasion("");
      setCost("");
      setAdding(false);
      load();
    } catch (e) {
      setError(errorMessage(e, "could not save that idea"));
    }
  }

  async function done(idea: Idea) {
    setError("");
    try {
      await completeIdea(idea.id);
      load();
      onChange();
    } catch (e) {
      setError(errorMessage(e, "could not mark that done"));
    }
  }

  async function remove(idea: Idea) {
    setError("");
    try {
      await deleteIdea(idea.id);
      load();
    } catch (e) {
      setError(errorMessage(e, "could not remove that"));
    }
  }

  const shown = filter === "ALL" ? ideas : ideas.filter((i) => i.kind === filter);

  return (
    <section className="ideas">
      <div className="section-head">
        <h3>ideas</h3>
        <div>
          <div className="seg">
            {(["ALL", ...KINDS] as const).map((k) => (
              <button
                key={k}
                type="button"
                aria-pressed={filter === k}
                onClick={() => setFilter(k)}
              >
                {k === "ALL" ? "all" : IDEA_LABEL[k]}
              </button>
            ))}
          </div>
          <button type="button" onClick={() => setAdding((v) => !v)}>
            {adding ? "cancel" : "+ idea"}
          </button>
        </div>
      </div>

      {error && <p className="error">{error}</p>}

      {adding && (
        <div className="account-form">
          <input
            placeholder="what is it"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
          />
          <select value={kind} onChange={(e) => setKind(e.target.value as IdeaKind)}>
            {KINDS.map((k) => (
              <option key={k} value={k}>
                {IDEA_LABEL[k]}
              </option>
            ))}
          </select>
          <select value={effort} onChange={(e) => setEffort(e.target.value as Effort)}>
            {EFFORTS.map((e) => (
              <option key={e} value={e}>
                {EFFORT_LABEL[e]}
              </option>
            ))}
          </select>
          <input
            placeholder="occasion (optional)"
            value={occasion}
            onChange={(e) => setOccasion(e.target.value)}
          />
          <input
            type="number"
            step="10"
            placeholder="cost"
            value={cost}
            onChange={(e) => setCost(e.target.value)}
          />
          <button type="button" onClick={add}>
            add
          </button>
        </div>
      )}

      {shown.length === 0 ? (
        <p className="muted small">
          Nothing saved yet. The useful ones to write down are the small ones — the things you
          think of when you are feeling generous and forget by the time it would matter.
        </p>
      ) : (
        <table className="txn-table ideas-table">
          <tbody>
            {shown.map((i) => (
              <tr key={i.id}>
                <td>
                  {i.title}
                  <div className="muted small">
                    {IDEA_LABEL[i.kind]}
                    {i.effort && ` · ${EFFORT_LABEL[i.effort]}`}
                    {i.occasion && ` · ${i.occasion}`}
                    {i.estCost != null && ` · ~$${i.estCost}`}
                  </div>
                </td>
                <td className="num">
                  <button type="button" className="ghost" onClick={() => done(i)}>
                    did it
                  </button>
                  <button type="button" className="ghost" onClick={() => remove(i)}>
                    ×
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
