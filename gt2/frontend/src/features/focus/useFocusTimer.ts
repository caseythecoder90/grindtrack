import { useCallback, useEffect, useRef, useState } from "react";
import { FOCUS_DEFAULTS } from "../../lib/types";
import { chime, notify, requestNotifyPermission } from "./alerts";
import {
  FOCUS_TIMER_STORAGE_KEY,
  breakStarted,
  decodeState,
  elapsedFocusMinutes,
  focusStarted,
  goalReached,
  idleState,
  isLastSession,
  isPaused,
  isRunning,
  mmss,
  pausedState,
  progressPct,
  remainingMsAt,
  resumedState,
  withConfig,
  type TimerConfig,
  type TimerState,
} from "./timer";

export interface FocusTimer {
  state: TimerState;
  /** Time left in the current phase, formatted m:ss for display. */
  clock: string;
  paused: boolean;
  /** Percent of the current phase elapsed, 0–100. */
  pct: number;
  start: () => void;
  pause: () => void;
  resume: () => void;
  endEarly: () => void;
  skipBreak: () => void;
  /** Back to the idle config screen (after "done"). */
  reset: () => void;
  setConfig: (patch: Partial<TimerConfig>) => void;
}

/**
 * Effect wiring around the pure state machine in timer.ts: persistence to
 * localStorage, the 500ms display tick, deadline-driven phase transitions,
 * and chime/notification side effects.
 *
 * `onFocusSessionEnd` fires when a focus session finishes or is ended early
 * (never for breaks); pass a stable callback to avoid re-running effects.
 */
export function useFocusTimer(
  onFocusSessionEnd: (startedAt: string, minutes: number, completed: boolean) => void,
): FocusTimer {
  const [state, setState] = useState<TimerState>(
    () => decodeState(localStorage.getItem(FOCUS_TIMER_STORAGE_KEY)) ?? idleState({ ...FOCUS_DEFAULTS }),
  );
  const [nowMs, setNowMs] = useState(Date.now());

  const stateRef = useRef(state);
  stateRef.current = state;

  useEffect(() => {
    localStorage.setItem(FOCUS_TIMER_STORAGE_KEY, JSON.stringify(state));
  }, [state]);

  const finishFocus = useCallback(
    (ranFull: boolean) => {
      const s = stateRef.current;
      const minutes = ranFull ? s.config.focusMin : elapsedFocusMinutes(s, Date.now());
      if (s.startedAt) onFocusSessionEnd(s.startedAt, minutes, ranFull);
      chime();
      if (!ranFull) {
        setState(idleState(s.config));
      } else if (!isLastSession(s)) {
        notify(`Session ${s.sessionIndex + 1} of ${s.config.sessions} done — break time`);
        setState(breakStarted(s, Date.now()));
      } else {
        notify(`All ${s.config.sessions} sessions done. Goal reached!`);
        setState(goalReached(s));
      }
    },
    [onFocusSessionEnd],
  );

  const beginFocus = useCallback((index: number) => {
    const s = stateRef.current;
    if (index > 0) {
      chime();
      notify(`Break over — session ${index + 1} of ${s.config.sessions}`);
    }
    setState(focusStarted(s, index, Date.now(), new Date().toISOString()));
  }, []);

  // Display tick while running. Absolute timestamps mean a missed tick can't
  // drift the clock.
  const running = isRunning(state);
  useEffect(() => {
    if (!running) return;
    const id = setInterval(() => setNowMs(Date.now()), 500);
    return () => clearInterval(id);
  }, [running]);

  // Phase transitions once the deadline passes. Also fires on mount, so a
  // timer restored from localStorage that expired while the tab was closed
  // resolves immediately.
  useEffect(() => {
    const s = stateRef.current;
    if (s.endsAt === null || Date.now() < s.endsAt) return;
    if (s.phase === "focus") finishFocus(true);
    else if (s.phase === "break") beginFocus(s.sessionIndex + 1);
  }, [nowMs, finishFocus, beginFocus]);

  return {
    state,
    clock: mmss(remainingMsAt(state, nowMs)),
    paused: isPaused(state),
    pct: progressPct(state, nowMs),
    start: () => {
      requestNotifyPermission();
      beginFocus(0);
    },
    pause: () => setState((s) => pausedState(s, Date.now())),
    resume: () => setState((s) => resumedState(s, Date.now())),
    endEarly: () => finishFocus(false),
    skipBreak: () => beginFocus(stateRef.current.sessionIndex + 1),
    reset: () => setState(idleState(stateRef.current.config)),
    setConfig: (patch) => setState((s) => withConfig(s, patch)),
  };
}
