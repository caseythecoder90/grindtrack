# API reference

All request/response bodies are JSON. Authenticated endpoints require the `gt_access` cookie
(set by login/refresh); unauthenticated calls receive `401` with no body.

## Errors

One envelope, `{"error": "<a sentence you can act on>"}`, produced by a single
`@RestControllerAdvice`:

| Status | When |
|---|---|
| 400 | Malformed shape — an unparseable date, an unknown enum constant, a string over its column length. Also a domain rule the service enforces (a duplicate budget category, an uncompilable regex, a moment dated in the future). |
| 404 | No such row. The message names it: `not found: account 5`. |
| 409 | Well-formed but inapplicable — currently only an identical transaction that already exists. |
| 500 | Anything unanticipated, with its stack trace kept in the log. There is deliberately no handler that catches `Exception`. |

Deletes answer `{"deleted": <id>}` and day-keyed upserts `{"saved": "<date>"}`. Reads of a day or
week with nothing recorded answer **200 with an empty body**, not 404 — "not logged yet" is not an
error.

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
| PUT | `/api/days/{date}` | Upsert: `{hours?, categories[], focus, did, wins, blockers, energy}`. Validates hours 0–24, energy 1–5. **Omitting `hours` leaves the stored value alone** — see below. |
| DELETE | `/api/days/{date}` | Remove a day |
| GET | `/api/weeks/{date}` | Weekly review; any date normalizes to its Monday |
| PUT | `/api/weeks/{date}` | Upsert review: `{summary, wins, blockers, adjustments, nextFocus, onTrack}` |
| GET | `/api/stats` | `{study, work, all}`, each a scope of `{totalHours, daysLogged, streak, daysThisMonth, weeks[12], categories[], days[]}`. All three are computed per request so the UI switches scope without refetching. `days[]` is the 26-week heatmap series. Category hours are split evenly across a day's tags; in `all` the two scopes' category maps are **summed**, not recomputed over merged rows (see backend.md). |
| GET | `/api/export` | Full JSON dump as a download |

### Hours have two writers

`hours` on a day (study or work) is written two ways with different meanings: the form sets a
total, and a focus session **adds** to it. An absolute write that re-sends a stale total therefore
undoes every session logged since the form loaded — across two devices, that is a silent lost
update.

So `hours` is optional on both upserts: **absent means unchanged**, and the forms omit it unless the
box was actually edited. It previously meant *zero*, so any client omitting the field wiped the
day's hours outright.

## Focus sessions (authenticated)

| Method | Path | Notes |
|---|---|---|
| POST | `/api/focus/sessions` | `{date, startedAt, durationMinutes, completed, kind, planItemId?, topic?}`. Records a pomodoro session and **atomically adds its minutes to that day's hours** (rounded to 0.1 h, day capped at 24). `completed=false` marks an ended-early session; its partial minutes still count. |
| PATCH | `/api/focus/sessions/{id}/takeaway` | `{takeaway}` — the note written *after* the session, which is the only time you know it. Blank clears it. |
| GET | `/api/focus/sessions?date=YYYY-MM-DD[&kind=…]` | That day's sessions, ordered by start time; optional `kind` filter |
| GET | `/api/focus/reading` | The lunch dashboard: weekday streak, **days** this week against the target (plus the session count and hours for the same window), per-subject totals, and the recent takeaways |

**Kinds.** `study` (the 6–8am block), `work` (the day job), `reading` (books, papers, RFCs) and
`review` (reading your own code). Only `work` folds into `work_logs.hours`; the other three go to
`daily_logs.hours`, because they are all study. An absent `kind` means `study`; anything else is a
400 — it used to be coerced to `study`, so a typo filed work hours as study time.

**Subjects.** `reading` and `review` are the lunch kinds and carry what the session went into.
`planItemId` links to a book/paper/module in the plan; `topic` is the label, snapshotted at write
time so history stays readable if the workbook later renames or drops the item — and it is the only
subject a `review` session has, since a repo is not a plan item. `/api/focus/reading` groups by
item id when there is one and by lower-cased topic otherwise, so `Grindtrack` and `grindtrack` do
not become two subjects.

**The weekly target counts days, not sessions**, so it and the streak measure the same unit — two
pomodoros over one lunch is one day against the target, exactly as the streak counts it.
`sessionsThisWeek` is reported alongside as the effort figure.

