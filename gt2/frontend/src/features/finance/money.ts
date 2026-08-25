/** Formatting helpers for the finance tab. Money is displayed, never computed, here. */

const USD = new Intl.NumberFormat("en-US", {
  style: "currency",
  currency: "USD",
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

const USD_WHOLE = new Intl.NumberFormat("en-US", {
  style: "currency",
  currency: "USD",
  maximumFractionDigits: 0,
});

export function money(amount: number): string {
  return USD.format(amount);
}

/** For big headline numbers where cents are noise: $64,778 rather than $64,777.81. */
export function moneyWhole(amount: number): string {
  return USD_WHOLE.format(amount);
}

/** Signed, with an explicit + so income reads as income at a glance. */
export function signed(amount: number): string {
  return `${amount > 0 ? "+" : ""}${USD.format(amount)}`;
}

/**
 * Months of runway at a given monthly rate. Returns null when the goal is met or the
 * rate is not positive, so callers can show "reached" rather than Infinity.
 */
export function monthsToGoal(remaining: number, perMonth: number): number | null {
  if (remaining <= 0) return null;
  if (perMonth <= 0) return null;
  return Math.ceil(remaining / perMonth);
}

/** "Mar 2029" from a month offset, for projecting a goal date. */
export function monthsFromNow(months: number): string {
  const d = new Date();
  d.setDate(1);
  d.setMonth(d.getMonth() + months);
  return d.toLocaleDateString("en-US", { month: "short", year: "numeric" });
}
