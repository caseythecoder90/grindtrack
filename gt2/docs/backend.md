# Backend

Spring Boot 3.5.16 on Java 21. Base package `dev.grindtrack`; entry point
`GrindtrackApplication` (`@SpringBootApplication` + `@ConfigurationPropertiesScan` — the latter is
what activates `AppProperties` without an explicit `@EnableConfigurationProperties`). Final
artifact: `target/grindtrack.jar` (`finalName=grindtrack`).

> See [architecture.md](architecture.md) for the system view and [auth.md](auth.md) for the auth
> internals and their sequence diagrams. This doc is the code-level reference: layers, endpoints,
> data model, config, build, and the request lifecycle.

## Package-by-feature, layered

Each feature is a top-level package split into layer subpackages:

| Subpackage | Holds | Depends on |
|---|---|---|
| `api` | `@RestController`s + DTO records | `service`, `domain` |
| `service` | business logic, `@Transactional` boundaries | `domain` |
| `domain` | JPA entities + Spring Data repositories | — |
| `security` | (auth only) the JWT filter | `service` |
| `config` | cross-cutting: `SecurityConfig`, `AppProperties` | — |

Full inventory:

```
dev.grindtrack
├── GrindtrackApplication.java
├── config/
│   ├── AppProperties.java        record, @ConfigurationProperties(prefix="grindtrack")
│   └── SecurityConfig.java       @EnableWebSecurity, SecurityFilterChain + PasswordEncoder beans
├── auth/
│   ├── api/AuthController.java            (+ nested LoginRequest record)
│   ├── service/AuthService.java           (+ nested RotatedTokens record)
│   ├── service/JwtService.java
│   ├── service/TotpService.java
│   ├── service/LoginRateLimiter.java
│   ├── service/UserBootstrap.java          CommandLineRunner (not a REST bean)
│   ├── security/JwtAuthFilter.java         OncePerRequestFilter
│   └── domain/{User,UserRepository,RefreshToken,RefreshTokenRepository}.java
├── tracking/
│   ├── api/{TrackingController,FocusController,PublicController,ExportController,Dtos}.java
│   ├── service/{StatsService,Stats,FocusService}.java
│   └── domain/{DailyLog,WeeklyReview,FocusSession}(+Repository).java
└── plan/
    ├── api/{PlanController,PlanDtos}.java
    ├── service/PlanService.java
    └── domain/{PlanItem,PlanQuarter,PlanReference}(+Repository).java
```

DTOs are Java **records**; response records carry a static `from(entity)` factory. DTO holders
`tracking/api/Dtos.java` and `plan/api/PlanDtos.java` are package-final with private constructors
(namespaces, not instantiable).

## REST endpoints

All controllers are `@RestController`. "Public" = permitted in `SecurityConfig`; everything else
requires a valid `gt_access` JWT cookie. Full request/response bodies in [api.md](api.md).

### `AuthController` — `/api/auth`
| Method | Path | Access |
|---|---|---|
| POST | `/login` — `{username,password,otp}` → sets `gt_access`+`gt_refresh`; 401 / 429 | Public |
| POST | `/refresh` — rotates via `gt_refresh` cookie | Public |
| POST | `/logout` — revokes + expires cookies | Public |
| GET | `/me` — `{username}` from `Principal` | Authenticated |

### `TrackingController` — `/api`
| Method | Path | Notes |
|---|---|---|
| GET | `/days?from=&to=` | range, ordered; 400 on bad dates |
| GET | `/days/{date}` | one `DayResponse` or `null` |
| PUT | `/days/{date}` | upsert; validates hours 0–24, energy 1–5, ≤50 categories, text ≤10k |
| DELETE | `/days/{date}` | |
| GET | `/weeks/{weekStart}` | date snapped to Monday (`previousOrSame(MONDAY)`) |
| PUT | `/weeks/{weekStart}` | upsert weekly review |
| GET | `/stats` | `Stats` aggregate |
| GET | `/export` | full JSON dump, `Content-Disposition: attachment` |

### `FocusController` — `/api/focus`
| Method | Path | Notes |
|---|---|---|
| POST | `/sessions` | `{date,startedAt,durationMinutes(1–1440),completed}` |
| GET | `/sessions?date=` | that day's sessions, ordered by start |

