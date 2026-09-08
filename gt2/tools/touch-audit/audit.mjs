/**
 * Measures every interactive element in the running app and fails on anything
 * below the 44px touch floor.
 *
 * The floor lives in a `@media (pointer: coarse)` block at the end of styles.css.
 * Reading that block does not tell you whether it worked: a `min-height` loses to
 * a more specific rule further up the file, a `min-height` on an input beats a
 * checkbox's `height` and stretches it, and a row can be 52px tall while the label
 * that actually toggles it is 28px. All three of those shipped and all three were
 * found here, by measuring the rendered page.
 *
 *   npx playwright install chromium     # once
 *   GT_TOTP_SECRET=... npm run audit:touch
 *
 * Environment:
 *   GT_URL            base URL (default http://localhost:8080)
 *   GT_USERNAME       login (default: the bootstrap user)
 *   GT_PASSWORD       login
 *   GT_TOTP_SECRET    base32 secret printed once by UserBootstrap on first boot
 *   GT_CHROMIUM       path to an existing chromium, instead of Playwright's download
 *
 * Not in CI: it needs a running app and real credentials. Run it after changing
 * anything about control sizing.
 */

import crypto from "node:crypto";
import { chromium, devices } from "playwright";

const URL = process.env.GT_URL ?? "http://localhost:8080";
const USERNAME = process.env.GT_USERNAME;
const PASSWORD = process.env.GT_PASSWORD;
const SECRET = process.env.GT_TOTP_SECRET;
const FLOOR = 44;

/** The tabs to walk, in nav order. */
const TABS = ["today", "focus", "cal", "todos", "plan", "work", "money", "us", "week", "stats"];

/**
 * Anything a finger is meant to hit. `.chip` and `.linkish` are buttons already,
 * but naming them keeps the list readable against the stylesheet.
 */
const INTERACTIVE = 'button, a[href], input, select, textarea, [role="button"], .chip, .linkish';

if (!USERNAME || !PASSWORD || !SECRET) {
  console.error("Set GT_USERNAME, GT_PASSWORD and GT_TOTP_SECRET. See the header of this file.");
  process.exit(2);
}

/** RFC 6238, so the run does not need an authenticator app in the loop. */
function totp(base32) {
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  let bits = "";
  for (const c of base32.toUpperCase().replace(/=+$/, "")) {
    const i = alphabet.indexOf(c);
    if (i < 0) throw new Error(`GT_TOTP_SECRET is not base32: ${c}`);
    bits += i.toString(2).padStart(5, "0");
  }
  const bytes = bits.slice(0, Math.floor(bits.length / 8) * 8).match(/.{8}/g) ?? [];
  const key = Buffer.from(bytes.map((b) => parseInt(b, 2)));
  const counter = Buffer.alloc(8);
  counter.writeUInt32BE(Math.floor(Date.now() / 1000 / 30), 4);
  const mac = crypto.createHmac("sha1", key).update(counter).digest();
  const offset = mac[mac.length - 1] & 0x0f;
  return String((mac.readUInt32BE(offset) & 0x7fffffff) % 1e6).padStart(6, "0");
}

// GT_CHROMIUM points at an existing browser instead of Playwright's own download —
// useful in a container that already ships one.
const browser = await chromium.launch({
  executablePath: process.env.GT_CHROMIUM || undefined,
  args: ["--no-sandbox", "--no-proxy-server"],
});
const context = await browser.newContext(devices["iPhone 14 Pro"]);
const page = await context.newPage();