**The streak counts weekdays.** A weekend is skipped, not counted as a miss, and it is computed
from lunch sessions only — a long evening of cert prep must not be able to satisfy it, or it stops
measuring the habit it exists to protect.

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
| PUT | `/api/work/days/{date}` | Upsert: `{hours?, categories[], project, goals, did, blockers, learnings}`. Validates hours 0–24. **Omitting `hours` leaves the stored value alone.** |
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
| GET | `/api/finance/transactions` | Paged browse: `accountId, txnType, uncategorizedOnly, sort=date\|amount, page, size` (≤200). Answers `{items[], page, size, totalElements, totalPages}` |
| POST | `/api/finance/transactions/{id}/categorize` | `{category, createRule?}` — files the row and, with `createRule`, writes a rule from the normalized merchant. Answers `{transaction, rule, ruleExisted}`; `rule` is null when none was asked for or one already existed |
| POST | `/api/finance/transactions/reclassify` | Re-run the type classifier over every row |

### Category rules

| Method | Path | Notes |
|---|---|---|
| GET | `/api/finance/rules?includeInactive=true` | Ordered by priority |
| POST | `/api/finance/rules` | `{pattern, matchType?, category, priority?}` — `matchType` ∈ `CONTAINS/EQUALS/REGEX`, default `CONTAINS`; an uncompilable regex is a 400 |
| PUT/DELETE | `/api/finance/rules/{id}` | Update / remove |
| POST | `/api/finance/rules/apply` | Re-run every rule over the whole history. Hand-corrected rows are never touched. |

### Spending

| Method | Path | Notes |
|---|---|---|
| GET | `/api/finance/spending?from=&to=` | Rollup over a window, default the last 30 days. `from` after `to` is a 400 |
| GET | `/api/finance/spending/monthly?months=6` | Months side by side, 1–36 |
| GET | `/api/finance/recurring` | The charges that come back every month |

### Budget

| Method | Path | Notes |
|---|---|---|
| GET | `/api/finance/budget/month?month=yyyy-MM` | The plan, what happened, and the gap, per category. Defaults to this month |
| GET/POST | `/api/finance/budget/lines` | The recurring plan |
| PUT/DELETE | `/api/finance/budget/lines/{id}` | |
| GET/POST | `/api/finance/budget/extras?from=yyyy-MM` | One-off costs and windfalls for a single month |
| PUT/DELETE | `/api/finance/budget/extras/{id}` | |
| PUT | `/api/finance/budget/income` | `{expectedMonthlyIncome}`; null or zero reverts to the trailing average of real deposits. Answers `{expectedMonthlyIncome, estimated}` |

### Statement import

| Method | Path | Notes |
|---|---|---|
| GET | `/api/finance/imports` | Upload history |
| POST | `/api/finance/imports?accountId=&dryRun=false` | Multipart `file`, ≤5 MB. Read into memory and never written to disk. `dryRun` parses and reports without writing — worth doing the first time each bank's export is tried, since the counts alone reveal a wrong-account upload |
| DELETE | `/api/finance/imports/{id}` | Undo a batch; answers `{undone, transactionsRemoved}` |

**Amount sign convention:** always signed, negative = money leaving the account, on every account
type. Importers normalize into this from three different bank conventions.

**Why `txnType` matters:** a credit-card payment is not an expense — the expense was the original
purchase. `TRANSFER` and `PAYMENT` are excluded from every spend rollup, in the repository query
rather than at the call site, so no future report can forget to do it.

## Relationship (authenticated)

Deliberately absent from `/api/public/**`. Nothing here has a public shape.

| Method | Path | Notes |
|---|---|---|
| GET | `/api/relationship/summary` | `{recency[], closeness, upcoming[], readyIdeas[], lately[]}` |
| GET/POST | `/api/relationship/moments` | Timeline (`?limit=60`) / log one `{occurredOn?, kind, note?, feltClose?}`; a future date is a 400 |
| PUT/DELETE | `/api/relationship/moments/{id}` | |
| GET/POST | `/api/relationship/ideas` | List (`?includeDone`) / create; least effort first |
| PUT/DELETE | `/api/relationship/ideas/{id}` | |
| POST | `/api/relationship/ideas/{id}/done` | `{on?}` — logs the idea as a moment and takes it off the list |
| GET | `/api/relationship/occasions` | Every occasion with its next date and how many ideas are waiting |
| POST/PUT | `/api/relationship/occasions[/{id}]` | Answers with the whole list: every next date shifts together |
| DELETE | `/api/relationship/occasions/{id}` | |
| GET/POST | `/api/relationship/reading[?status=TO_READ\|READ]` | |
| POST | `/api/relationship/reading/{id}/read` | `{takeaway, readOn?}` |
| POST | `/api/relationship/reading/{id}/promote` | Turns a takeaway into a gesture idea — the reason the takeaway field exists |
| DELETE | `/api/relationship/reading/{id}` | |
