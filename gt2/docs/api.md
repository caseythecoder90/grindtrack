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
| POST | `/api/auth/login` | `{username, password, otp?, trustDevice?}` | Sets `gt_access` (30 min) + `gt_refresh` (90 d, sliding) cookies, plus `gt_device` (30 d) when `trustDevice` is true. `otp` may be omitted on a trusted device. 401 on any failure (deliberately does not say which factor failed). 429 after 5 attempts / 5 min / IP. |
| GET | `/api/auth/device` | – | `{trusted, count}` — whether this browser holds a live device cookie. Public; says nothing about who. |
| POST | `/api/auth/refresh` | – | Renews the session (slides expiry, same token) and sets a fresh `gt_access`; rotates the token once it is 24 h old. 401 **and both cookies cleared** if missing/expired/revoked; a rotated token replayed more than 24 h after its rotation also revokes that token's family (see [auth.md](auth.md)). |
| POST | `/api/auth/logout` | – | Revokes this browser's session server-side and expires both cookies. The device cookie is kept. |
| POST | `/api/auth/logout-all` | – | Authenticated. Ends every live session for the account, on every device. `{status, sessionsEnded}`; expires this browser's cookies. |
| POST | `/api/auth/devices/forget` | – | Authenticated. Revokes every trusted device; `{trusted: false, count}`. |
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

**Subjects belong to the lunch kinds only.** A `planItemId` or `topic` sent with a `study` or
`work` session is dropped, not rejected — the service enforces it, so the rule holds however the
call arrives. The browser sent exactly this once: the subject is persisted in the timer config
next to `kind` so a reload cannot detach a session from its book, and switching kind did not
clear it, which filed an hour of cert prep against a payments book.

`reading` and `review` carry what the session went into.
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
| GET | `/api/plan` | `{items[], quarters[], reference[]}` — all trackable items (milestones/certs/modules/books/papers/projects), the 20-quarter (5-year) roadmap, and the reference sheets (row-JSON) |
| PATCH | `/api/plan/items/{id}` | `{status?, notes?}` — status ∈ `not_started/in_progress/done`; transitioning to done stamps `completedAt` |
| POST | `/api/plan/import` | Full plan.json replace. Items matched by (type, title) **keep their status, completedAt, and notes** — re-importing an evolved workbook never loses progress. |

## Assistant (authenticated)

The surface an assistant reasons over, and the three features built on it. **What the model can
read and what it can change are deliberately different sizes**: the four tools behind every one of
these calls are reads, and the only write in the whole feature is booking a proposed week onto the
calendar — a separate POST, on a draft you have already been shown. The first thing an assistant
with a write path does wrong is mark the wrong plan item done, and unlike a wrong sentence that is
invisible until the next review.

The POSTs below that call a model **cost money and take ten to thirty seconds**, which is why they
are POSTs: none of them is safe to retry idly or to prefetch. With no API key configured every one
of them answers 503 with a sentence, and the GETs keep working.

Full design notes: [assistant.md](assistant.md).

