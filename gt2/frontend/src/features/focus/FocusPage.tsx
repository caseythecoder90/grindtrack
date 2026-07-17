import { useCallback, useEffect, useRef, useState } from "react";
import { api, jsonInit } from "../../lib/api";
import { todayISO } from "../../lib/dates";
import { FOCUS_DEFAULTS, type FocusSession } from "../../lib/types";

interface Props {
  onLogged: () => void;
}

type Phase = "idle" | "focus" | "break" | "done";

interface Config {
  sessions: number;
  focusMin: number;
  breakMin: number;
}

interface TimerState {
  phase: Phase;
  sessionIndex: number; // 0-based index of the current focus session
  endsAt: number | null; // epoch ms while running; null when paused/idle/done
  remainingMs: number | null; // set while paused
  startedAt: string | null; // ISO start of the current focus session
  config: Config;
}

const STORAGE_KEY = "gt-focus-timer-v1";

function idleState(config: Config): TimerState {
  return { phase: "idle", sessionIndex: 0, endsAt: null, remainingMs: null, startedAt: null, config };
}

function loadState(): TimerState {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) return JSON.parse(raw) as TimerState;
  } catch {
    /* corrupted state — start fresh */
  }
  return idleState({ ...FOCUS_DEFAULTS });
}

function mmss(ms: number): string {
  const total = Math.max(0, Math.round(ms / 1000));
  return `${Math.floor(total / 60)}:${String(total % 60).padStart(2, "0")}`;
}

function chime() {
  try {
    const ctx = new AudioContext();
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.frequency.value = 880;
    osc.connect(gain);
    gain.connect(ctx.destination);
    gain.gain.setValueAtTime(0.25, ctx.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 1.1);
    osc.start();
    osc.stop(ctx.currentTime + 1.1);
  } catch {
    /* no audio available */
  }
}

function notify(body: string) {
  if ("Notification" in window && Notification.permission === "granted") {
    new Notification("grindtrack", { body });
  }
}

/**
 * Pomodoro timer. Times are absolute (endsAt epoch ms) and persisted to localStorage,
 * so a reload, tab switch, or laptop sleep doesn't lose or drift the timer.
 * Every finished (or ended-early) focus session is POSTed to the backend, which also
 * adds its minutes to the day's hours — streak and heatmap update as you study.
 */
export default function FocusPage({ onLogged }: Props) {
  const [state, setState] = useState<TimerState>(loadState);
  const [nowMs, setNowMs] = useState(Date.now());
  const [sessions, setSessions] = useState<FocusSession[]>([]);
  const [error, setError] = useState("");

  const stateRef = useRef(state);
  stateRef.current = state;

  useEffect(() => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  }, [state]);

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

  const finishFocus = useCallback(
    (ranFull: boolean) => {
      const s = stateRef.current;
      const plannedMs = s.config.focusMin * 60000;
      const remaining =
        s.endsAt !== null ? Math.max(0, s.endsAt - Date.now()) : (s.remainingMs ?? plannedMs);
      const minutes = ranFull ? s.config.focusMin : Math.floor((plannedMs - remaining) / 60000);
      if (s.startedAt) recordSession(s.startedAt, minutes, ranFull);
      chime();
      if (!ranFull) {
        setState(idleState(s.config));
      } else if (s.sessionIndex + 1 < s.config.sessions) {
        notify(`Session ${s.sessionIndex + 1} of ${s.config.sessions} done — break time`);
        setState({
          ...s,
          phase: "break",
          endsAt: Date.now() + s.config.breakMin * 60000,
          remainingMs: null,
          startedAt: null,
        });
      } else {
        notify(`All ${s.config.sessions} sessions done. Goal reached!`);
        setState({ ...s, phase: "done", endsAt: null, remainingMs: null, startedAt: null });
      }
    },
    [recordSession],
  );

  const beginFocus = useCallback((index: number) => {
    const s = stateRef.current;
    if (index > 0) {
      chime();
      notify(`Break over — session ${index + 1} of ${s.config.sessions}`);
    }
    setState({
      ...s,
      phase: "focus",
      sessionIndex: index,
      startedAt: new Date().toISOString(),
      endsAt: Date.now() + s.config.focusMin * 60000,
      remainingMs: null,
    });
  }, []);

  const running = (state.phase === "focus" || state.phase === "break") && state.endsAt !== null;

  // Tick + phase transitions. Absolute timestamps mean a missed tick can't drift the clock.
  useEffect(() => {
    if (!running) return;
    const id = setInterval(() => setNowMs(Date.now()), 500);
    return () => clearInterval(id);
  }, [running]);

  useEffect(() => {
    const s = stateRef.current;
    if (s.endsAt === null || Date.now() < s.endsAt) return;
    if (s.phase === "focus") finishFocus(true);
    else if (s.phase === "break") beginFocus(s.sessionIndex + 1);
  }, [nowMs, finishFocus, beginFocus]);

  function start() {
    if ("Notification" in window && Notification.permission === "default") {
      Notification.requestPermission();
    }
    beginFocus(0);
  }

  function pause() {
    setState((s) => ({ ...s, remainingMs: s.endsAt === null ? null : s.endsAt - Date.now(), endsAt: null }));
  }

  function resume() {
    setState((s) => ({ ...s, endsAt: Date.now() + (s.remainingMs ?? 0), remainingMs: null }));
  }

  function setConfig(patch: Partial<Config>) {
    setState((s) => ({ ...s, config: { ...s.config, ...patch } }));
  }

  const cfg = state.config;
  const phaseMs = (state.phase === "break" ? cfg.breakMin : cfg.focusMin) * 60000;
  const remainingMs =
    state.endsAt !== null ? state.endsAt - nowMs : (state.remainingMs ?? phaseMs);
  const paused = state.remainingMs !== null;
  const pct = Math.min(100, Math.max(0, Math.round((1 - remainingMs / phaseMs) * 100)));
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
                onChange={(e) => setConfig({ sessions: Math.max(1, Math.min(12, Number(e.target.value) || 1)) })} />
            </div>
            <div>
              <label htmlFor="f-len">Session minutes</label>
              <input id="f-len" type="number" min={5} max={180} value={cfg.focusMin}
                onChange={(e) => setConfig({ focusMin: Math.max(5, Math.min(180, Number(e.target.value) || 5)) })} />
            </div>
            <div>
              <label htmlFor="f-break">Break minutes</label>
              <input id="f-break" type="number" min={1} max={60} value={cfg.breakMin}
                onChange={(e) => setConfig({ breakMin: Math.max(1, Math.min(60, Number(e.target.value) || 1)) })} />
            </div>
          </div>
          <div className="actions">
            <button className="primary" onClick={start}>
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
          <div className={"timer" + (state.phase === "break" ? " break" : "")}>{mmss(remainingMs)}</div>
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
                  ? <button className="primary" onClick={resume}>Resume</button>
                  : <button onClick={pause}>Pause</button>}
                <button onClick={() => finishFocus(false)}>End early</button>
              </>
            )}
            {state.phase === "break" && (
              <button onClick={() => beginFocus(state.sessionIndex + 1)}>Skip break</button>
            )}
          </div>
        </>
      )}

      {state.phase === "done" && (
        <>
          <div className="timer-phase">goal reached</div>
          <div className="timer break">✓</div>
          <div className="actions focus-actions">
            <button className="primary" onClick={() => setState(idleState(cfg))}>New goal</button>
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
