import { useCallback, useEffect, useState } from "react";
import { api, jsonInit } from "../../lib/api";
import { todayISO } from "../../lib/dates";
import type { FocusSession } from "../../lib/types";
import { useFocusTimer } from "./useFocusTimer";

interface Props {
  onLogged: () => void;
}

/**
 * Pomodoro timer screen. Timer mechanics live in timer.ts (pure state
 * machine) and useFocusTimer.ts (effect wiring); this component renders the
 * timer and posts finished sessions to the backend, which also adds their
 * minutes to the day's hours — streak and heatmap update as you study.
 */
export default function FocusPage({ onLogged }: Props) {
  const [sessions, setSessions] = useState<FocusSession[]>([]);
  const [error, setError] = useState("");

  const loadSessions = useCallback(async () => {
    try {
      setSessions(await api<FocusSession[]>(`/api/focus/sessions?date=${todayISO()}`));
    } catch {
      /* list is cosmetic; auth errors surface via the app shell */
    }
  }, []);

  useEffect(() => {
    loadSessions();
  }, [loadSessions]);

  const recordSession = useCallback(
    async (startedAt: string, minutes: number, completed: boolean) => {
      if (minutes < 1) return;
      try {
        await api(
          "/api/focus/sessions",
          jsonInit("POST", { date: todayISO(), startedAt, durationMinutes: minutes, completed }),
        );
        setError("");
        onLogged();
        loadSessions();
      } catch (e) {
        setError(e instanceof Error ? e.message : "could not save session");
      }
    },
    [onLogged, loadSessions],
  );

  const timer = useFocusTimer(recordSession);
  const { state, clock, paused, pct } = timer;
  const cfg = state.config;
  const focusedMin = sessions.reduce((sum, s) => sum + s.durationMinutes, 0);

  return (
    <div className="panel">
      <h2>focus timer</h2>

      {state.phase === "idle" && (
        <>
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
              Start · {cfg.sessions} × {cfg.focusMin}min
            </button>
          </div>
        </>
      )}

      {(state.phase === "focus" || state.phase === "break") && (
        <>
          <div className="timer-phase">
            {state.phase === "focus"
              ? `session ${state.sessionIndex + 1} of ${cfg.sessions}${paused ? " · paused" : ""}`
              : "break — step away from the desk"}
          </div>
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

      {error && <div className="error" style={{ marginTop: 10 }}>{error}</div>}

      <h2 style={{ marginTop: 24 }}>today's sessions · {(focusedMin / 60).toFixed(1)}h focused</h2>
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
                <td className={s.completed ? "" : "muted"}>{s.completed ? "completed" : "ended early"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
