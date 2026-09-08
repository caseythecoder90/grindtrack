/** Shared by the audits: a TOTP code, and a check that prints and counts. */
import { createHmac } from "node:crypto";

/** RFC 6238, six digits, thirty-second step — the same scheme TotpService implements. */
export function totp(base32) {
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  let bits = "";
  for (const ch of base32.replace(/=+$/, "").toUpperCase()) {
    bits += alphabet.indexOf(ch).toString(2).padStart(5, "0");
  }
  const key = Buffer.from((bits.match(/.{8}/g) ?? []).map((b) => parseInt(b, 2)));
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(Math.floor(Date.now() / 1000 / 30)));
  const mac = createHmac("sha1", key).update(counter).digest();
  const offset = mac[19] & 0x0f;
  return String((mac.readUInt32BE(offset) & 0x7fffffff) % 1e6).padStart(6, "0");
}

export function checker() {
  let failures = 0;
  return {
    check(name, actual, expected) {
      const ok = JSON.stringify(actual) === JSON.stringify(expected);
      if (!ok) failures++;
      console.log(
        `${ok ? "PASS" : "FAIL"} ${name.padEnd(52, ".")} ` +
          `got=${JSON.stringify(actual)} want=${JSON.stringify(expected)}`,
      );
    },
    get failures() {
      return failures;
    },
  };
}

/** Every audit needs the same three, and none of them has a sensible default. */
export function credentials() {
  const { GT_USERNAME, GT_PASSWORD, GT_TOTP_SECRET } = process.env;
  if (!GT_USERNAME || !GT_PASSWORD || !GT_TOTP_SECRET) {
    console.error("GT_USERNAME, GT_PASSWORD and GT_TOTP_SECRET are required. See README.md.");
    process.exit(2);
  }
  return { username: GT_USERNAME, password: GT_PASSWORD, secret: GT_TOTP_SECRET };
}
