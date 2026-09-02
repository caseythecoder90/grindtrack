import type { FocusKind, PlanItem } from "../../lib/types";

interface Props {
  kind: FocusKind;
  planItemId: number | null;
  topic: string;
  /** Books, papers and modules still open — what a reading session can be filed against. */
  readable: PlanItem[];
  /** Topics used before, so a repo name stays spelled the same way and keeps grouping. */
  pastTopics: string[];
  onChange: (patch: { planItemId?: number | null; topic?: string }) => void;
}

/**
 * What this lunch session is going into.
 *
 * Reading picks a plan item — that link is what turns 40 minutes into "9.2h into DDIA".
 * Code review has no plan row to point at, so its subject is a typed repo and area, offered
 * from what you have typed before so "grindtrack" doesn't become three different subjects.
 */
export default function LunchSubject({
  kind,
  planItemId,
  topic,
  readable,
  pastTopics,
  onChange,
}: Props) {
  if (kind === "review") {
    return (
      <>
        <label htmlFor="lunch-repo">Reviewing</label>
        <input
          id="lunch-repo"
          list="lunch-past-topics"
          value={topic}
          placeholder="grindtrack — finance service"
          onChange={(e) => onChange({ planItemId: null, topic: e.target.value })}
        />
        <datalist id="lunch-past-topics">
          {pastTopics.map((t) => (
            <option key={t} value={t} />
          ))}
        </datalist>
      </>
    );
  }

  return (
    <>
      <label htmlFor="lunch-item">Reading</label>
      <select
        id="lunch-item"
        value={planItemId ?? ""}
        onChange={(e) => {
          const id = e.target.value ? Number(e.target.value) : null;
          const picked = readable.find((i) => i.id === id);
          // Snapshot the title alongside the id: the backend stores both so the
          // history stays readable if the workbook later renames or drops the item.
          onChange({ planItemId: id, topic: picked ? picked.title : "" });
        }}
      >
        <option value="">— something not in the plan —</option>
        {readable.map((item) => (
          <option key={item.id} value={item.id}>
            {item.type} · {item.title}
          </option>
        ))}
      </select>
      {planItemId === null && (
        <input
          aria-label="What are you reading"
          list="lunch-past-topics"
          value={topic}
          placeholder="Stripe blog — idempotency"
          onChange={(e) => onChange({ topic: e.target.value })}
        />
      )}
      <datalist id="lunch-past-topics">
        {pastTopics.map((t) => (
          <option key={t} value={t} />
        ))}
      </datalist>
    </>
  );
}
