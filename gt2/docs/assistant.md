# The assistant

Five features, one API key, and a hard rule: **the model can read everything and write almost
nothing.** The only write in the whole surface is the week planner booking calendar blocks, and
that takes a separate click on a draft you have already seen.

With no key the assistant is *off*, not broken: every endpoint answers 503 with a sentence, the
Friday job stays quiet, and the rest of the app neither knows nor cares. That is what makes it safe
to deploy this before the secret exists.

> Turning it on in production: [deployment.md](deployment.md).

## What it does

| Feature | Where | Calls | Writes |
|---|---|---|---|
| Weekly review draft | week tab, and a Friday 17:00 job | 1 per week | a stored draft, never the review itself |
| Chat | ask tab | 1–6 per turn | conversation history |
| Week planner | week tab | 1 per proposal | calendar blocks — only on **Book** |

## Configuration

```yaml
grindtrack.assistant:
  api-key: ${ANTHROPIC_API_KEY:}   # blank means off
  model: claude-opus-5
  zone: America/New_York           # "Friday at five" means the owner's Friday, not the pod's UTC
  review-cron: "0 0 17 * * FRI"    # Spring cron: second minute hour day month weekday
```

`AssistantProperties.configured()` is the single switch. It is deliberately separate from
`AppProperties`: a missing JWT secret is a broken deployment, a missing API key is a feature
switched off.

## The context, and why it has no timestamp

`ContextService` assembles `AssistantContext` — this week against its targets, planned versus
actual study hours, the lunch streak, in-flight and near plan items, the next fortnight of calendar,
and the recent logged days with your own notes. Two rules govern it: it is **compact** (194 plan
items would be most of a context window spent on rows years away) and every actual arrives **next
to its target**, because a number with nothing to compare it to invites the model to invent the
comparison.

It carries no assembled-at timestamp. `today` already carries the freshness anything in the prompt
reasons over, and a per-request clock reading would sit inside the cached prefix and change on
every call — see caching below. `ContextServiceTest` asserts that two builds of the same day
serialize to identical bytes, which guards the invariant rather than the one field that broke it.

## The four read tools

`AssistantToolExecutor` is the whole tool surface, and every one of them is a read:

| Tool | Fetches |
|---|---|
| `get_plan` | the full plan, all 194 items |
| `get_days` | daily logs over a range |
| `get_calendar` | calendar events over a range |
| `get_focus_sessions` | one day's focus sessions |

Ranges are capped at 120 days. Bad arguments come back to the model as an error string rather than
throwing, so it can correct itself instead of failing the turn.

The loop in `AnthropicChatModel` is manual rather than the SDK's runner, because the runner
instantiates tool classes itself and these are thin wrappers over Spring services. It enforces two
rules. Every tool result of a round goes back in **one** user message — splitting them trains the
model out of parallel calls. And it is bounded at six rounds: a model still reading after six is not
going to be saved by a seventh, and the turn fails with a message rather than running up a bill.

## Caching

A turn is not one request. Every round resends the whole prompt, so without caching a turn that
reads the plan and then last week's days pays for the system prompt, the four tool schemas and the
context three times before it answers a word.

Two breakpoints, placed by how often each part changes:

| Breakpoint | Covers | Changes |
|---|---|---|
| explicit, on the last system block | tools + system prompt + context | per turn |
| automatic, top-level | the history and accumulating tool results | per round |

`tools` renders before `system`, which is why a marker on the last system block caches both. The
default five-minute TTL is right here: rounds are seconds apart and a read refreshes the entry, so
an hour would only double the write price.

**This works only while the cached prefix is byte-identical.** The timestamp above and the fixed
tool order are both load-bearing. Change either and the cache silently stops hitting — no error,
just `cacheReadTokens` at zero and a bigger bill.

Only chat caches. A weekly review is one call a week with nothing to reuse; a breakpoint there
would buy the write premium and never a read.

## What it costs

Opus 5 bills **$5 per million input tokens and $25 per million output**, with cache writes at 1.25×
the input rate and cache reads at 0.1×. Cached tokens are stored in their own columns because they
are not billed at the same rate, and the API reports them separately and leaves them out of
`input_tokens` entirely — folding them in would make the month's figure understate the invoice.

`GET /api/assistant/status` prices the month from what actually happened, and reports
`cacheSavingUsd`: reads saved minus writes paid for. **It can go negative**, and that is the point —
a prefix written and never read back is a surcharge, and a persistently negative number means the
breakpoints should come out.

Rough shape: a weekly review is about 3¢. Chat is the variable one. A hard cap belongs at the
Anthropic account, not here.

## Streaming

`POST /api/assistant/chat/stream` sends the turn as it happens; `POST /api/assistant/chat` waits and
returns it. Same turn, same storage, same bill — the streaming one just has somebody watching.
The plain one stays because a stream is a poor thing to test against and a worse thing to curl.

POST rather than GET rules out `EventSource` on the client, which only does GET, in favour of
reading the response body as a stream. The alternative — post the question, get a handle, open a
second request to watch it — is two round trips and a piece of server state to invent a lifetime
for.

Events, each a JSON payload:

| Event | Payload | Means |
|---|---|---|
| `tool` | `{"name":"get_plan"}` | the model started reading something |
| `text` | `{"delta":"…"}` | a fragment of the answer |
| `tick` | `{}` | still alive; ignore |
| `done` | the `ChatReply` | finished, stored, billed |
| `error` | `{"error":"…"}` | the turn failed |

Three things about that list are deliberate. The payload is JSON so a newline inside an answer is
not a frame boundary. A failure arrives as an **event**, not a status code, because by the time a
turn can fail the response has been 200 for some seconds and the status line is long gone. And
`tick` exists because an ingress proxy closes an idle upstream connection after sixty seconds while
a model can think for longer than that before its first word — every upstream event, including the
ones this app has no use for, pokes the connection often enough to be left alone.

A stream that ends without `done` throws on the client rather than reporting a partial answer as a
complete one. The turn itself may well have finished and been stored; reopening the conversation is
what shows it.

### Threads and transactions

A turn is three phases and only the outer two touch the database, which is why `ChatService` drives
transactions by hand instead of wearing `@Transactional` over the whole thing: the model call in the
middle takes ten to thirty seconds, and holding a pooled connection across it would pin one of ten
to a request doing nothing but waiting on a socket. The split also means the conversation row is
written *after* the reply arrives, so a failed turn no longer leaves an empty thread in the list.

Streamed turns run on `assistantTurnExecutor`, a pool of two — not Tomcat's threads, which are
needed for the requests that answer in milliseconds. Nothing under there reads the authenticated
principal (the app has one user and the services are written without one), so the security context
staying behind on the request thread costs nothing. **That stops being true the day a second user
exists.**

## Testing without spending money

`ChatModel`, `ReviewModel` and `WeekPlanModel` are interfaces for one reason: tests must not call
the API. The `Anthropic*` implementations are the only classes that know a key exists.

To exercise the real thing, set `ANTHROPIC_API_KEY` in your own shell and run the backend locally.
That variable is unrelated to the one in the cluster — see [deployment.md](deployment.md).
