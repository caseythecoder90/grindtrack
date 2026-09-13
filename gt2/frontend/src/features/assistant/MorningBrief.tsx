import { useCallback, useEffect, useState } from "react";
import { errorMessage } from "../../lib/api";
import { todayISO } from "../../lib/dates";
import { draftMorningBrief, getMorningBrief, type MorningBrief as Brief } from "./assistantApi";

/**
 * This morning's brief, above the daily log.
 *
 * <p>The one thing the assistant does that a person sees without asking for it, so it has to earn
 * the space every day: three short pieces, read once, then the form. It is drafted at six by the
 * scheduler; the button is for a morning that changed, and it says what it costs because a click
 * that spends money should never look like a click that does not.
 *
 * <p>Absent is a quiet state, not an error. Before six, or with the assistant off, the card is a
 * single line and the day's log is where it always was.
 */
export default function MorningBrief() {
  const today = todayISO();
  const [brief, setBrief] = useState<Brief | null>(null);
  const [loaded, setLoaded] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    try {
      setBrief(await getMorningBrief(today));
    } catch {
      /* no brief is a normal morning; a failed fetch must not sit above the log as an error */
    } finally {
      setLoaded(true);
    }
  }, [today]);

  useEffect(() => {
    load();
  }, [load]);

  async function redraft() {
    setBusy(true);
    setError("");
    try {
      setBrief(await draftMorningBrief(today));
    } catch (e) {
      setError(errorMessage(e, "could not draft the brief"));
    } finally {
      setBusy(false);
    }
  }

  if (!loaded) return null;

  if (!brief) {
    return (
      <div className="brief brief-empty">
        <span className="muted small">no brief yet this morning</span>
        <button type="button" className="linkish" disabled={busy} onClick={redraft}>
          {busy ? "drafting…" : "draft one · ~2¢"}
        </button>
        {error && <div className="error">{error}</div>}
      </div>
    );
  }

  return (
    <div className="brief">
      <div className="brief-head">
        <span className="lbl">this morning</span>
        <span className="brief-when">
          {brief.generatedAt.slice(11, 16)} · ${brief.costUsd.toFixed(2)}
        </span>
      </div>
      {brief.draft.motivation && <p className="brief-motivation">{brief.draft.motivation}</p>}
      <p className="brief-headline">{brief.draft.headline}</p>
      <p className="brief-body">{brief.draft.today}</p>
      <p className="brief-suggestion">{brief.draft.suggestion}</p>
      <div className="brief-foot">
        <button type="button" className="linkish" disabled={busy} onClick={redraft}>
          {busy ? "drafting…" : "redraft · ~2¢"}
        </button>
      </div>
      {error && <div className="error">{error}</div>}
    </div>
  );
}
