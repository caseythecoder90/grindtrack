# Touch-target audit

Measures every interactive element in the **running** app and fails on anything below the
44px touch floor, or any field whose type is small enough to make iOS zoom the viewport.

It lives here, in its own package, rather than in `frontend/`. Playwright downloads a
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
npm run audit
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
