/**
 * Session-durability audit.
 *
 * Drives the running app through the four failures that produced "session expired" on a
 * screen where nothing had expired, and asserts the behaviour that replaced each of them.
 * Every check is measured against a real backend with real cookies and real rotation --
 * the bug being guarded here was invisible to unit tests because it lived in the seam
 * between an expired access cookie, a refresh that answered 502, and a write nobody
 * retried.
 */
import { chromium } from "playwright";
import { checker, credentials, totp } from "./lib.mjs";

const URL = process.env.GT_URL ?? "http://localhost:8080";
const { GT_CHROMIUM } = process.env;
const { username, password, secret } = credentials();

const today = () => new Date().toISOString().slice(0, 10);

const audit = checker();
const check = audit.check;

const browser = await chromium.launch({
  executablePath: GT_CHROMIUM,
  args: ["--no-sandbox", "--no-proxy-server"],
});
const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
const page = await ctx.newPage();
const rejections = [];
page.on("pageerror", (e) => rejections.push(e.message));

await page.goto(URL, { waitUntil: "domcontentloaded" });
await page.click("button:has-text('Owner login')");
await page.fill("#u", username);
await page.fill("#p", password);
await page.fill("#o", totp(secret));
await page.click("button:has-text('Sign in')");
await page.waitForSelector(".statbar", { timeout: 20000 });
await page.click(".tabs button:has-text('focus')");
await page.waitForSelector("text=focus timer");

// This audit writes a focus session, so everything below is a delta on whatever the day
// already holds rather than an absolute count.
const onServer = () =>
  page.evaluate(async (d) =>
    (await (await fetch(`/api/focus/sessions?date=${d}&kind=study`)).json()).length, today());
const baseline = await onServer();

// --- 1. an unreachable server queues the block rather than losing it --------
// Seeded rather than waited out: the shortest block the UI offers is five minutes, and
// this is the real path anyway -- a timer whose deadline passed while the tab was closed
// resolves on mount and records the session it owes.
await page.route("**/api/focus/sessions", (route) =>
  route.request().method() === "POST"
    ? route.fulfill({ status: 502, contentType: "text/html", body: "502 Bad Gateway" })
    : route.continue());
await page.evaluate((startedAt) => {
  localStorage.setItem("gt-focus-timer-v1", JSON.stringify({
    phase: "focus", sessionIndex: 0, endsAt: Date.now() - 1000, remainingMs: null, startedAt,
    config: { sessions: 1, focusMin: 5, breakMin: 5, kind: "study", planItemId: null, topic: "" },
  }));
}, new Date(Date.now() - 5 * 60000).toISOString());
await page.reload({ waitUntil: "domcontentloaded" });
await page.waitForSelector(".statbar", { timeout: 20000 });
await page.click(".tabs button:has-text('focus')");
await page.waitForTimeout(1500);

const outbox = () => page.evaluate(() => JSON.parse(localStorage.getItem("gt-focus-outbox") ?? "[]"));
const queued = await outbox();
check("the block is queued, not lost", queued.length, 1);
check("queued with its real kind", queued[0]?.kind, "study");
check("queued with its full duration", queued[0]?.durationMinutes, 5);
check("the queue is shown", await page.locator(".pending-note").count(), 1);
check("and not shown as an error", (await page.locator(".error").allTextContents()).filter(Boolean), []);
check("still signed in", await page.locator(".statbar").count(), 1);
check("nothing reached the server", (await onServer()) - baseline, 0);

// --- 2. it drains when the server comes back --------------------------------
await page.unroute("**/api/focus/sessions");
await page.evaluate(() => document.dispatchEvent(new Event("visibilitychange")));
await page.waitForTimeout(2000);
check("the queue drained on resume", (await outbox()).length, 0);
check("the note is gone", await page.locator(".pending-note").count(), 0);
check("the session reached the server", (await onServer()) - baseline, 1);

// --- 3. a replay of something already saved must not double it --------------
// The failure being guarded: a write can be committed with only its response lost, so a
// blind retry would count the hours twice. Inflating the study total is worse than the
// bug being fixed.
const before = await onServer();
await page.evaluate((s) => localStorage.setItem("gt-focus-outbox", JSON.stringify([s])), queued[0]);
await page.waitForTimeout(3200); // useAppResume throttles duplicate signals for 3s
await page.evaluate(() => document.dispatchEvent(new Event("visibilitychange")));
await page.waitForTimeout(2000);
check("replaying a saved session adds nothing", await onServer(), before);
check("and still clears the queue", (await outbox()).length, 0);

// --- 4. a 502 from refresh is not an expired session ------------------------
// The access cookie is deleted the way an hour of a running timer deletes it.
const cookies = await ctx.cookies();
await ctx.clearCookies();
await ctx.addCookies(cookies.filter((c) => c.name === "gt_refresh"));
await page.route("**/api/auth/refresh", (route) => route.fulfill({ status: 502, body: "bad gateway" }));
await page.click(".tabs button:has-text('today')");
await page.waitForTimeout(1500);
check("no bogus 'session expired'", /session expired/i.test(await page.locator("body").innerText()), false);
check("not thrown back to the login screen", await page.locator("button:has-text('Sign in')").count(), 0);

// --- 5. a real 401 from refresh still ends the session ----------------------
await page.unroute("**/api/auth/refresh");
await page.waitForTimeout(3200);
await page.route("**/api/auth/refresh", (route) => route.fulfill({
  status: 401, contentType: "application/json", body: JSON.stringify({ error: "Refresh token invalid." }) }));
await page.evaluate(() => document.dispatchEvent(new Event("visibilitychange")));
await page.waitForTimeout(2000);
check("a genuine 401 does log you out",
  (await page.locator("button:has-text('Owner login')").count()) > 0, true);

check("no unhandled rejections", rejections, []);

await browser.close();
console.log(audit.failures ? `\n${audit.failures} FAILED` : "\nall checks passed");
process.exit(audit.failures ? 1 : 0);
