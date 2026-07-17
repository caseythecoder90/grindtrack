# Authentication

Two factors, three tokens, all state in httpOnly cookies. This document explains each decision so
the design can be defended (and reused at work).

## The factors

1. **Password** — stored as a BCrypt hash (`BCryptPasswordEncoder`, cost 10). BCrypt is slow on
   purpose: brute-forcing a leaked hash is expensive.
2. **TOTP (RFC 6238)** — a 6-digit code from an authenticator app. Implemented by hand in
   `TotpService` so the mechanism is visible: `HMAC-SHA1(secret, floor(unix_time / 30))`,
   dynamic truncation (RFC 4226 §5.3), mod 10⁶. A ±1 step window tolerates clock drift.
   The secret is generated at first boot and printed **once** as an `otpauth://` URI.

Login failures return the same 401 regardless of which factor failed — an attacker learns
nothing about whether the username exists or the password was close.

## The tokens

| Token | Form | Lifetime | Where | Why this form |
|---|---|---|---|---|
| Access | JWT (HS256) | 15 min | `gt_access` cookie, `Path=/` | Stateless verification on every request — no DB hit |
| Refresh | Opaque random 256-bit string | 14 days | `gt_refresh` cookie, `Path=/api/auth` | Deliberately **not** a JWT: stored (hashed) server-side so it can be revoked and rotated |

The asymmetry is the core lesson: **JWTs can't be revoked** (they're valid until expiry, full
stop), so anything long-lived must be a server-side record. Short-lived JWT + revocable refresh
token gets both properties: cheap request auth and a real kill switch.

Refresh tokens are stored as SHA-256 hashes — a database leak yields nothing usable.

## Cookie decisions

All auth cookies are `HttpOnly` (JavaScript can never read them — XSS can't exfiltrate tokens,
which is why localStorage is the wrong place), `Secure` (HTTPS only; toggle off via
`COOKIE_SECURE=false` for local http dev), and `SameSite=Strict` (the browser refuses to attach
them to any cross-site request — this is the CSRF defense, which is why Spring's CSRF token
machinery is disabled). The refresh cookie is additionally scoped to `Path=/api/auth` so it is
only ever transmitted to the auth endpoints, not on every API call.

## Login flow

```plantuml
@startuml
actor User
participant "React SPA" as SPA
participant AuthController as AC
participant AuthService as AS
participant TotpService as TOTP
database Postgres as DB

User -> SPA : username + password + 6-digit code
SPA -> AC : POST /api/auth/login
AC -> AC : rate limiter (5 / 5 min / IP)
AC -> AS : authenticate(username, password, otp)
AS -> DB : find user by username
AS -> AS : BCrypt.matches(password, hash)
AS -> TOTP : verify(secret, otp) [±1 step]
AS --> AC : user
AC -> AS : issueRefreshToken(user)
AS -> DB : INSERT refresh_tokens (SHA-256 hash, +14d)
AC --> SPA : 200 + Set-Cookie: gt_access (JWT, 15m)\n+ Set-Cookie: gt_refresh (opaque, 14d)
@enduml
```

## Authenticated request

```plantuml
@startuml
participant "React SPA" as SPA
participant JwtAuthFilter as F
participant "SecurityFilterChain" as SEC
participant TrackingController as TC

SPA -> F : GET /api/stats (Cookie: gt_access)
F -> F : Jwts.parser().verifyWith(key)\n→ signature + expiry check
F -> SEC : SecurityContext = authenticated(subject)
SEC -> TC : request proceeds
TC --> SPA : 200 JSON
@enduml
```

If the JWT is missing/expired/invalid, the filter sets nothing, the chain's authorization rules
reject the request, and the entry point returns a bare 401.

## Refresh with rotation

```plantuml
@startuml
participant "React SPA" as SPA
participant AuthController as AC
participant AuthService as AS
database Postgres as DB

SPA -> AC : POST /api/auth/refresh (Cookie: gt_refresh)
AC -> AS : rotate(presentedToken)
AS -> DB : SELECT by SHA-256(token)\nWHERE not revoked AND not expired
AS -> DB : UPDATE old row → revoked = true
AS -> DB : INSERT new token row (+14d)
AS --> AC : user + new refresh token
AC --> SPA : 200 + fresh gt_access + fresh gt_refresh
@enduml
```

**Why rotate?** Each refresh token is single-use. If a token is stolen and the thief uses it,
the legitimate client's next refresh presents an already-revoked token — theft becomes
*detectable*, and the server acts on the signal: presenting a revoked token revokes **all** of
that user's live refresh tokens, forcing a fresh password+TOTP login everywhere. A non-rotating
refresh token can be silently replayed for its whole lifetime.

The SPA side (see `frontend/src/lib/api.ts`): any 401 (except from login itself, where it means
bad credentials) triggers one refresh attempt and a replay of the original request; if the
refresh also 401s, the app drops to the login screen. Concurrent 401s share a single in-flight
refresh so the rotating token is never raced.

## Logout

`POST /api/auth/logout` revokes the presented refresh token in the database and expires both
cookies. The access token technically remains valid for up to its remaining ≤15 minutes — that
window is the accepted trade-off of stateless access tokens, and is why the access TTL is short.

## Bootstrap

On first startup with an empty `users` table, `UserBootstrap` creates the user from
`GRINDTRACK_USERNAME` / `GRINDTRACK_PASSWORD` and logs the TOTP secret + provisioning URI once:

```
docker compose logs app | grep -A4 "Bootstrap user"
```

Add the secret to your authenticator app (manual entry, or paste the `otpauth://` URI into any
QR generator and scan it), then treat those log lines as sensitive and clear them
(`docker compose logs` reads from the container; recreate the container after setup if you want
the trace gone: `docker compose up -d --force-recreate app`).

## Threat-model notes (honest edges)

- **XSS**: httpOnly cookies protect the tokens, not the session — injected script could still
  call the API as you while the page is open. React's escaping + no `dangerouslySetInnerHTML`
  is the real defense.
- **Access-token window after logout**: ≤15 min, by design; see Logout.
- **Rate limiter is in-memory**: resets on restart, per-instance. Fine for one user on one node;
  a multi-instance deployment would move it to Redis (the Stripe post's production shape).
- **No account lockout**: the TOTP factor makes online guessing impractical; the rate limiter
  handles volume.
