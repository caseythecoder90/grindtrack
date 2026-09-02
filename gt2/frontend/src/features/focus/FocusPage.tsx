import { useCallback, useEffect, useRef, useState } from "react";
import Segmented from "../../components/Segmented";
import { errorMessage } from "../../lib/api";
import { getReadingProgress, getSessions, recordSession as postSession, saveTakeaway } from "./focusApi";
import { todayISO } from "../../lib/dates";
// The plan is another feature's data, but a reading session files itself against a plan
// item — so the picker needs the list, and there is no second way to get it.
import { getPlan } from "../plan/planApi";
import {
  FOCUS_KIND_LABEL,
  LUNCH_DEFAULTS,
  type FocusKind,
  type FocusSession,
  type PlanItem,
  type ReadingProgress,
} from "../../lib/types";
import LunchSubject from "./LunchSubject";
import ReadingPanel from "./ReadingPanel";
import { useFocusTimer } from "./useFocusTimer";

interface Props {
  onLogged: () => void;
}

/** Books, papers and modules still open — the only plan types you read at a desk. */
const READABLE_TYPES = ["book", "paper", "module"];

function isLunch(kind: FocusKind): boolean {
  return kind === "reading" || kind === "review";
}

/**
 * Pomodoro timer screen. Timer mechanics live in timer.ts (pure state machine) and
 * useFocusTimer.ts (effect wiring); this component renders the timer and posts finished
 * sessions to the backend, which also adds their minutes to the day's hours.
 *
 * The two lunch kinds — reading and code review — additionally record what the session went
 * into and prompt for a takeaway once it ends. That prompt is the point: the reading is worth
 * roughly nothing without the three sentences afterwards.
 */
