# A week with the code

A reading order for someone who has a week and wants to understand every part of grindtrack
well enough to change it. Each day has three parts: **read** (the docs, in order), **trace**
(open the code with a question in mind and follow one path through it), and **do** (a change
you can make and check). The doing is the point; the reading is so the doing is not guesswork.

Rough shape: two days on the shape of the app and how a request moves through it, one on auth,
one on the frontend, two on the assistant and what it can reach, one to build something.

## Before day one: run it on your machine

Everything below assumes the app is running locally so you can watch it while you read.

**Postgres.** The defaults in `application.yml` expect `localhost:5432`, user `grind`, password
`grind`, database `grindtrack`:

```bash
docker run -d --name gt-pg -p 5432:5432 \
  -e POSTGRES_USER=grind -e POSTGRES_PASSWORD=grind -e POSTGRES_DB=grindtrack postgres:16-alpine
```

**Backend.** Liquibase creates the schema on first boot, and `UserBootstrap` creates the user
and logs the TOTP URI once — read [auth.md](auth.md#bootstrap) before you lose it:

```bash
cd gt2/backend
GRINDTRACK_USERNAME=you GRINDTRACK_PASSWORD='a password' mvn spring-boot:run
# or: mvn -q package -DskipTests && java -jar target/grindtrack.jar
```

**Frontend.** Vite on `:5173`, proxying `/api` to the backend, so cookies work without CORS:

```bash
cd gt2/frontend && npm ci && npm run dev
```

**Tests** are the fastest way to check anything you change: `mvn verify` in the backend (with
spotless formatting), `npm run build` in the frontend (typecheck is the gate).

**Keys** are optional. Without `ANTHROPIC_API_KEY` in your shell the assistant is off and says
so; the whole app still works. Add the key on day five.

## Day 1 — the shape of the app

**Read:** [architecture.md](architecture.md), then Parts 1 and 3 of
[architecture-conventions.md](architecture-conventions.md).

The one idea to hold onto: a single Spring Boot container serves both the API and the built React
app from one origin. Everything else — the cookie model with no CORS, the deploy shape, the
service worker's rule about `/api` — follows from that.

**Trace:** the request lifecycle in architecture.md, with `tracking/api/TrackingController` and
`tracking/service/TrackingService` open. Find the three layers (api → service → domain) and the
one place validation of request strings lives (`web/Requests`). Then find `ApiExceptionHandler`
and read the five statuses it produces; every error in the app is one of them.

**Do:** add a `GET /api/tracking/echo?date=` endpoint that parses the date with `Requests` and
returns it, with a `MockMvc` test next to `TrackingControllerTest`. Delete it after. You have
touched all three layers and the test style.

**Questions to be able to answer:** Why is there no CORS configuration anywhere? Why does a
missing day answer 200 with an empty body rather than 404?

## Day 2 — the data, and how it changes

**Read:** [backend.md](backend.md) — the endpoint tables, then the data model and its diagrams,
then migrations.

**Trace:** pick one table you have used from the phone (`daily_logs` is the natural one) and
follow it end to end: the migration that created it, the entity, the repository, the service
that writes it, the controller that exposes it, the TypeScript type in `lib/types.ts`, the screen
that renders it. Six files. Every feature is those six files.

Then read `db.changelog-master.yaml` top to bottom. The migrations are a history of the app;
each one's comment says what changed and usually why.

**Do:** write a migration that adds a nullable column to a table of your choice, with a
rollback, and the entity field to match. Boot the app and confirm Liquibase applied it. Then
delete both. Rule learned: never edit an applied changeset.

**Questions:** Why does `assistant_reports` keep a column named `week_start` for a brief that
covers one day? Why is nothing personal ever seeded in SQL?

## Day 3 — auth

**Read:** [auth.md](auth.md), all of it. It is the most carefully argued document in the repo,
and the one where a mistake costs the most.

**Trace:** log in with the devtools network tab open. Watch three cookies arrive. Wait thirty
minutes (or set `access-token-minutes: 1` locally) and watch a request get a 401, the client
call `/api/auth/refresh` once, and the original request replay. That single-flight refresh lives
in `lib/api.ts`; read it after you have watched it.

Then `auth/security/JwtAuthFilter` and `auth/service/AuthService`: rotation, the one-day grace
for the loser of a race, and family revocation on reuse. Draw the state machine yourself before
reading the one in the doc.

**Do:** write the test that a rotated refresh token replayed after the grace period revokes its
family. (One may already exist — find it first, then write the one it does not cover.)

**Questions:** Why is the access token not revocable, and why is that fine? What does the
trusted-device cookie skip, and what does it never skip?

## Day 4 — the frontend

**Read:** [frontend.md](frontend.md), then Part 2 of
[architecture-conventions.md](architecture-conventions.md).

**Trace:** `App.tsx` is a two-level state machine: a view (landing, login, app) and a tab. Follow
a tab from `lib/tabs.ts` through `BottomNav` and `MoreSheet` to the screen it renders. Then read
one feature screen fully — `features/tracking/Today.tsx` — and the `api()` calls it makes.

Then the phone: `styles.css` has the touch-target rules under `@media (pointer: coarse)`, and
`docs/frontend.md` explains why every new control restates the 44px floor. The service worker
in `public/sw.js` is short and its header comment is the whole policy.

**Do:** add a tab. `Tab` union, `TABS`, a screen component, a case in `App.tsx`, an icon in
`TabIcon`. Put it in the more sheet. Check it at 393px wide in the browser's device mode. Then
remove it.

**Questions:** Why does the service worker never intercept `/api`? Why is there no token in
JavaScript anywhere?

## Day 5 — the assistant

**Read:** [assistant.md](assistant.md), start to finish, with the diagrams. Then
[api.md](api.md#assistant-authenticated) for the endpoint shapes.

The rule that organises everything: the model reads everything and produces only drafts, and a
person turns a draft into the real thing by pressing a button. Once you see that every arrow in
the map lands on `assistant_reports`, the rest is detail.

**Trace:** with `ANTHROPIC_API_KEY` in your shell, ask a question on the ask tab and read the
backend log for the turn: tools called, rounds, tokens, cache reads. Then read
`AnthropicChatModel.round` and `ChatService.chat` with that log next to them. Then
`AssistantToolExecutor` — six tools, and the two that draft delegate to services that the week
tab also uses.

Then the draft-then-accept path with `WeekPlanService` open: `propose`, the stored row, `accept`
re-reading it. Then `DayLogService` and the merge.

**Do:** exercises 1–3 in assistant.md (trace a turn, move the brief, break the cache on
purpose). If there is time, exercise 4: a new read tool.

**Questions:** Why does the context carry no timestamp? Why is the tool loop bounded at six?
Why is the conversation row written after the reply rather than before?

## Day 6 — the phone, and the two other providers

**Read:** [push-notifications.md](push-notifications.md) and
[speech-to-text.md](speech-to-text.md). Both are design documents with the runbook at the end;
read the design first. Then the [optional features runbook](deployment.md#optional-features-the-assistant-push-and-speech)
in deployment.md, which is the same three moves for all three keys.

**Trace, push:** `PayloadCipherTest` first — the RFC's worked example, byte for byte — then
`PayloadCipher` with RFC 8291 open in another tab. Then `PushService.deliverOne` and the three
outcomes. Then `lib/push.ts` and the five states, and `sw.js`'s two new listeners.

**Trace, speech:** `TranscriptionRelay` is the whole protocol in one class; read it with
`TranscriptionRelayTest` beside it, which speaks for both the model and the browser. Then
`lib/speech.ts` — the worklet string, the `ready` gate, and why `getUserMedia` is the first
await inside the tap.

**Do:** push exercise 3 (a new producer) or speech exercise 3 (the "hearing you" indicator).
Both are a screen's worth of change and both touch a real device.

**Questions:** Why two key pairs in Web Push, and which one does the app generate? Why is the
transcription key on the server rather than a short-lived token in the browser? Why is stop a
handshake?

## Day 7 — deployment, and something of your own

**Read:** [deployment.md](deployment.md), all of it, and the k8s repo's `docs/` for the cluster
underneath.

**Trace:** the CI workflow in `.github/workflows/ci-cd.yml` at the repository root: verify,
build, push the image,
`kubectl set image`, `rollout status`. Then find the rollout that timed out in the history of
`main` (the speech relay's first deploy) and read the fix commit: two bean-wiring faults the
unit tests could not see, found by booting the jar locally. That is the one class of bug this
suite does not catch, and the reason "boot it once" belongs in your checklist.

**Do:** the open item in every list. Either the backup CronJob (deployment.md names it as the
gap; prove the restore), or `WeekPlanService.accept` made idempotent (assistant.md exercise 5),
or the calendar-block reminder (push-notifications.md's "what this sets up"). Pick the one you
would want on Monday morning.

## How to read this code

Three habits that make the repo legible faster than any document:

- **Read the class comment first.** Every class opens with why it exists and what it refuses to
  do. The what is in the code; the comment is the why.
- **Find the test with the same name.** `FooServiceTest` next to `FooService` states the
  contract in plain sentences as method names. It is usually the fastest way to learn what a
  class promises.
- **Ask what "off" looks like.** Every optional thing here has a quiet off state — no key, no
  network, no recognizer — and the code that handles it is usually the code that explains the
  feature's edges.
