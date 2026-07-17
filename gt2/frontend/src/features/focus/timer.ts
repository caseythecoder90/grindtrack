/**
 * Pure pomodoro state machine — no React, no browser APIs. Callers pass the
 * current time (epoch ms), so every transition and derivation is deterministic
 * and unit-testable without mounting a component.
 *
 * Times are absolute (endsAt epoch ms), so a missed tick, tab switch, or
 * laptop sleep can't lose or drift the timer.
 */

export type Phase = "idle" | "focus" | "break" | "done";

export interface TimerConfig {
  sessions: number;
  focusMin: number;
  breakMin: number;
}

export interface TimerState {
  phase: Phase;
  sessionIndex: number; // 0-based index of the current focus session
  endsAt: number | null; // epoch ms while running; null when paused/idle/done
  remainingMs: number | null; // set while paused
  startedAt: string | null; // ISO start of the current focus session
  config: TimerConfig;
}

/**
 * localStorage key for the persisted timer. TimerState is the on-disk shape —
 * keep both stable so a running timer survives reloads and deploys.
 */
export const FOCUS_TIMER_STORAGE_KEY = "gt-focus-timer-v1";

export function idleState(config: TimerConfig): TimerState {
  return { phase: "idle", sessionIndex: 0, endsAt: null, remainingMs: null, startedAt: null, config };
}

/** Parse a persisted TimerState; null for missing or corrupted input. */
export function decodeState(raw: string | null): TimerState | null {
  if (!raw) return null;
  try {
    return JSON.parse(raw) as TimerState;
  } catch {
    return null;
  }
}

// ---------------------------------------------------------------------------
// Transitions. Each returns a new state; none touch the clock themselves.
// ---------------------------------------------------------------------------

export function focusStarted(
  s: TimerState,
  sessionIndex: number,
  nowMs: number,
  startedAt: string,
): TimerState {
  return {
    ...s,
    phase: "focus",
    sessionIndex,
    startedAt,
    endsAt: nowMs + s.config.focusMin * 60000,
    remainingMs: null,
  };
}

export function breakStarted(s: TimerState, nowMs: number): TimerState {
  return {
    ...s,
    phase: "break",
    endsAt: nowMs + s.config.breakMin * 60000,
    remainingMs: null,
    startedAt: null,
  };
}

export function goalReached(s: TimerState): TimerState {
  return { ...s, phase: "done", endsAt: null, remainingMs: null, startedAt: null };
}

export function pausedState(s: TimerState, nowMs: number): TimerState {
  return { ...s, remainingMs: s.endsAt === null ? null : s.endsAt - nowMs, endsAt: null };
}

export function resumedState(s: TimerState, nowMs: number): TimerState {
  return { ...s, endsAt: nowMs + (s.remainingMs ?? 0), remainingMs: null };
}

export function withConfig(s: TimerState, patch: Partial<TimerConfig>): TimerState {
  return { ...s, config: { ...s.config, ...patch } };
}

/** True when the last configured focus session has been completed. */
export function isLastSession(s: TimerState): boolean {
  return s.sessionIndex + 1 >= s.config.sessions;
}

// ---------------------------------------------------------------------------
// Derivations.
// ---------------------------------------------------------------------------

export function isRunning(s: TimerState): boolean {
  return (s.phase === "focus" || s.phase === "break") && s.endsAt !== null;
}

export function isPaused(s: TimerState): boolean {
  return s.remainingMs !== null;
}

/** Planned length of the displayed phase (idle/done fall back to the focus length). */
export function phaseDurationMs(s: TimerState): number {
  return (s.phase === "break" ? s.config.breakMin : s.config.focusMin) * 60000;
}

/**
 * Time left in the current phase. May be slightly negative in the window
 * between the deadline and the tick that handles it; mmss clamps for display.
 */
export function remainingMsAt(s: TimerState, nowMs: number): number {
  if (s.endsAt !== null) return s.endsAt - nowMs;
  return s.remainingMs ?? phaseDurationMs(s);
}

/** Percent of the current phase elapsed, clamped to 0–100 for the progress bar. */
export function progressPct(s: TimerState, nowMs: number): number {
  const pct = Math.round((1 - remainingMsAt(s, nowMs) / phaseDurationMs(s)) * 100);
  return Math.min(100, Math.max(0, pct));
}

/** Whole minutes of focus elapsed so far — what gets logged on an early end. */
export function elapsedFocusMinutes(s: TimerState, nowMs: number): number {
  const plannedMs = s.config.focusMin * 60000;
  const remaining =
    s.endsAt !== null ? Math.max(0, s.endsAt - nowMs) : (s.remainingMs ?? plannedMs);
  return Math.floor((plannedMs - remaining) / 60000);
}

/** Format milliseconds as m:ss, clamping negatives to 0:00. */
export function mmss(ms: number): string {
  const total = Math.max(0, Math.round(ms / 1000));
  return `${Math.floor(total / 60)}:${String(total % 60).padStart(2, "0")}`;
}
