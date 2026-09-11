
import { useCallback, useEffect, useState } from "react";
import WeekTotals from "../../components/WeekTotals";
import { errorMessage } from "../../lib/api";
import {
  generateReviewDraft,
  getAssistantStatus,
  getReviewDraft,
  type ReviewReport,
} from "../assistant/assistantApi";
import { getDays, getWeek, saveWeek } from "./trackingApi";
import { addDays, mondayOf, todayISO } from "../../lib/dates";
import type {DayLog} from "../../lib/types";
import { useAppResume } from "../../lib/resume";

const DOW = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

export default function Week() {
  const [weekStart, setWeekStart] = useState(mondayOf(todayISO()));
  const [days, setDays] = useState<DayLog[]>([]);
  const [summary, setSummary] = useState("");
  const [wins, setWins] = useState("");
  const [blockers, setBlockers] = useState("");
  const [adjustments, setAdjustments] = useState("");
  const [nextFocus, setNextFocus] = useState("");
  const [onTrack, setOnTrack] = useState<boolean | null>(null);
  const [toast, setToast] = useState(false);
  const [loadError, setLoadError] = useState("");
  /** Null while unknown; the panel renders nothing until the server has said the assistant is on. */
  const [assistantOn, setAssistantOn] = useState<boolean | null>(null);
  const [draft, setDraft] = useState<ReviewReport | null>(null);
  const [drafting, setDrafting] = useState(false);
  const [draftError, setDraftError] = useState("");

  /** Same reasoning as Today: an unreachable server says so rather than showing an empty week. */
  const load = useCallback(async () => {
    setLoadError("");
    // The draft is a bonus on this page: if it cannot load, the review form must not care.
    getReviewDraft(weekStart)
      .then(setDraft)
      .catch(() => setDraft(null));
    try {
      const end = addDays(weekStart, 6);
      setDays(await getDays(weekStart, end));
      const review = await getWeek(weekStart);
      setSummary(review?.summary ?? "");
      setWins(review?.wins ?? "");
      setBlockers(review?.blockers ?? "");
      setAdjustments(review?.adjustments ?? "");
      setNextFocus(review?.nextFocus ?? "");
      setOnTrack(review?.onTrack ?? null);
    } catch (e) {
      setLoadError(errorMessage(e, "could not load this week"));
    }
  }, [weekStart]);

  // Anything logged on another device since this screen loaded.
  useAppResume(() => load());

  useEffect(() => {
    load();
  }, [load]);

  const byDate = new Map(days.map((d) => [d.logDate, d]));

  useEffect(() => {
    getAssistantStatus()
      .then((st) => setAssistantOn(st.configured))
      .catch(() => setAssistantOn(false));
  }, []);

  /** The click that spends money — about three cents — and takes half a minute. */
  async function draftReview() {
    setDrafting(true);
    setDraftError("");
    try {
      setDraft(await generateReviewDraft(weekStart));
    } catch (e) {
      setDraftError(errorMessage(e, "could not draft the review"));
    } finally {
      setDrafting(false);
    }
  }

  /**
   * The accept step, and the only path from a draft to real data: six fields copied into the form
   * the user already owns, still editable, saved by the same button as a hand-written review.
   * The model never writes to weekly_reviews; this click is a person deciding.
   */
  function useDraft() {
    if (!draft) return;
    setSummary(draft.draft.summary);
    setWins(draft.draft.wins);
    setBlockers(draft.draft.blockers);
    setAdjustments(draft.draft.adjustments);
    setNextFocus(draft.draft.nextFocus);
    setOnTrack(draft.draft.onTrack);
  }

  async function save() {
    await saveWeek(weekStart, { summary, wins, blockers, adjustments, nextFocus, onTrack });
    setToast(true);
    setTimeout(() => setToast(false), 1600);
  }

  return (
    <div className="panel">
      <h2>week view</h2>
      <div className="weeknav">
        <button onClick={() => setWeekStart(addDays(weekStart, -7))}>◀</button>
        <span>{weekStart} → {addDays(weekStart, 6)}</span>
        <button onClick={() => setWeekStart(addDays(weekStart, 7))}>▶</button>
        <button onClick={() => setWeekStart(mondayOf(todayISO()))}>this week</button>
      </div>
      <WeekTotals weekStart={weekStart} />
      <table className="stacked">
        <thead>
          <tr><th style={{ width: 110 }}>day</th><th style={{ width: 60 }}>hrs</th>
            <th style={{ width: 180 }}>categories</th><th>what happened</th></tr>
        </thead>
        <tbody>
          {DOW.map((name, i) => {
            const date = addDays(weekStart, i);
            const d = byDate.get(date);
            return (
              <tr key={date} className={date === todayISO() ? "today-row" : ""}>
                <td className="num">{name} <span className="muted">{date.slice(5)}</span></td>
                <td className="num" data-label="hrs">{d ? d.hours.toFixed(1) : "—"}</td>
                <td className="muted" data-label="categories">{d?.categories.join(", ") ?? ""}</td>
                <td data-label="did">{(d?.did || d?.focus || "").slice(0, 140)}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
      <h2 style={{ marginTop: 24 }}>weekly review</h2>
      {assistantOn && (
        <div className="ai-draft">
          {!draft && (
            <div className="ai-draft-bar">
              <span className="muted small">No draft for this week yet.</span>
              <button type="button" onClick={draftReview} disabled={drafting}>
                {drafting ? "drafting — about half a minute…" : "Draft it for me"}
              </button>
            </div>
          )}
          {draft && (
            <>
              <div className="ai-draft-head">
                <span className="ai-draft-kicker">assistant draft</span>
                <span className={"badge " + (draft.draft.onTrack ? "badge-project" : "badge-cert")}>
                  {draft.draft.onTrack ? "on track" : "off track"}
                </span>
              </div>
              <div className="ai-draft-body">
                <p>{draft.draft.summary}</p>
                <p><b>Wins</b> {draft.draft.wins}</p>
                {draft.draft.blockers && <p><b>Blockers</b> {draft.draft.blockers}</p>}
                <p><b>Adjustments</b> {draft.draft.adjustments}</p>
                <p><b>Next week</b> {draft.draft.nextFocus}</p>
              </div>
              {/* A feature that spends money per click says what it spent, every time. */}
              <div className="ai-draft-cost">
                {draft.model} · {draft.inputTokens} in / {draft.outputTokens} out · $
                {draft.costUsd.toFixed(3)} · drafted {draft.generatedAt.slice(0, 16).replace("T", " ")}
              </div>
              <div className="actions" style={{ marginTop: 10 }}>
                <button type="button" className="primary" onClick={useDraft}>
                  Use as my review
                </button>
                <button type="button" onClick={draftReview} disabled={drafting}>
                  {drafting ? "redrafting…" : "Redraft"}
                </button>
              </div>
            </>
          )}
          {draftError && <div className="error">{draftError}</div>}
        </div>
      )}
      <div className="row">
        <div><label>Summary of the week</label>
          <textarea value={summary} onChange={(e) => setSummary(e.target.value)} /></div>
        <div><label>Wins</label>
          <textarea value={wins} onChange={(e) => setWins(e.target.value)} /></div>
      </div>
      <div className="row">
        <div><label>Blockers</label>
          <textarea value={blockers} onChange={(e) => setBlockers(e.target.value)} /></div>
        <div><label>Adjustments to the plan</label>
          <textarea value={adjustments} onChange={(e) => setAdjustments(e.target.value)} /></div>
      </div>
      <label>Next week's focus</label>
      <textarea value={nextFocus} onChange={(e) => setNextFocus(e.target.value)} />
      <label>On track for the quarter?</label>
      <div className="chips">
        <button type="button" className="chip" aria-pressed={onTrack === true}
          onClick={() => setOnTrack(onTrack === true ? null : true)}>yes</button>
        <button type="button" className="chip" aria-pressed={onTrack === false}
          onClick={() => setOnTrack(onTrack === false ? null : false)}>no — adjust</button>
      </div>
      {loadError && <div className="error">{loadError}</div>}
      <div className="actions">
        <button className="primary" onClick={save}>Save review</button>
        <span className={"toast" + (toast ? " show" : "")}>saved ✓</span>
      </div>
    </div>
  );
}
