# API reference

All request/response bodies are JSON. Authenticated endpoints require the `gt_access` cookie
(set by login/refresh); unauthenticated calls receive `401` with no body.

## Public

| Method | Path | Description |
|---|---|---|
| GET | `/api/public/stats` | Streak, total hours, days logged, and 26 weeks of `{date, hours}` for the landing heatmap. **No text fields are ever exposed here.** |

## Auth

| Method | Path | Body | Effect |
|---|---|---|---|
| POST | `/api/auth/login` | `{username, password, otp}` | Sets `gt_access` (15 min) + `gt_refresh` (14 d) cookies. 401 on any failure (deliberately does not say which factor failed). 429 after 5 attempts / 5 min / IP. |
| POST | `/api/auth/refresh` | – | Rotates the refresh token, sets fresh cookies. 401 if missing/expired/revoked; reuse of a rotated token additionally revokes all of the user's live tokens. |
| POST | `/api/auth/logout` | – | Revokes the refresh token server-side and expires both cookies. |
| GET | `/api/auth/me` | – | `{username}` if the access cookie is valid. |

## Tracking (authenticated)

| Method | Path | Notes |
|---|---|---|
| GET | `/api/days?from=YYYY-MM-DD&to=YYYY-MM-DD` | Ordered range of daily logs |
| GET | `/api/days/{date}` | Single day or `null` |
| PUT | `/api/days/{date}` | Upsert: `{hours, categories[], focus, did, wins, blockers, energy}`. Validates hours 0–24, energy 1–5. |
| DELETE | `/api/days/{date}` | Remove a day |
| GET | `/api/weeks/{date}` | Weekly review; any date normalizes to its Monday |
| PUT | `/api/weeks/{date}` | Upsert review: `{summary, wins, blockers, adjustments, nextFocus, onTrack}` |
| GET | `/api/stats` | `{totalHours, daysLogged, streak, weeks[12], categories[]}` — category hours split evenly across a day's tags |
| GET | `/api/export` | Full JSON dump as a download |

## Focus sessions (authenticated)

| Method | Path | Notes |
|---|---|---|
| POST | `/api/focus/sessions` | `{date, startedAt, durationMinutes, completed}`. Records a pomodoro session and **atomically adds its minutes to that day's hours** (rounded to 0.1 h, day capped at 24). `completed=false` marks an ended-early session; its partial minutes still count. |
| GET | `/api/focus/sessions?date=YYYY-MM-DD` | That day's sessions, ordered by start time |