| Method | Path | Notes |
|---|---|---|
| GET | `/api/assistant/context[?today=YYYY-MM-DD]` | The whole picture in one request, ~1.5k tokens. `today` is for reproducing a Friday review on a Saturday, and for tests |
| GET | `/api/assistant/tools` | The read tools, described — generated from the app so it cannot drift from it |
| GET | `/api/assistant/status` | Whether it is on, and what the month has cost — including what prompt caching saved, which can be negative |
| GET | `/api/assistant/reviews?weekStart=` | The stored draft for that week, or `null`. No model call |
| POST | `/api/assistant/reviews?weekStart=` | **Spends money.** Drafts the weekly review, ~3¢. The Friday 17:00 job posts the same thing |
| GET | `/api/assistant/week-plan?weekStart=` | The stored proposal, or `null`. No model call |
| POST | `/api/assistant/week-plan?weekStart=` | **Spends money.** Proposes a week. Writes a draft and nothing else |
| POST | `/api/assistant/week-plan/accept?weekStart=` | One of two writes. Books the stored draft — re-read and re-validated server-side, never taken from the request |
| GET | `/api/assistant/day-log?date=` | A day's log as drafted in conversation, merged over the day as it stands now: `{logDate, generatedAt, changes, result}`. `changes` is only what was said (nulls mean unchanged); `result` is the whole day as it will read once saved. Or `null` |
| GET | `/api/assistant/brief[?date=]` | The morning brief for a day (today by default): `{date, generatedAt, model, costUsd, draft:{headline, today, suggestion}}`, or `null` before the 06:00 job has run |
| POST | `/api/assistant/brief[?date=]` | **Spends money** (~2¢). Redrafts the day's brief, replacing the row |
| POST | `/api/assistant/day-log/accept?date=` | The other write. Merges the stored draft over the day at that moment and saves through the same path the form uses; absent hours stay unchanged |
| GET | `/api/assistant/chat` | Conversations, most recent first: `{id, title, lastMessageAt, turns, hasDraft}`. `turns` and `hasDraft` exist because a title taken from the first message cannot tell two threads apart when both began with the same question — and a retry begins with exactly the same question |
| GET | `/api/assistant/chat/{id}` | One conversation's turns |
| POST | `/api/assistant/chat` | **Spends money.** One turn, answered when it is finished |
| POST | `/api/assistant/chat/stream` | The same turn as server-sent events: `tool`, `text`, `tick`, `done`, `error`. A failure arrives as an event, not a status — by then the response has been 200 for seconds |

A turn that drafted a week carries `proposedWeekStart` (a Monday) on both the reply and the stored
turn; one that drafted a day's log carries `proposedLogDate` the same way. It is a pointer, not the blocks: the client fetches those from `/week-plan?weekStart=` and
books with `/week-plan/accept`, so the card always shows what accepting will write. Nearly every
turn has it null.

| DELETE | `/api/assistant/chat/{id}` | Removes a conversation and its turns |

The context carries, in order: the current quarter, this week against its targets, planned versus
actual study hours, the lunch streak, in-flight and upcoming plan items, the next 14 days of
calendar, overdue and due-soon upkeep, open todos, and the last 14 logged days with what was
written on them.

Two rules govern what is in it:

- **Compact.** 194 plan items is most of a context window spent on rows that are years away, so
  only what is in flight or lands within 90 days is included.
- **Every actual arrives next to its target.** "14.5 hours" reads very differently against 20 than
  against 10, and a number with nothing to compare it to invites the model to invent the
  comparison.

**`plannedVsActual` is the one figure nothing else in the app computes**: study-block hours booked
this week against study hours logged. It is why the calendar records a plan item at all. Only
blocks with both a start and an end count — an all-day study block is an intention without a
duration, and counting it as zero understates the plan while counting it as a whole day overstates
it by an order of magnitude.

`quarter` is derived from today's date against the plan's own quarters, and is **null outside
them** rather than clamped to the nearest — before the plan starts and after it ends are both real
states, and neither should be reported as "you are in Q1".

## Calendar (authenticated)

Things that happen on a day. A **date plus an optional time**, not a timestamp: "09:00 on Sep 12"
on a personal calendar means wall clock, and it must not move when a server's zone does. A null
`startTime` is what all-day means — `allDay` is derived from it, so the two can never disagree.

| Method | Path | Notes |
|---|---|---|
| GET | `/api/calendar[?month=YYYY-MM]` | Whole month, day order, all-day entries first. Defaults to the current month |
| GET | `/api/calendar?from=&to=` | An explicit range, for the week view and the assistant. `to` before `from` → 400 |
| POST | `/api/calendar` | `{title, kind, date, startTime?, endTime?, planItemId?, notes?}`. `kind` ∈ `appointment/study_block/work_block/personal` |
| PATCH | `/api/calendar/{id}` | Partial. `clearTimes: true` makes it all-day; `clearPlanItem: true` unlinks. 404 if missing |
| DELETE | `/api/calendar/{id}` | Remove an event |

**Only a study block carries a plan item.** A `planItemId` sent with any other kind is **dropped,
not rejected** — the same rule a focus session follows for its reading subject, and for the same
reason: a stale link from an older client is not a mistake the user can act on, but hours filed
against a dentist are ones they would never see.

That link is recorded **intent** and is deliberately not a focus session's subject. This column
says what a morning was booked for; `focus_sessions` say what actually happened. Keeping them
separate is what makes planned-versus-actual answerable at all.

