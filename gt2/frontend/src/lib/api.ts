/**
 * Fetch wrapper implementing the SPA side of the access/refresh pattern:
 * on a 401, try POST /api/auth/refresh once (rotates the refresh cookie and
 * mints a new access cookie), then replay the original request.
 *
 * The two failures below are kept apart because their remedies are opposite.
 * They used to be one: `refreshOnce` returned `r.ok`, so a 502 from a pod being
 * replaced mid-deploy, a 503, or a laptop whose wifi had not come back yet all
 * reported themselves as an expired session — and the app dropped to the login
 * screen over something that fixed itself in seconds. Nothing had expired; the
 * refresh token is good for a fortnight.
 */

/** The session really is over. The only cure is logging in again. */
export class AuthError extends Error {}

/**
 * The request never got an answer worth acting on: the network is down, the
 * server is between pods, or a gateway answered in its place. Nothing is wrong
 * with the session and nothing needs the user's attention beyond waiting.
 */
export class OfflineError extends Error {}

const OFFLINE_MESSAGE = "could not reach the server";

/**
 * Statuses that mean "not now" rather than "no": a proxy between the browser and
 * the app answered. A plain 500 is deliberately excluded — that is the app itself
 * failing, and its message should reach the user rather than be dressed up as a
 * network blip.
 */
function unavailable(status: number): boolean {
  return status === 502 || status === 503 || status === 504;
}

async function rawFetch(path: string, init?: RequestInit): Promise<Response> {
  return fetch(path, { credentials: "same-origin", ...init });
}

/** A transport failure is not an application answer, so it never reaches the caller as one. */
async function send(path: string, init?: RequestInit): Promise<Response> {
  try {
    return await rawFetch(path, init);
  } catch {
    throw new OfflineError(OFFLINE_MESSAGE);
  }
}

type Refreshed = "ok" | "expired" | "unavailable";

/**
 * Deduped refresh: concurrent 401s share one in-flight attempt. A refresh usually
 * hands the same session token back, and when it does rotate, the server forgives
 * the loser of a race for a day -- but sending one request instead of two is still
 * the right thing to do.
 */
let refreshInFlight: Promise<Refreshed> | null = null;

function refreshOnce(): Promise<Refreshed> {
  if (!refreshInFlight) {
    refreshInFlight = rawFetch("/api/auth/refresh", { method: "POST" })
      .then((r): Refreshed => (r.ok ? "ok" : r.status === 401 ? "expired" : "unavailable"))
      .catch((): Refreshed => "unavailable")
      .finally(() => {
        refreshInFlight = null;
      });
  }
  return refreshInFlight;
}

/**
 * Mint a fresh access cookie before the current one lapses.
 *
 * <p>For the focus timer, which runs for an hour without making a single request
 * while the access cookie lasts thirty minutes. See useFocusTimer.
 */
export async function keepSessionAlive(): Promise<boolean> {
  return (await refreshOnce()) === "ok";
}

export async function api<T>(path: string, init?: RequestInit): Promise<T> {
  let res = await send(path, init);
  // A 401 from login means bad credentials, not an expired session — let its
  // error body reach the login form instead of attempting a refresh.
  if (res.status === 401 && path !== "/api/auth/login") {
    const outcome = await refreshOnce();
    // Only the refresh endpoint itself answering 401 means the session is over.
    if (outcome === "expired") throw new AuthError("session expired");
    if (outcome === "unavailable") throw new OfflineError(OFFLINE_MESSAGE);
    res = await send(path, init);
    if (res.status === 401) throw new AuthError("session expired");
  }
  if (unavailable(res.status)) throw new OfflineError(OFFLINE_MESSAGE);
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body.error ?? res.statusText);
  }
  // "No entry yet" endpoints (e.g. GET /api/days/<unlogged date>) return 200
  // with an empty body; surface that as null rather than a JSON parse error.
  const text = await res.text();
  return (text ? JSON.parse(text) : null) as T;
}

export function jsonInit(method: string, body: unknown): RequestInit {
  return {
    method,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  };
}

/**
 * The message to show a user when a request failed.
 *
 * <p>This exact ternary appeared fifty-two times across nineteen components, each with its own
 * fallback string. The fallback is the argument because it is the only part that legitimately
 * differs -- "could not load your budget" is more use than "something went wrong".
 */
export function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback;
}
