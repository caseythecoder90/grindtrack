# Architecture conventions

How code is organised in this repo, and why each rule exists. Every rule below cites the file that
motivated it, because a convention with no concrete failure behind it is just taste.

`architecture.md` describes what the system *is*. This describes how to **add to it** without the
codebase drifting into two shapes again — which is exactly what had happened by August 2026, when
half the controllers talked to a service and half talked straight to a repository.

---

## Part 1 — The backend

### The four layers, and the one rule each

| Layer | Owns | Never contains |
|---|---|---|
| **Controller** (`<feature>/api/`) | HTTP: routing, parsing request strings, calling **one** service, mapping to DTOs | Business rules, `@Transactional`, repositories |
| **Service** (`<feature>/service/`) | The transaction boundary, orchestration, rules that span entities | HTTP types (`ResponseEntity`, status codes) |
| **Domain** (`<feature>/domain/`) | Its own state transitions and the invariants protecting them | Anything about how it is displayed or delivered |
| **Repository** (`<feature>/domain/`) | Queries | Logic of any kind |

#### A controller injects services, never repositories

`FinanceController` injected `TransactionRepository` alongside two services, purely to call
`countByAccountId` while mapping an account to its DTO. It looked harmless. The cost is that
"how do you count an account's transactions" now has two answers depending on which file you are
in, and the next person adds a third.

If a controller needs a number, the service provides it.

#### A controller calls exactly one service

Not a hard rule you can never break, but treat a second injected service as a design smell worth a
minute of thought. `FinanceController` needing `FinanceService` **and** `CategoryRuleService` is
legitimate — they are genuinely different capabilities that share a URL prefix. `TrackingController`
needing three repositories was not: it meant the controller *was* the service.

#### Controllers do not open transactions

`@Transactional` belongs on service methods. A controller that manages a transaction has taken over
the service's job, and the boundary of a unit of work stops being visible in one place.

### Where validation goes — the distinction that matters most

Two kinds of validation, two homes. Getting this wrong is the single most common way a service
becomes untestable.

**Shape validation → controller.** Is this a date? Is `CONTAINS` a real `MatchType`? Is the title
under 300 characters? The answer is always HTTP 400 and the message is written for whoever typed
the request. It is meaningless without a request, so it lives with the request.

**Invariant validation → service.** Does this category already have a budget line? Is that regex
compilable? Is the moment dated in the future? These must hold no matter how the call arrives — via
REST today, via an import job or a CLI tomorrow. A rule that only runs when the call came over HTTP
is a rule you will eventually violate.

The tell: if you can state the rule without mentioning a request, it belongs in the service.

`BudgetService.create` already gets this right — it rejects a duplicate category itself rather than
trusting the controller to have checked. `WorkController.validateDayFields` got it wrong: "hours
must be 0-24" is an invariant of a work log, not a fact about HTTP, and it sat in the controller
where no service test could reach it.

### Parsing and error mapping live in `dev.grindtrack.web`

Six controllers had each defined a private `BadRequest` class and its own `@ExceptionHandler` —
about fifty lines of identical code that could not be shared *because* each exception type was
private to its own file. Three had separate copies of `optionalDate`.

- `web/Requests.java` — static helpers: `requireDate`, `optionalDate`, `requireText`, `enumValue`.
- `web/BadRequestException.java` — one type, thrown by controllers.
- `web/ApiExceptionHandler.java` — one `@RestControllerAdvice` mapping exceptions to statuses.

Services still throw plain `IllegalArgumentException` for invariant failures, and the advice maps it
to 400. **This is a deliberate trade-off with a real downside:** `IllegalArgumentException` is
thrown by plenty of JDK code, so a genuine bug deep in a call stack can surface as a tidy 400
instead of a 500 and a stack trace. Accepted because the alternative — a bespoke exception hierarchy
threaded through every service — costs more than it saves at this size. **Revisit if** a
production 400 ever turns out to have been a NullPointer-shaped bug in disguise; the fix is a
`ValidationException` in a neutral package that services throw explicitly.

### DTO mapping stays in the DTO

`static from(Entity)` on the response record, as `TransactionResponse.from` and `WorkDayResponse.from`
already do. The mapping lives next to the shape it produces, so changing a field means opening one
file. No mapper classes, no MapStruct — see "decided against".

### Rich domain objects

This repo does this well and it should stay that way. `Transaction.categorizeByRule` refusing to
overwrite a manual category, `Occasion.nextOccurrence` handling 29 February, `Account.recordBalance`
signing liabilities — these are invariants that belong *to the object*, and putting them there is
what makes the service layer thin enough to read.

A service that only calls getters and setters on an entity is a sign the logic ended up in the wrong
place.

---

## Part 2 — The frontend

### Every URL for a feature lives in one module

`features/<feature>/<feature>Api.ts` exports typed functions; components import those, never a URL
string.

