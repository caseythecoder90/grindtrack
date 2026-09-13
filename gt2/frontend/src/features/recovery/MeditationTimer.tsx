import { useEffect, useRef, useState } from "react";
import { prepareBell, type Bell } from "./bell";

interface Props {
  /** The length last used, from the settings row. */
  minutes: number;
  streak: number;
  doneToday: boolean;
  /** A sitting finished or cut short, to be logged. */
  onDone: (minutes: number, completed: boolean) => void;
}

/** The lengths offered as chips: the usual ones, plus whatever was last used. */
function chips(chosen: number): number[] {
  return [...new Set([5, 10, 15, 20, chosen])].sort((a, b) => a - b);
}

function clock(seconds: number): string {
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${m}:${s < 10 ? "0" : ""}${s}`;
}

/**
 * A timer of any length and a bell at the end. Quiet by design: no guided anything, no music,
 * no scores. The chips are the lengths people actually sit for; "any…" takes a number.
 *
 * The screen is kept awake while it runs, where the browser allows, so the bell is not missed
 * because the phone locked.
 */
export default function MeditationTimer({ minutes, streak, doneToday, onDone }: Props) {
  const [chosen, setChosen] = useState(minutes);
  const [custom, setCustom] = useState(false);
  const [endAt, setEndAt] = useState<number | null>(null);
  const [left, setLeft] = useState(minutes * 60);
  const bell = useRef<Bell | null>(null);
  const lock = useRef<{ release(): Promise<void> } | null>(null);
  const finished = useRef(false);

  // The settings row can change under us (another device) while nothing is running.
  useEffect(() => {
    if (endAt === null) {
      setChosen(minutes);
      setLeft(minutes * 60);
    }
  }, [minutes, endAt]);

  useEffect(() => {
    if (endAt === null) return;
    const tick = () => {
      const remaining = Math.max(0, Math.round((endAt - Date.now()) / 1000));
      setLeft(remaining);
      if (remaining === 0 && !finished.current) {
        finished.current = true;
        bell.current?.ring();
        setEndAt(null);
        onDone(chosen, true);
      }
    };
    tick();
    const id = window.setInterval(tick, 250);
    return () => window.clearInterval(id);
  }, [endAt, chosen, onDone]);

  async function start() {
    finished.current = false;
    bell.current?.close();
    bell.current = prepareBell();
    setLeft(chosen * 60);
    setEndAt(Date.now() + chosen * 60_000);
    try {
      const wl = (navigator as Navigator & { wakeLock?: { request(type: "screen"): Promise<{ release(): Promise<void> }> } }).wakeLock;
      lock.current = wl ? await wl.request("screen") : null;
    } catch {
      lock.current = null;
    }
  }

  function stop() {
    const elapsed = chosen * 60 - left;
    setEndAt(null);
    setLeft(chosen * 60);
    lock.current?.release().catch(() => {});
    lock.current = null;
    // A sitting cut short after a minute is still a sitting worth the record; a mis-tap is not.
    if (elapsed >= 60) onDone(chosen, false);
  }

  useEffect(
    () => () => {
      lock.current?.release().catch(() => {});
      bell.current?.close();
    },
    [],
  );

  const running = endAt !== null;

  return (
    <div className="rec-card rec-meditation">
      <div>
        <span className="rec-lbl">meditation</span>
        <div className="rec-timer" aria-live={running ? "off" : "polite"}>
          {clock(running ? left : chosen * 60)}
        </div>
        {running ? (
          <div className="rec-note">
            {doneToday ? "again today" : streak > 0 ? `${streak} days in a row` : "quiet · a bell at the end"}
          </div>
        ) : (
          <div className="rec-chips" role="group" aria-label="Length in minutes">
            {chips(chosen).map((n) => (
              <button
                key={n}
                type="button"
                className={n === chosen && !custom ? "on" : ""}
                aria-pressed={n === chosen && !custom}
                onClick={() => {
                  setChosen(n);
                  setLeft(n * 60);
                  setCustom(false);
                }}
              >
                {n}
              </button>
            ))}
            {custom ? (
              <input
                type="number"
                inputMode="numeric"
                min={1}
                max={180}
                aria-label="Minutes"
                autoFocus
                defaultValue={chosen}
                onChange={(e) => {
                  const n = Math.min(180, Math.max(1, Number(e.target.value) || 0));
                  if (n > 0) {
                    setChosen(n);
                    setLeft(n * 60);
                  }
                }}
                onBlur={() => setCustom(false)}
              />
            ) : (
              <button type="button" className="any" onClick={() => setCustom(true)}>
                any…
              </button>
            )}
          </div>
        )}
        {!running && (
          <div className="rec-note" style={{ marginTop: 6 }}>
            {doneToday ? "sat today ✓" : ""}
            {doneToday && streak > 0 ? " · " : ""}
            {streak > 0 ? `${streak} day${streak === 1 ? "" : "s"} in a row` : ""}
          </div>
        )}
      </div>
      {running ? (
        <button type="button" onClick={stop}>
          stop
        </button>
      ) : (
        <button type="button" className="rec-primary" onClick={start}>
          start
        </button>
      )}
    </div>
  );
}
