/**
 * Local-timezone date helpers. All dates are exchanged with the API as
 * YYYY-MM-DD strings in the user's local timezone (never UTC — a late-night
 * log should land on the local day).
 */

function toISO(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

export function todayISO(): string {
  return toISO(new Date());
}

/** The Monday on or before the given date (weeks start Monday). */
export function mondayOf(iso: string): string {
  const d = new Date(iso + "T00:00:00");
  d.setDate(d.getDate() - ((d.getDay() + 6) % 7));
  return toISO(d);
}

export function addDays(iso: string, n: number): string {
  const d = new Date(iso + "T00:00:00");
  d.setDate(d.getDate() + n);
  return toISO(d);
}

/**
 * Whole days from a YYYY-MM-DD to today, both taken at local midnight.
 *
 * <p>Normalising both ends to midnight is the whole point: subtracting raw
 * timestamps makes "yesterday evening" 0 days ago before noon and 1 after, so a
 * card that says how long it has been would change while you looked at it.
 */
export function daysSince(iso: string): number {
  const then = new Date(iso + "T00:00:00");
  const today = new Date(todayISO() + "T00:00:00");
  return Math.round((today.getTime() - then.getTime()) / 86_400_000);
}

/** The first day of a month, as YYYY-MM. The calendar grid's unit. */
export function monthOf(iso: string): string {
  return iso.slice(0, 7);
}

/** Shifts a YYYY-MM by n months, staying in local time. */
export function addMonths(month: string, n: number): string {
  const [y, m] = month.split("-").map(Number);
  const d = new Date(y, m - 1 + n, 1);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
}

/**
 * The grid for a month: six weeks of YYYY-MM-DD starting on a Monday.
 *
 * <p>Always six rows, never five-or-six. A grid that changes height when you page
 * from September to October moves everything below it, and the day sheet is below it.
 */
export function monthGrid(month: string): string[] {
  const first = `${month}-01`;
  const start = mondayOf(first);
  return Array.from({ length: 42 }, (_, i) => addDays(start, i));
}

/** HH:mm from the API's HH:mm:ss, for display. */
export function shortTime(time: string | null): string {
  return time ? time.slice(0, 5) : "";
}