Fifty-seven endpoint literals were scattered across twenty-four components. A typo'd path was a
runtime 404 with nothing catching it at build time, and a backend rename meant grepping. With one
module per feature, a path change touches one file and TypeScript checks the call sites.

The module returns parsed data or throws. It does not catch, and it does not render.

### Components keep their own loading and error state

Deliberately **not** centralised. Each panel has a genuinely independent lifecycle — the import
panel can be mid-upload while the spending panel reloads — and a shared store would have to model
that anyway. `useState` per panel is honest about it.

What *was* worth removing: `e instanceof Error ? e.message : "..."` appeared fifty-two times. That
is now `errorMessage(e, fallback)` in `lib/api.ts`.

### The rest of the layout

- `lib/api.ts` — the fetch wrapper: cookies, the 401-refresh-replay dance, `errorMessage`. Nothing
  feature-specific ever goes here.
- `lib/types.ts` — every wire type, mirroring the backend DTOs.
- `features/<feature>/` — one page component that owns the feature's top-level state, plus panels.
  A panel takes data and callbacks as props, or loads its own slice if it is genuinely independent.
- `features/<feature>/<name>.ts` — pure helpers, no React: `money.ts`, `categories.ts`, `kinds.ts`.

### Formatting is a display concern and stays in the frontend

The backend sends numbers and ISO strings; the frontend decides that spending is red and that
`-410.55` reads as `$410.55`. `money.ts` says it in a comment — *money is displayed, never computed,
here* — and that split is why the sign convention (negative is money out) survives all the way from
the database to the one place it is flipped.

---

## Part 3 — What was decided against, and why

Worth more than the list above: these are the options a reasonable person would pick, rejected for
reasons specific to this codebase.

**Mapper classes / MapStruct.** A `TransactionMapper` would separate the DTO from its construction.
Rejected: `static from()` on the record is already one obvious place, and MapStruct adds annotation
processing and generated sources to a build that currently has neither. The pain it solves —
hand-writing dozens of near-identical mappings — is not pain this repo has.

**A repository-per-aggregate abstraction.** Wrapping Spring Data behind hand-written interfaces
would decouple the domain from JPA. Rejected: it buys portability nobody wants (Postgres is not
going anywhere) at the cost of an indirection on every single query, and Spring Data interfaces
already *are* the abstraction.

**CQRS / separate read models.** Rejected outright. `spendByCategoryBetween` is a projection query
returning a record — that is as far as this needs to go for a single-user app.

**A global exception handler that catches `Exception`.** Rejected: it turns every unexpected failure
into a clean JSON body and hides the stack trace that would have told you what broke. The advice
maps named types only; anything else stays a 500 and gets logged.

**Constructor-injected `Clock` everywhere.** `TotpService` takes one because time is load-bearing
there. Elsewhere `LocalDate.now()` is called directly and tests pass explicit dates into the methods
that matter (`BudgetService.month(YearMonth)`, `RelationshipService.upcoming(LocalDate)`).
Rejected as a blanket rule: designing the method to take the date it operates on is simpler than
injecting a clock, and it is testable for the same reason.

**React Query / SWR.** Rejected: the caching, retry and invalidation machinery solves problems this
app does not have. Panels reload explicitly after mutations via an `onChange` callback, which is
about fifteen lines of `useState` in total and is legible without knowing a library.

**A shared frontend store (Redux/Zustand/Context).** Rejected: the only genuinely shared state is
the auth view and the header stats, both of which live in `App.tsx` and are passed down. Adding a
store for that is machinery in search of a problem.

**Splitting the backend into modules or services.** Rejected, permanently. One Spring Boot app, one
database, one deployable. The feature packages *are* the modularity.

---

## Part 4 — Adding a feature, end to end

The order that keeps the layers honest:

1. **Migration** — `db/changelog/NNN-<feature>.sql`, registered in `db.changelog-master.yaml`.
   Comments explain why a column exists, not what type it is. Seed nothing: the repo is public.
2. **Domain** — entity with its invariants, enums, repository interface.
3. **Service** — `@Transactional`, orchestration, invariant validation. **Test this first and
   heaviest**; it is where the logic is and it needs no HTTP to exercise.
4. **DTOs** — request and response records with `static from()`.
5. **Controller** — routing, `Requests.*` parsing, one service call, DTO mapping.
6. **Wire types** — `lib/types.ts`, mirroring the response records.
7. **API module** — `features/<feature>/<feature>Api.ts`.
8. **UI** — page plus panels.

Run `mvn -B verify` (spotless is enforced, not advisory) and `npm run build` before committing.

### On comments

Every comment in this repo answers *why*, and where possible names the concrete thing that goes
wrong otherwise. `TxnType`'s comment does not say "an enum of transaction types" — it says that 78
of 947 real rows were transfers, and counting them would have inflated spending by thousands.

If a comment restates the code, delete it. If it explains a decision that cost you an afternoon,
it is the most valuable line in the file.