export default function FocusPage({ onLogged }: Props) {
  const [sessions, setSessions] = useState<FocusSession[]>([]);
  const [progress, setProgress] = useState<ReadingProgress | null>(null);
  const [readable, setReadable] = useState<PlanItem[]>([]);
  const [pending, setPending] = useState<FocusSession | null>(null);
  const [takeaway, setTakeaway] = useState("");
  const [error, setError] = useState("");

  // The timer keeps `record` for the life of the component, so it must not close over the
  // subject — a ref lets it read whatever is configured at the moment a session finishes.
  const subject = useRef<{ planItemId: number | null; topic: string }>({
    planItemId: null,
    topic: "",
  });

  const loadSessions = useCallback(async (k: FocusKind) => {
    try {
      setSessions(await getSessions(todayISO(), k));
    } catch {
      /* list is cosmetic; auth errors surface via the app shell */
    }
  }, []);

  const loadProgress = useCallback(async () => {
    try {
      setProgress(await getReadingProgress());
    } catch {
      /* the dashboard is a nudge, not the app */
    }
  }, []);

  /**
   * Must stay referentially stable: useFocusTimer keys its deadline effect off this callback,
   * and an identity that changes every render re-arms that effect every render. It takes the
   * kind as an argument and reads the subject from a ref for exactly that reason — closing over
   * either would put them in the dependency array and defeat the point.
   */
  const record = useCallback(
    async (startedAt: string, minutes: number, completed: boolean, k: FocusKind) => {
      if (minutes < 1) return;
      try {
        const saved = await postSession({
          date: todayISO(),
          startedAt,
          durationMinutes: minutes,
          completed,
          kind: k,
          planItemId: subject.current.planItemId,
          topic: subject.current.topic,
        });
        setError("");
        if (isLunch(k)) {
          setPending(saved);
          setTakeaway("");
          loadProgress();
        }
        onLogged();
        loadSessions(k);
      } catch (e) {
        setError(errorMessage(e, "could not save session"));
      }
    },
    [onLogged, loadSessions, loadProgress],
  );

  const timer = useFocusTimer(record);
  const { state, clock, paused, pct } = timer;
  const cfg = state.config;
  const kind = cfg.kind;
  subject.current = { planItemId: cfg.planItemId, topic: cfg.topic };

  async function submitTakeaway() {
    if (!pending) return;
    try {
      await saveTakeaway(pending.id, takeaway);
      setPending(null);
      setTakeaway("");
      loadProgress();
    } catch (e) {
      setError(errorMessage(e, "could not save that takeaway"));
    }
  }

  /** Switching to or from a lunch kind also switches the shape of the session. */
  function changeKind(next: FocusKind) {
    timer.setConfig(
      isLunch(next) && !isLunch(kind)
        ? { kind: next, ...LUNCH_DEFAULTS }
        : { kind: next },
    );
  }

  useEffect(() => {
    loadSessions(kind);
  }, [loadSessions, kind]);

  useEffect(() => {
    loadProgress();
    getPlan()
      .then((plan) =>
        setReadable(
          plan.items.filter((i) => READABLE_TYPES.includes(i.type) && i.status !== "done"),
        ),
      )
      .catch(() => {
        /* no plan imported yet — the freeform topic still works */
      });
  }, [loadProgress]);

  const focusedMin = sessions.reduce((sum, s) => sum + s.durationMinutes, 0);
  const pastTopics = progress ? progress.subjects.map((s) => s.label) : [];

  return (
    <>
      <div className="panel">
        <h2>focus timer</h2>

        {state.phase === "idle" && (
          <>
            <label>Focus for</label>
            <Segmented
              label="What this session counts as"
              value={kind}
              onChange={changeKind}
              options={[
                { value: "study", label: "study", tone: "study" },
                { value: "work", label: "work", tone: "work" },
                { value: "reading", label: "reading" },
                { value: "review", label: "code review" },
              ]}
            />

            {isLunch(kind) && (
              <LunchSubject
                kind={kind}
                planItemId={cfg.planItemId}
                topic={cfg.topic}
                readable={readable}
                pastTopics={pastTopics}
                onChange={(patch) => timer.setConfig(patch)}
              />
            )}

            <div className="row3">
              <div>
                <label htmlFor="f-sessions">Sessions</label>
                <input id="f-sessions" type="number" min={1} max={12} value={cfg.sessions}
                  onChange={(e) => timer.setConfig({ sessions: Math.max(1, Math.min(12, Number(e.target.value) || 1)) })} />
              </div>
              <div>
                <label htmlFor="f-len">Session minutes</label>
                <input id="f-len" type="number" min={5} max={180} value={cfg.focusMin}
                  onChange={(e) => timer.setConfig({ focusMin: Math.max(5, Math.min(180, Number(e.target.value) || 5)) })} />
              </div>
              <div>
                <label htmlFor="f-break">Break minutes</label>
                <input id="f-break" type="number" min={1} max={60} value={cfg.breakMin}
                  onChange={(e) => timer.setConfig({ breakMin: Math.max(1, Math.min(60, Number(e.target.value) || 1)) })} />
              </div>
            </div>
            <div className="actions">
              <button className="primary" onClick={timer.start}>
                Start {FOCUS_KIND_LABEL[kind]} · {cfg.sessions} × {cfg.focusMin}min
              </button>
            </div>
          </>
        )}

        {(state.phase === "focus" || state.phase === "break") && (
          <>
            <div className="timer-phase">
              {state.phase === "focus"
                ? `${FOCUS_KIND_LABEL[kind]} · session ${state.sessionIndex + 1} of ${cfg.sessions}${paused ? " · paused" : ""}`
                : "break — step away from the desk"}
            </div>
            {cfg.topic && <div className="muted small">{cfg.topic}</div>}
            <div className={"timer" + (state.phase === "break" ? " break" : "")}>{clock}</div>
            <div className="dots">
              {Array.from({ length: cfg.sessions }, (_, i) => (
                <span key={i}
                  className={"dot" + (i < state.sessionIndex || (i === state.sessionIndex && state.phase === "break") ? " done" : i === state.sessionIndex ? " now" : "")} />
              ))}
            </div>
            <div className="progress"><i style={{ width: `${pct}%` }} /></div>
            <div className="actions focus-actions">
              {state.phase === "focus" && (
                <>
                  {paused
                    ? <button className="primary" onClick={timer.resume}>Resume</button>
                    : <button onClick={timer.pause}>Pause</button>}
                  <button onClick={timer.endEarly}>End early</button>
                </>
              )}
              {state.phase === "break" && (
                <button onClick={timer.skipBreak}>Skip break</button>
              )}
            </div>
          </>
        )}

        {state.phase === "done" && (
          <>
            <div className="timer-phase">goal reached</div>
            <div className="timer break">✓</div>
            <div className="actions focus-actions">
              <button className="primary" onClick={timer.reset}>New goal</button>
            </div>
          </>
        )}

        {pending && (
          <div className="plan-detail">
            <label htmlFor="takeaway">
              Three sentences on {pending.topic || "that session"}
            </label>
            <textarea id="takeaway" value={takeaway} autoFocus
              placeholder="What surprised you? What does it change about how you'd build something?"
              onChange={(e) => setTakeaway(e.target.value)} />
            <div className="actions">
              <button className="primary" onClick={submitTakeaway} disabled={!takeaway.trim()}>
                Save takeaway
              </button>
              <button onClick={() => setPending(null)}>Skip</button>
            </div>
          </div>
        )}

        {error && <div className="error" style={{ marginTop: 10 }}>{error}</div>}

        <h2 style={{ marginTop: 24 }}>
          today's {FOCUS_KIND_LABEL[kind]} sessions · {(focusedMin / 60).toFixed(1)}h focused
        </h2>
        {sessions.length === 0 ? (
          <div className="empty">none yet — start the timer</div>
        ) : (
          <table>
            <thead>
              <tr><th style={{ width: 110 }}>started</th><th style={{ width: 80 }}>minutes</th><th>status</th></tr>
            </thead>
            <tbody>
              {sessions.map((s) => (
                <tr key={s.id}>
                  <td className="num">{new Date(s.startedAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}</td>
                  <td className="num">{s.durationMinutes}</td>
                  <td className={s.completed ? "" : "muted"}>
                    {s.topic || (s.completed ? "completed" : "ended early")}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {progress && <ReadingPanel progress={progress} />}
    </>
  );
}
