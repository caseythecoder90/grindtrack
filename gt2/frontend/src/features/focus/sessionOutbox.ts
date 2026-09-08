/**
 * A completed focus block that could not be saved, kept until it can be.
 *
 * <p>A block is an hour of your life. Before this it was a red line of text and then
 * nothing: `recordSession` threw, the catch in FocusPage set an error string, and the
 * session was gone. The timer had already moved on to the next one, so the loss was
 * silent unless you happened to count the rows.
 *
 * <p>The window for that is not small. The timer makes no requests at all while it runs
 * and the access cookie lasts fifteen minutes, so every block longer than that ends with
 * a refresh — and any hiccup in that refresh landed on the one request that mattered.
 *
 * <p>localStorage rather than memory, because the reload that "fixes" the error would
 * otherwise be what destroys the evidence.
 */
import { AuthError, OfflineError } from "../../lib/api";
import type { FocusKind, FocusSession } from "../../lib/types";
import { getSessions, recordSession } from "./focusApi";

const KEY = "gt-focus-outbox";

/** Exactly the body POST /api/focus/sessions takes, so a replay is byte-for-byte the original. */
export interface PendingSession {
  date: string;
  startedAt: string;
  durationMinutes: number;
  completed: boolean;
  kind: FocusKind;
  planItemId: number | null;
  topic: string;
}

function read(): PendingSession[] {
  try {
    const parsed = JSON.parse(localStorage.getItem(KEY) ?? "[]");
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    // A corrupt queue must not take the focus tab down with it.
    return [];
  }
}

function write(sessions: PendingSession[]): void {
  try {
    localStorage.setItem(KEY, JSON.stringify(sessions));
  } catch {
    // Quota, or a private window that refuses to store. Nothing useful to do here, and
    // throwing would replace a saved session with a crash.
  }
}

export function pendingCount(): number {
  return read().length;
}

/** Idempotent: a session already queued is not queued twice. */
export function enqueue(session: PendingSession): void {
  const pending = read();
  if (pending.some((p) => sameSession(p, session))) return;
  write([...pending, session]);
}

/**
 * Two records of the same block.
 *
 * <p>Compared as instants rather than strings: the server round-trips startedAt through
 * an OffsetDateTime, so the text that comes back need not match the text that went out
 * even when they name the same moment.
 */
function sameSession(a: { startedAt: string }, b: { startedAt: string }): boolean {
  return new Date(a.startedAt).getTime() === new Date(b.startedAt).getTime();
}

/**
 * Try to save everything queued. Returns how many landed.
 *
 * <p>Reads back before it writes. A queued session may already be on the server: the
 * request that "failed" can have been committed with only its response lost, which is
 * exactly what a pod replaced mid-deploy does. Posting it again would double-count the
 * hours — and inflating the study total is a worse failure than the one being fixed.
 */
export async function flush(): Promise<number> {
  const pending = read();
  if (pending.length === 0) return 0;

  const kept: PendingSession[] = [];
  let saved = 0;
  for (let i = 0; i < pending.length; i++) {
    const session = pending[i];
    try {
      if (!(await alreadyOnServer(session))) {
        await recordSession(session);
      }
      saved++;
    } catch (e) {
      if (e instanceof OfflineError || e instanceof AuthError) {
        // Still no usable connection, or the session really has ended. Keep this one and
        // everything after it, in order, and stop asking.
        kept.push(...pending.slice(i));
        break;
      }
      // A 4xx: the server has looked at this body and refused it, and will again. Holding
      // it forever would mean retrying on every resume for the rest of the fortnight.
      console.warn("dropping a queued focus session the server rejected", e);
    }
  }
  write(kept);
  return saved;
}

async function alreadyOnServer(session: PendingSession): Promise<boolean> {
  const existing: FocusSession[] = await getSessions(session.date, session.kind);
  return existing.some((s) => sameSession(s, session));
}
