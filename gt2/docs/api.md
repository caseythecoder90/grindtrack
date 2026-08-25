# API reference

All request/response bodies are JSON. Authenticated endpoints require the `gt_access` cookie
(set by login/refresh); unauthenticated calls receive `401` with no body.

## Public

| Method | Path | Description |
|---|---|---|
| GET | `/api/public/stats` | Streak, total hours, days logged, and 26 weeks of `{date, hours}` for the landing heatmap. **No text fields are ever exposed here**, and it serves the **study scope only** — day-job hours and project names stay behind the authenticated API. |

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
| GET | `/api/stats` | `{study, work, all}`, each a scope of `{totalHours, daysLogged, streak, daysThisMonth, weeks[12], categories[], days[]}`. All three are computed per request so the UI switches scope without refetching. `days[]` is the 26-week heatmap series. Category hours are split evenly across a day's tags; in `all` the two scopes' category maps are **summed**, not recomputed over merged rows (see backend.md). |
| GET | `/api/export` | Full JSON dump as a download |

## Focus sessions (authenticated)

| Method | Path | Notes |
|---|---|---|
| POST | `/api/focus/sessions` | `{date, startedAt, durationMinutes, completed, kind}`. Records a pomodoro session and **atomically adds its minutes to that day's hours** (rounded to 0.1 h, day capped at 24). `kind` is `study` (default → `daily_logs.hours`) or `work` (→ `work_logs.hours`). `completed=false` marks an ended-early session; its partial minutes still count. |
| GET | `/api/focus/sessions?date=YYYY-MM-DD[&kind=study\|work]` | That day's sessions, ordered by start time; optional `kind` filters to study or work |

## Plan (authenticated)

Plan **content** never lives in the repo (it's personal; the repo is public) — it is loaded at
runtime via the import endpoint from a locally generated `plan.json`
(`gt2/tools/plan-import/xlsx_to_plan_json.py`).

| Method | Path | Notes |
|---|---|---|
| GET | `/api/plan` | `{items[], quarters[], reference[]}` — all trackable items (milestones/certs/modules/books/papers/projects), the 16-quarter roadmap, and the reference sheets (row-JSON) |
| PATCH | `/api/plan/items/{id}` | `{status?, notes?}` — status ∈ `not_started/in_progress/done`; transitioning to done stamps `completedAt` |
| POST | `/api/plan/import` | Full plan.json replace. Items matched by (type, title) **keep their status, completedAt, and notes** — re-importing an evolved workbook never loses progress. |

## Todos (authenticated)

Short-lived actionable items, tagged `work` or `personal` so the list can be filtered to one side
of the day. Separate from the Plan, which is the fixed 4-year roadmap.

| Method | Path | Notes |
|---|---|---|
| GET | `/api/todos[?kind=work\|personal]` | Open items first, then `sortOrder`, then id. Unknown `kind` → 400 |
| POST | `/api/todos` | Create `{title, kind?, dueDate?}` — title required (≤300 chars, trimmed); `kind` defaults to `personal`; new items sort to the end |
| PATCH | `/api/todos/{id}` | Partial `{title?, kind?, done?, dueDate?, clearDueDate?, sortOrder?}`. A null `dueDate` leaves the existing one alone — pass `clearDueDate: true` to remove it. Flipping `done` stamps/clears `completedAt`. 404 if missing |
| DELETE | `/api/todos/{id}` | Remove a todo |

## Work (authenticated)

Day-job tracking, kept separate from the personal study tracking above. Content is user-entered
and lives only in the database — nothing is seeded (the repo is public).

| Method | Path | Notes |
|---|---|---|
| GET | `/api/work/days?from=YYYY-MM-DD&to=YYYY-MM-DD` | Ordered range of work-day logs |
| GET | `/api/work/days/{date}` | Single work day or `null` |
| PUT | `/api/work/days/{date}` | Upsert: `{hours, categories[], project, goals, did, blockers, learnings}`. Validates hours 0–24. |
| DELETE | `/api/work/days/{date}` | Remove a work day |
| GET | `/api/work/skills` | Deliberate competency checklist, ordered by `sortOrder` then id |
| POST | `/api/work/skills` | Create: `{name, category?, detail?}` — name required; status defaults to `not_started` |
| PATCH | `/api/work/skills/{id}` | Partial update `{name?, category?, detail?, status?, notes?, sortOrder?}`; status ∈ `not_started/in_progress/proficient`; 404 if missing |
| DELETE | `/api/work/skills/{id}` | Remove a skill |

## Finance (authenticated)

Personal money tracking: accounts, transactions and savings goals. Like the Work tab, all content
is user-entered and lives only in the database — nothing is seeded, and no statement file is ever
committed (`statements/`, `*.csv`, `*.ofx`, `*.qfx` are gitignored).

| Method | Path | Notes |
|---|---|---|
| GET | `/api/finance/summary` | Dashboard payload: savings balance, net worth, goals with progress, accounts, uncategorized count |
| GET | `/api/finance/accounts?includeInactive=false` | Accounts ordered by `sortOrder` then name |
| POST | `/api/finance/accounts` | Create: `{name, institution, accountType, last4?, countsTowardSavings?, sortOrder?}` |
| PUT | `/api/finance/accounts/{id}` | Full update, same body plus `active` |
| PATCH | `/api/finance/accounts/{id}/balance` | `{balance, asOf?}` — sign is corrected server-side for cards and loans |
| DELETE | `/api/finance/accounts/{id}` | Removes the account **and its transactions** (`ON DELETE CASCADE`) |
| GET | `/api/finance/accounts/{id}/transactions` | Newest first |
| GET | `/api/finance/transactions/uncategorized` | The review inbox; excludes transfers |
| POST | `/api/finance/transactions` | Create: `{accountId, postedDate, transactionDate?, amount, description, txnType?, notes?}`. Omit `txnType` to let the server classify. Returns **409** if an identical row already exists. |
| PATCH | `/api/finance/transactions/{id}/category` | `{category}` — sets `categorySource=MANUAL`, which automation may never overwrite |
| PATCH | `/api/finance/transactions/{id}/type` | `{txnType}` ∈ `SPEND/INCOME/TRANSFER/PAYMENT` |
| DELETE | `/api/finance/transactions/{id}` | Remove a transaction |
| GET | `/api/finance/goals?includeInactive=false` | Goals with `currentAmount`, `remaining`, `progressPercent` |
| POST | `/api/finance/goals` | Create: `{name, targetAmount, targetDate?, note?, sortOrder?}` |
| PUT | `/api/finance/goals/{id}` | Full update, same body plus `active` |
| DELETE | `/api/finance/goals/{id}` | Remove a goal |

**Amount sign convention:** always signed, negative = money leaving the account, on every account
type. Importers normalize into this from three different bank conventions.

**Why `txnType` matters:** a credit-card payment is not an expense — the expense was the original
purchase. `TRANSFER` and `PAYMENT` are excluded from every spend rollup, in the repository query
rather than at the call site, so no future report can forget to do it.
