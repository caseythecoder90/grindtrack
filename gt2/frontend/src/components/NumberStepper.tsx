import { useState, type KeyboardEvent } from "react";

interface Props {
  id: string;
  label: string;
  value: number;
  min: number;
  max: number;
  /** How much one press of − or + moves the value. Default 1. */
  step?: number;
  onChange: (value: number) => void;
  /** Read out to assistive tech as the unit, e.g. "sessions", "minutes". */
  unit?: string;
}

/**
 * A number field you can actually clear, with buttons a thumb can hit.
 *
 * Two failures motivated this, both on the focus timer's config row.
 *
 * **You could not erase the value.** The old inputs committed on every keystroke through
 * `Math.max(1, Math.min(12, Number(e.target.value) || 1))`. Clearing the field makes
 * `e.target.value` the empty string, `Number("")` is 0, and `0 || 1` is 1 — so the field
 * refilled itself with 1 the instant it was emptied, and typing "2" over a "1" was
 * impossible on a phone, where there is no drag-select to overwrite it. The fix is that
 * an in-progress edit is a *string* (`draft`), not a number: while you are typing, the
 * field shows exactly what you typed, empty included. It commits when the text happens to
 * be a valid number, and clamps on blur.
 *
 * **There was nothing to press.** `type="number"` renders spinner arrows on a desktop
 * browser and nothing at all on a phone, which is where this control is hardest to use.
 * So the arrows are real buttons that exist on every platform, sized by the 44px floor in
 * the `pointer: coarse` block. The input is `type="text"` with `inputMode="numeric"`:
 * that keeps the numeric keypad on iOS while leaving the value a plain string, which is
 * what makes the draft above possible. Losing the native arrows costs nothing, because
 * the whole point is that they were not there when it mattered — ArrowUp/ArrowDown are
 * wired back up by hand.
 */
export default function NumberStepper({
  id,
  label,
  value,
  min,
  max,
  step = 1,
  onChange,
  unit,
}: Props) {
  /** Non-null only while the field is being edited. Null means "show the committed value". */
  const [draft, setDraft] = useState<string | null>(null);

  const clamp = (n: number) => Math.min(max, Math.max(min, n));

  function nudge(by: number) {
    setDraft(null);
    onChange(clamp(value + by));
  }

  function type(next: string) {
    setDraft(next);
    // Commit only when the text is a whole number inside the range. Anything else —
    // empty, "1e3", a half-typed "-" — stays on screen as typed and changes nothing,
    // so the value behind it is never briefly wrong.
    if (/^\d+$/.test(next)) {
      const n = Number(next);
      if (n >= min && n <= max) onChange(n);
    }
  }

  /** Whatever is in the box is now final: clamp it, or fall back to the last good value. */
  function commit() {
    if (draft !== null && /^\d+$/.test(draft)) onChange(clamp(Number(draft)));
    setDraft(null);
  }

  function onKeyDown(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === "ArrowUp") {
      e.preventDefault();
      nudge(step);
    } else if (e.key === "ArrowDown") {
      e.preventDefault();
      nudge(-step);
    } else if (e.key === "Enter") {
      commit();
    }
  }

  const unitLabel = unit ? ` ${unit}` : "";

  return (
    <div className="stepper-field">
      <label htmlFor={id}>{label}</label>
      <div className="stepper">
        <button
          type="button"
          className="stepper-btn"
          onClick={() => nudge(-step)}
          disabled={value <= min}
          aria-label={`Decrease ${label.toLowerCase()}`}
        >
          −
        </button>
        <input
          id={id}
          type="text"
          inputMode="numeric"
          pattern="[0-9]*"
          value={draft ?? String(value)}
          onChange={(e) => type(e.target.value)}
          onBlur={commit}
          onKeyDown={onKeyDown}
          role="spinbutton"
          aria-valuenow={value}
          aria-valuemin={min}
          aria-valuemax={max}
          aria-valuetext={`${value}${unitLabel}`}
        />
        <button
          type="button"
          className="stepper-btn"
          onClick={() => nudge(step)}
          disabled={value >= max}
          aria-label={`Increase ${label.toLowerCase()}`}
        >
          +
        </button>
      </div>
    </div>
  );
}
