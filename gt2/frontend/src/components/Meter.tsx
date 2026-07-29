import type { Scope } from "../lib/types";

interface Props {
  study: number;
  work: number;
  target: number;
  /** Which segments to draw. A study-only view shouldn't render an empty work bar. */
  scope: Scope;
  /** Taller variant for the primary tile. */
  large?: boolean;
}

/**
 * Hours against a weekly target, split into its study and work parts.
 *
 * The app's one recurring visual device: two tracks competing for the same finite
 * week. The bar spans whichever is bigger, the target or the total — so once you
 * pass the target the marker slides inward instead of the bar silently clamping,
 * and beating a target still looks like beating it.
 */
export default function Meter({ study, work, target, scope, large }: Props) {
  const total = (scope === "work" ? 0 : study) + (scope === "study" ? 0 : work);
  const span = Math.max(target, total, 0.1);
  const pct = (hours: number) => `${(hours / span) * 100}%`;

  return (
    <div className={"meter" + (large ? " large" : "")}
      role="img"
      aria-label={`${total.toFixed(1)} of ${target} hours`}>
      {scope !== "work" && study > 0 && (
        <i className="study" style={{ width: pct(study) }} title={`study ${study.toFixed(1)}h`} />
      )}
      {scope !== "study" && work > 0 && (
        <i className="work" style={{ width: pct(work) }} title={`work ${work.toFixed(1)}h`} />
      )}
      {total > target && (
        <span className="target" style={{ left: pct(target) }} title={`target ${target}h`} />
      )}
    </div>
  );
}