### `PublicController` — `/api/public`
| Method | Path | Notes |
|---|---|---|
| GET | `/stats` | `{streak,totalHours,daysLogged,days[]}` — last 26 weeks of `{date,hours}` only. **Never exposes text.** |

### `PlanController` — `/api/plan`
| Method | Path | Notes |
|---|---|---|
| GET | `` | `{items,quarters,reference}` |
| PATCH | `/items/{id}` | `{status,notes}`; 404 if missing; validates status ∈ {not_started,in_progress,done} |
| POST | `/import` | bulk `{items[],quarters[],reference[]}` |

## Auth internals (summary)

Deep dive with sequence diagrams in [auth.md](auth.md). The moving parts:

- **`JwtService`** — HS256 (`Keys.hmacShaKeyFor(jwtSecret)`), 15-min access tokens; `validate()`
  returns `Optional<subject>`, empty on any `JwtException`.
- **`TotpService`** — hand-rolled RFC 6238 (HMAC-SHA1, 30s period, 6 digits, ±1 window),
  constant-time compare; `generateSecret()` = 160-bit Base32; `provisioningUri()` builds the
  `otpauth://` URI.
- **`AuthService`** — `authenticate` (BCrypt **and** TOTP, both required); `issueRefreshToken`
  (32 random bytes, stores only SHA-256(token)); `rotate` (single-use rotation; reuse of a revoked
  token ⇒ `revokeAllForUser`); `revoke` (logout).
- **`LoginRateLimiter`** — in-memory per-IP sliding window, 5 / 5 min, bounded to 10k IPs.
- **`SecurityConfig`** — CSRF disabled (SameSite=Strict mitigates), session policy STATELESS,
  permitAll on static assets + `/api/public/**` + login/refresh/logout, everything else
  authenticated, bare-401 entry point, `JwtAuthFilter` before `UsernamePasswordAuthenticationFilter`.
- **`JwtAuthFilter`** — reads `gt_access`, validates, sets a
  `UsernamePasswordAuthenticationToken(username, null, [ROLE_USER])` into the `SecurityContextHolder`.

## Data model

![Grindtrack data model — Postgres schema `grindtrack`](diagrams/data-model.svg)

<sub>PlantUML source: [`diagrams/data-model.puml`](diagrams/data-model.puml) — edit it and regenerate the SVG with [`diagrams/render.sh`](diagrams/render.sh).</sub>

Design notes worth remembering:

- **Natural keys for time-series rows.** `daily_logs` is keyed by `log_date` and `weekly_reviews`
  by `week_start` (always a Monday) — one row per day/week, so upserts are just save-by-id. No
  surrogate id, no uniqueness constraint to manage.
- **Loose coupling for tokens.** `refresh_tokens.user_id` is a plain column (FK enforced in SQL
  with `ON DELETE CASCADE`), not a JPA `@ManyToOne` — the auth domain doesn't need object graphs.
- **`categories`** is stored as a comma-separated string, exposed as `List<String>` via
  `DailyLog.categoryList()`. Fine for a single-user app; it's the obvious first thing to normalize
  if the model grows.
- **State transitions in the entity.** `PlanItem.setStatus()` stamps/clears `completed_at` on
  entering/leaving `done`; `DailyLog.addHours(delta)` clamps at 24 (`MAX_DAY_HOURS`).

### Migrations

Schema **`grindtrack`**; Hibernate is `validate`-only, so Liquibase is the single source of truth.

- `resources/preliquibase/postgresql.sql` — `CREATE SCHEMA IF NOT EXISTS grindtrack` (runs first).
- `resources/db/changelog/db.changelog-master.yaml` includes, in order:
  - `001-users-and-tokens.sql` — `users`, `refresh_tokens` (+ `idx_refresh_tokens_user`)
  - `002-tracking.sql` — `daily_logs` (CHECK hours 0–24, energy 1–5), `weekly_reviews`
  - `003-focus-sessions.sql` — `focus_sessions` (CHECK duration 1–1440, + index)
  - `004-plan.sql` — `plan_quarters`, `plan_items` (CHECKs + index), `plan_reference`
