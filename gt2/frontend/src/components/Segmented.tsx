interface Option<T extends string> {
  value: T;
  label: string;
  /** Optional modifier class, so a selected option can carry its data colour. */
  tone?: string;
}

interface Props<T extends string> {
  /** Names the group for screen readers; not rendered. */
  label: string;
  options: Option<T>[];
  value: T;
  onChange: (value: T) => void;
}

/**
 * A segmented control for picking one of a few mutually exclusive views.
 *
 * Real buttons, so it is keyboard-reachable and announces its state — unlike the
 * `.chip` spans used elsewhere for multi-select values. Filters and values look
 * different here on purpose: a chip sets a value on the record you are editing,
 * this switches what you are looking at.
 */
export default function Segmented<T extends string>({ label, options, value, onChange }: Props<T>) {
  return (
    <div className="seg" role="group" aria-label={label}>
      {options.map((o) => (
        <button
          key={o.value}
          type="button"
          className={o.tone ?? ""}
          aria-pressed={value === o.value}
          onClick={() => onChange(o.value)}
        >
          {o.label}
        </button>
      ))}
    </div>
  );
}