**An end time needs a start and must come after it** — enforced on the entity and by a CHECK, so
a service cannot build an impossible event and find out from the database.

## Upkeep (authenticated)

Recurring maintenance: the dog's flea and tick, the HVAC filter, an oil change. The question is
"when did I last", and the one it is asked is "what is overdue" — both the same fact read two ways.

**`nextDue` is derived, never stored.** It is `lastDoneOn + intervalDays`, computed per request. A
stored copy is one write away from disagreeing with the field it came from, and a reminder that is
wrong once is not trusted again. A task never done is due **today**, not at some epoch date, so it
sits at the top of the list without claiming to be twenty thousand days late.

| Method | Path | Notes |
|---|---|---|
| GET | `/api/upkeep` | `{due, archived}`. `due` is active tasks, most overdue first |
| POST | `/api/upkeep` | `{title, category, intervalDays, lastDoneOn?, notes?}`. `category` ∈ `pet/home/health/car/other`; `intervalDays` 1–3650; a future `lastDoneOn` → 400 |
| POST | `/api/upkeep/{id}/done` | One tap. Logs a completion and rolls the clock **from the date it was done**, not from when it was due — otherwise the drift compounds every cycle. A second tap the same day is not a second completion. 404 if missing |
| PATCH | `/api/upkeep/{id}` | Partial `{title?, category?, intervalDays?, notes?, active?, lastDoneOn?}`. Setting `lastDoneOn` here is a **correction** and logs nothing: fixing a typo is not the same act as doing the task |
| DELETE | `/api/upkeep/{id}` | Remove a task and its completions |
| GET | `/api/upkeep/{id}/history` | Every completion, newest first |

`state` is one of `overdue` / `due_soon` (within 7 days) / `later`, and `daysOverdue` is negative
before the due date, zero on the day, positive once late — one number a screen can render rather
than three booleans it has to reconcile.

## Todos (authenticated)

Short-lived actionable items, tagged `work` or `personal` so the list can be filtered to one side
of the day. Separate from the Plan, which is the fixed multi-year roadmap.

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
| GET | `/api/relationship/moments?limit=&offset=` | A page of the timeline, newest first: `{items[], total, offset, limit}`. `total` is every moment ever logged, which is what lets a footer say "13–24 of 48" rather than offering an "older" button that might do nothing. `offset` comes back **snapped** to a page boundary — the rows returned are the page it fell inside, so reporting the asked-for offset would mislabel them. Past the end is an empty page, not a 404; `limit` caps at 100 |
| POST | `/api/relationship/moments` | Log one `{occurredOn?, kind, note?, feltClose?}`; a future date is a 400 |
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

## Push notifications (authenticated)

Web Push to the installed app. Design, key generation and the runbook are in
[push-notifications.md](push-notifications.md). With no VAPID pair configured, `status` says so and
the writes answer 503.

| Method | Path | Body | Answer |
|---|---|---|---|
| GET | `/api/push/status` | – | `{configured, publicKey, devices}` — the VAPID public key a browser subscribes with, and how many have |
| GET | `/api/push/subscriptions` | – | `[{id, label, createdAt, lastSentAt, endpoint}]` |
| PUT | `/api/push/subscriptions` | `{endpoint, keys: {p256dh, auth}, userAgent?}` — what `pushManager.subscribe` returned | upsert by endpoint; `{id, devices}`. 400 with a sentence when the keys are not a browser's |
| DELETE | `/api/push/subscriptions/{id}` | – | `{deleted: id}` |
| POST | `/api/push/test` | `{endpoint?}` | sends the test notification to that device, or to all when absent; `{sent, gone, failed}` |

## Speech to text (authenticated)

| Method | Path | Body | Answer |
|---|---|---|---|
| GET | `/api/speech/status` | – | `{configured, model}` — whether the mic button should exist |
| WS | `/api/speech/ws` | binary frames of PCM16 mono 24 kHz; one text frame `{"type":"stop"}` | text frames `{type: "ready" \| "delta" \| "final" \| "speech" \| "error", text?, state?, message?}`; the server closes after the last phrase or on error |

The handshake is an ordinary GET through the security filter, so the session cookie gates it.
Off (no `OPENAI_API_KEY`) is an `error` frame and a close.