try {
  await page.goto(URL, { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(1500);

  const coarse = await page.evaluate(() => matchMedia("(pointer: coarse)").matches);
  if (!coarse) throw new Error("the emulated device did not report pointer:coarse — nothing to audit");

  await page.getByRole("button", { name: "Owner login" }).click();
  await page.waitForSelector("#u");
  await page.fill("#u", USERNAME);
  await page.fill("#p", PASSWORD);
  await page.fill("#o", totp(SECRET));
  await page.getByRole("button", { name: "Sign in" }).click();
  await page.waitForSelector(".bottomnav", { timeout: 15000 });
  await page.waitForTimeout(1200);

  /**
   * Reach a tab the way a thumb would. Four sections are in the bar; the rest are
   * behind "more", whose own label becomes the open section's name — so match the
   * primaries by position rather than by text, or "money" matches the more button.
   */
  async function goTo(tab) {
    const bar = page.locator(".bottomnav button");
    const slots = await bar.count();
    for (let i = 0; i < slots - 1; i += 1) {
      if ((await bar.nth(i).innerText()).trim() === tab) {
        await bar.nth(i).click();
        await page.waitForTimeout(700);
        return;
      }
    }
    await bar.nth(slots - 1).click();
    await page.waitForTimeout(400);
    await page.locator(".sheet-item", { hasText: new RegExp(`^${tab}$`) }).click();
    await page.waitForTimeout(700);
  }

  let checked = 0;
  const failures = [];

  for (const tab of TABS) {
    await goTo(tab);
    // The plan tab hides most of its rows behind a disclosure; open it or the
    // densest screen in the app is measured almost empty.
    if (tab === "plan") {
      const roadmap = page.locator("button.linkish", { hasText: /quarter roadmap/ });
      if (await roadmap.count()) {
        await roadmap.first().click();
        await page.waitForTimeout(500);
      }
    }

    const found = await page.evaluate(
      ({ selector, floor }) => {
        const rows = [];
        for (const el of document.querySelectorAll(selector)) {
          const box = el.getBoundingClientRect();
          const style = getComputedStyle(el);
          if (!box.width || !box.height) continue;
          if (style.visibility === "hidden" || style.display === "none") continue;
          // A native checkbox cannot be grown by padding — width and height are
          // its only handles, and the label wrapping it is the real target. Judge
          // that label instead, and flag the box only if it has no label.
          if (el.type === "checkbox") {
            const label = el.closest("label");
            if (label) continue;
          }
          rows.push({
            tag: el.tagName.toLowerCase(),
            cls: String(el.className || "").slice(0, 32),
            text: String(el.innerText || el.value || el.type || "").trim().slice(0, 28),
            h: Math.round(box.height),
            w: Math.round(box.width),
            fs: parseFloat(style.fontSize),
            short: box.height < floor - 0.5,
            // iOS zooms the viewport when a focused field is under 16px.
            zoomy: ["input", "textarea", "select"].includes(el.tagName.toLowerCase())
              && parseFloat(style.fontSize) < 16,
          });
        }
        return rows;
      },
      { selector: INTERACTIVE, floor: FLOOR },
    );

    checked += found.length;
    const bad = found.filter((r) => r.short || r.zoomy);
    for (const b of bad) failures.push({ tab, ...b });
    const flag = bad.length ? "FAIL" : "ok  ";
    console.log(`${flag} ${tab.padEnd(6)} ${String(found.length).padStart(3)} interactive, ${bad.length} failing`);
  }

  // The sheet is a screen of its own and is only reachable while open.
  await page.locator(".bottomnav button").last().click();
  await page.waitForTimeout(400);
  const sheet = await page.evaluate(
    ({ floor }) =>
      [...document.querySelectorAll(".sheet button")].map((el) => {
        const box = el.getBoundingClientRect();
        return {
          tag: "button",
          cls: String(el.className || "").slice(0, 32),
          text: el.innerText.trim().slice(0, 28),
          h: Math.round(box.height),
          short: box.height < floor - 0.5,
          zoomy: false,
        };
      }),
    { floor: FLOOR },
  );
  checked += sheet.length;
  const sheetBad = sheet.filter((r) => r.short);
  for (const b of sheetBad) failures.push({ tab: "sheet", ...b });
  console.log(`${sheetBad.length ? "FAIL" : "ok  "} ${"sheet".padEnd(6)} ${String(sheet.length).padStart(3)} interactive, ${sheetBad.length} failing`);

  console.log(`\n${checked} interactive elements measured across ${TABS.length} tabs and the more sheet`);
  if (failures.length) {
    console.log(`\n${failures.length} below the ${FLOOR}px floor or under 16px type:\n`);
    for (const f of failures) {
      const why = f.short ? `${f.h}px tall` : `${f.fs}px type (iOS will zoom)`;
      console.log(`  ${f.tab.padEnd(6)} ${why.padEnd(24)} ${f.tag}.${f.cls}  "${f.text}"`);
    }
    process.exitCode = 1;
  } else {
    console.log(`all at or above ${FLOOR}px, no field under 16px`);
  }
} finally {
  await browser.close();
}
