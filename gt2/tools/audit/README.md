# Browser audits

Three audits that drive the **running** app and fail on what a unit test cannot see. All
need a real instance, real cookies and real credentials, so none of them runs in CI.

| script | what it guards |
|---|---|
| `npm run touch` | every interactive element against the 44px touch floor |
| `npm run session` | that a bad minute of network never costs you a focus session |
| `npm run trust` | that remembering a device never becomes a way in without the password |

They live here, in their own package, rather than in `frontend/`. Playwright downloads a
browser on install, and the frontend's `npm ci` runs on every CI build — a 150 MB download
on each of those, for a tool CI never invokes, is not a trade worth making. Installing it
is opt-in.

## Why measure instead of reading the stylesheet

The floor is a `@media (pointer: coarse)` block at the end of `styles.css`. Reading it does
not tell you whether it worked. Three real bugs were found here that the CSS looked fine for:

- A `min-height` on the general `input` rule beats a checkbox's `height` and stretches it
  into a 22×44 rectangle.
- Two fields set a smaller `font-size` earlier in the file and win on specificity, so they
  keep triggering the iOS zoom.
- A todo row is 52px tall while the label that actually toggles it is 28px, with dead space
  above and below that looks tappable and is not.

## Running it

```
npm install
npx playwright install chromium

GT_TOTP_SECRET=<base32> \
GT_USERNAME=<user> \
GT_PASSWORD=<pass> \
npm run touch      # or: npm run session
```

| variable | default | notes |
|---|---|---|
| `GT_URL` | `http://localhost:8080` | point it at a local jar or at production |
| `GT_USERNAME` / `GT_PASSWORD` | — | required |
| `GT_TOTP_SECRET` | — | the base32 secret `UserBootstrap` logs once on first boot |
| `GT_CHROMIUM` | — | path to an existing chromium, instead of Playwright's own download |

Exits non-zero when anything fails, so it can gate a release by hand. It is deliberately
not in CI: it needs a running app and real credentials.

## What it does not check

Horizontal size. A 44×20 control passes, because height is what fails in this app and a
width floor would flag every chip in a row. Add it if that changes.

## The session audit

`session.mjs` reproduces the failure that made "session expired" appear on a screen where
nothing had expired, and asserts what replaced it.

The bug lived in a seam no unit test covers. A focus timer makes no requests for the hour
it runs while the access cookie lasts fifteen minutes, so every long block ends by
refreshing. `refreshOnce` returned `r.ok`, so a 502 from a pod being replaced answered
"your session is over" — and the completed block, whose POST had just failed, was dropped
on the floor with a red line of text and no retry.

Five things it checks, each against a real backend:

1. A 502 on the save queues the block rather than losing it, and says so in amber rather
   than red.
2. Coming back to the foreground drains the queue to the server.
3. Replaying a session the server already has adds nothing. A write can be committed with
   only its response lost, and double-counting the hours would be worse than the bug.
4. A 502 from `/api/auth/refresh` is not an expired session and does not reach the login
   screen.
5. A genuine 401 from `/api/auth/refresh` still logs you out.

It writes one five-minute study session to whatever instance you point it at, so run it
against a local jar rather than production unless you want that row in your history. The
timer state is seeded through localStorage rather than waited out: the shortest block the
UI offers is five minutes, and a deadline that passed while the tab was closed is the real
path anyway.

## The trusted-device audit

`trust.mjs` covers "trust this device", which skips the authenticator code on a browser that
has already presented one. The password is unchanged and still required.

A feature that waives a second factor is only as good as the things it refuses to do, so most
of the audit is refusals. The one that matters most is that a trusted device presenting the
**wrong password** still gets 401 — if that ever passes, the feature has quietly turned the
second factor into the only factor. It also checks that a browser is asked for a code until it
is trusted, that logging out does not forget the device (signing back in without reaching for
the phone is the whole point), that forgetting reports what it revoked, and that afterwards a
code is demanded again while the right code still works.

The device cookie is HttpOnly, so none of this is checkable from inside the page; every
assertion goes through the server.

It signs in several times on purpose, including failures, which is exactly what
`LoginRateLimiter` exists to stop. The audit gives each attempt its own `X-Forwarded-For` so
each lands in its own bucket. That is the audit stepping around a guard aimed at someone else
— a real client cannot choose what the ingress writes into that header.
