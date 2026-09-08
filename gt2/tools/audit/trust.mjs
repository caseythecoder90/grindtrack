/**
 * Trusted-device audit.
 *
 * A feature that waives a second factor is only as good as the things it refuses to do, so
 * most of what follows is the refusals. Each runs against a real backend: the device cookie
 * is HttpOnly, so nothing here can be checked from inside the page.
 */
import { chromium } from "playwright";
import { checker, credentials, totp } from "./lib.mjs";

const URL = process.env.GT_URL ?? "http://localhost:8080";
const { GT_CHROMIUM } = process.env;
const { username, password, secret } = credentials();

const audit = checker();
const check = audit.check;

const browser = await chromium.launch({
  executablePath: GT_CHROMIUM,
  args: ["--no-sandbox", "--no-proxy-server"],
});
const ctx = await browser.newContext({ viewport: { width: 393, height: 852 } });

// Most of this audit is deliberately-failing logins, which is precisely what LoginRateLimiter
// exists to stop -- five attempts per five minutes per IP. Left alone it answers 429 to the
// checks that matter and they fail for the wrong reason. The limiter keys off the first
// X-Forwarded-For entry, so giving each attempt its own address puts each in its own bucket.
// This is the audit stepping around a guard aimed at someone else, not a hole in the guard:
// a real client cannot choose what the ingress writes into that header.
let attempt = 0;
await ctx.route("**/api/auth/login", (route) =>
  route.continue({
    headers: { ...route.request().headers(), "x-forwarded-for": `10.42.0.${++attempt % 250}` },
  }),
);

const page = await ctx.newPage();

/** Sign in over HTTP rather than through the form, for the cases the form will not offer. */
const signIn = (body) =>
  page.evaluate(
    async (b) => {
      const r = await fetch("/api/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(b),
        credentials: "same-origin",
      });
      return r.status;
    },
    body,
  );
const deviceState = () =>
  page.evaluate(async () => (await (await fetch("/api/auth/device")).json()).trusted);

async function openLoginForm() {
  await page.goto(URL, { waitUntil: "domcontentloaded" });
  await page.click("button:has-text('Owner login')");
  await page.waitForSelector("#u");
  await page.waitForTimeout(600); // the form asks the server before it decides what to render
}

// --- an untrusted browser is asked for a code, and offered the choice ---------
await openLoginForm();
check("untrusted: the code field is shown", await page.locator("#o").count(), 1);
check("untrusted: the trust checkbox is offered", await page.locator("#trust").count(), 1);

await page.fill("#u", username);
await page.fill("#p", password);
await page.fill("#o", totp(secret));
await page.check("#trust");
await page.click("button:has-text('Sign in')");
await page.waitForSelector(".statbar", { timeout: 20000 });
check("signed in with a code", await page.locator(".statbar").count(), 1);
check("the browser is now trusted", await deviceState(), true);

// --- the refusals ------------------------------------------------------------
// A device token is not a credential. This is the check that matters most: if it ever
// passes a wrong password, the feature has turned the second factor into the only factor.
check("a trusted device does NOT rescue a wrong password",
  await signIn({ username, password: "definitely-wrong", otp: "" }), 401);

// --- logging out does not forget the device ----------------------------------
// Signing back in without reaching for the phone is the entire point of the feature.
await page.evaluate(() => fetch("/api/auth/logout", { method: "POST", credentials: "same-origin" }));
await openLoginForm();
check("trusted: the code field is gone", await page.locator("#o").count(), 0);
check("trusted: so is the checkbox", await page.locator("#trust").count(), 0);
check("trusted: the form says why", /remembered/i.test(await page.locator(".login-card").innerText()), true);

await page.fill("#u", username);
await page.fill("#p", password);
await page.click("button:has-text('Sign in')");
await page.waitForSelector(".statbar", { timeout: 20000 });
check("password alone signs in on a trusted device", await page.locator(".statbar").count(), 1);

// --- and it can be taken back ------------------------------------------------
const forgotten = await page.evaluate(async () =>
  (await (await fetch("/api/auth/devices/forget", { method: "POST", credentials: "same-origin" })).json()).count);
check("forgetting reports what it revoked", forgotten >= 1, true);
check("the browser is no longer trusted", await deviceState(), false);
check("and a code is required again", await signIn({ username, password, otp: "" }), 401);
check("while the right code still works", await signIn({ username, password, otp: totp(secret) }), 200);

await browser.close();
console.log(audit.failures ? `\n${audit.failures} FAILED` : "\nall checks passed");
process.exit(audit.failures ? 1 : 0);
