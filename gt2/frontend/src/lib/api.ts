/**
 * Fetch wrapper implementing the SPA side of the access/refresh pattern:
 * on a 401, try POST /api/auth/refresh once (rotates the refresh cookie and
 * mints a new access cookie), then replay the original request. If refresh
 * also fails, surface AuthError so the app can drop to the login screen.
 */
export class AuthError extends Error {}

async function rawFetch(path: string, init?: RequestInit): Promise<Response> {
  return fetch(path, { credentials: "same-origin", ...init });
}

/**
 * Deduped refresh: concurrent 401s share one in-flight attempt. Refresh tokens
 * are single-use (rotation), so two parallel refresh calls would race — the
 * loser presents an already-rotated token and gets logged out.
 */
let refreshInFlight: Promise<boolean> | null = null;

function refreshOnce(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = rawFetch("/api/auth/refresh", { method: "POST" })
      .then((r) => r.ok)
      .finally(() => {
        refreshInFlight = null;
      });
  }
  return refreshInFlight;
}

export async function api<T>(path: string, init?: RequestInit): Promise<T> {
  let res = await rawFetch(path, init);
  // A 401 from login means bad credentials, not an expired session — let its
  // error body reach the login form instead of attempting a refresh.
  if (res.status === 401 && path !== "/api/auth/login") {
    if (!(await refreshOnce())) throw new AuthError("session expired");
    res = await rawFetch(path, init);
    if (res.status === 401) throw new AuthError("session expired");
  }
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