- Every changeset has a `--rollback`. Time columns are `TIMESTAMPTZ DEFAULT now()`. **Add schema
  changes only as new changesets** — never edit an applied one.

## Configuration

`resources/application.yml`, bound where noted:

| Key | Env var | Default | Bound to |
|---|---|---|---|
| datasource url/user/pass | `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` | localhost/grind/grind | Spring |
| `grindtrack.jwt-secret` | `JWT_SECRET` | insecure placeholder | `AppProperties.jwtSecret` |
| `grindtrack.access-token-minutes` | — | `15` | `AppProperties` |
| `grindtrack.refresh-token-days` | — | `14` | `AppProperties` |
| `grindtrack.cookie-secure` | `COOKIE_SECURE` | `false` | `AppProperties` |
| `grindtrack.bootstrap-username` | `GRINDTRACK_USERNAME` | empty | `AppProperties` |
| `grindtrack.bootstrap-password` | `GRINDTRACK_PASSWORD` | empty | `AppProperties` |

Also: `spring.threads.virtual.enabled: true` (Java 21 virtual threads), `ddl-auto: validate`,
`hibernate.default_schema: grindtrack`, `server.port: 8080`. **No Spring profiles** — environment
differences are driven purely by env vars (see `.env.example`; compose also injects
`POSTGRES_USER/PASSWORD/DB` and `JAVA_TOOL_OPTIONS`).

## Build

`pom.xml`: parent `spring-boot-starter-parent:3.5.16`, Java 21. Notable deps:
`spring-boot-starter-{web,security,data-jpa,validation}`, `postgresql` (runtime), `liquibase-core`,
`net.lbruun.springboot:preliquibase-spring-boot-starter:1.6.1`, JJWT `0.12.6`
(`jjwt-api` + runtime `jjwt-impl`/`jjwt-jackson`), `commons-codec` (Base32 for TOTP).

**Spotless** (`spotless-maven-plugin`, google-java-format, GOOGLE style) binds `spotless:check` to
`verify` — CI fails on formatting drift. Run `mvn spotless:apply` to fix.

**Frontend baking** (`gt2/Dockerfile`, 3 stages): stage 1 builds the SPA to `frontend/dist`;
stage 2 does `COPY --from=ui /ui/dist ./src/main/resources/static` then `mvn package`, so the
compiled UI ends up inside `grindtrack.jar` at `classpath:/static/` and is served by Spring Boot's
default static handler. `src/main/resources/static` does **not** exist in the repo — it's
materialized only during the Docker build. Stage 3 runs `java -jar app.jar` on
`eclipse-temurin:21-jre-alpine`.

## Request lifecycle (trace)

1. **Dispatch** — request hits Tomcat on `:8080`, gets a **virtual thread**; Spring Security's
   filter chain runs first.
2. **`JwtAuthFilter`** — reads `gt_access`; on a valid JWT, populates `SecurityContextHolder`.
3. **Authorization** (`SecurityConfig`) — static assets + `/api/public/**` + login/refresh/logout
   bypass; anything else needs an authentication or the entry point writes **401** and stops.
4. **`DispatcherServlet` → controller** — e.g. `PUT /api/days/{date}` → `TrackingController`,
   which parses/validates path + body and returns `badRequest()` on failure.
5. **Service / repository** — mutations run through repositories directly or `@Transactional`
   services. Example: `FocusService.record` saves a `FocusSession` **and**, in the same
   transaction, upserts the day's `DailyLog` adding the session's minutes (÷60, rounded to 0.1 h,
   clamped to 24) — so the streak/heatmap update atomically.
6. **Response mapping** — entities → record DTOs via `from(...)`, serialized by Jackson; auth
   endpoints attach `Set-Cookie` via `ResponseCookie` (httpOnly, SameSite=Strict, secure per
   `cookieSecure`).
7. **Return** — JSON body + any cookies; the virtual thread is released.

Read-path example (`GET /api/stats`): `StatsService.compute()` loads all `daily_logs` and folds
them in-process — total hours, 12-week window totals, per-category shares (a day's hours split
evenly across its categories), and current streak by walking backward from today. It deliberately
aggregates in Java ("~350 rows/year… simpler and plenty fast") rather than in SQL.
