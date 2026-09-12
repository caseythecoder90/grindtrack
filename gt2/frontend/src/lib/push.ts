/**
 * Web Push from the browser's side: what state this device is in, and the two things it can do.
 *
 * <p>Everything here is the platform's — `PushManager`, `Notification`, the service worker — and
 * the edges are the platform's too. There is no push in Firefox on iOS or in Safari in the browser
 * proper; on an iPhone the app must be installed to the home screen first, and the permission
 * prompt appears only from a tap. So this module answers one question honestly (`pushState`) and
 * keeps `subscribePush` free of anything that would spend the tap before the prompt.
 */

export type PushState =
  /** No push here at all. Firefox on iOS, an old browser. The panel does not render. */
  | "unsupported"
  /** iOS, in Safari rather than the installed app: push exists once it is on the home screen. */
  | "needs-install"
  /** The person said no once; only the browser's settings can undo that. */
  | "blocked"
  | "off"
  | "on";

export interface PushStatus {
  state: PushState;
  /** This browser's subscription endpoint when `on`: how it recognises its own row. */
  endpoint: string | null;
}

/** What the browser hands back from `pushManager.subscribe`, in the shape the server stores. */
export interface BrowserSubscription {
  endpoint: string;
  keys: { p256dh: string; auth: string };
}

function isIos(): boolean {
  // iPadOS reports itself as a Mac; the touch points give it away.
  return (
    /iPhone|iPad|iPod/.test(navigator.userAgent) ||
    (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1)
  );
}

function isInstalled(): boolean {
  return (
    window.matchMedia?.("(display-mode: standalone)").matches ||
    (navigator as { standalone?: boolean }).standalone === true
  );
}

export function pushSupported(): boolean {
  return "serviceWorker" in navigator && "PushManager" in window && "Notification" in window;
}

export async function pushState(): Promise<PushStatus> {
  if (!pushSupported()) {
    return { state: isIos() && !isInstalled() ? "needs-install" : "unsupported", endpoint: null };
  }
  if (Notification.permission === "denied") {
    return { state: "blocked", endpoint: null };
  }
  const registration = await navigator.serviceWorker.getRegistration();
  const subscription = registration ? await registration.pushManager.getSubscription() : null;
  return subscription
    ? { state: "on", endpoint: subscription.endpoint }
    : { state: "off", endpoint: null };
}

/**
 * Ask, then subscribe. Must be called from a click with nothing awaited before it: the permission
 * prompt is the first thing that happens here, because iOS shows it only inside the tap that asked,
 * and a prompt raised any other way is refused silently and stays refused.
 *
 * @param publicKey the server's VAPID public key, base64url, fetched before the tap
 */
export async function subscribePush(publicKey: string): Promise<BrowserSubscription> {
  const permission = await Notification.requestPermission();
  if (permission !== "granted") {
    throw new Error(
      permission === "denied"
        ? "notifications are blocked — allow them for grindtrack in the browser's settings"
        : "notifications were not allowed",
    );
  }
  const registration =
    (await navigator.serviceWorker.getRegistration()) ??
    (await navigator.serviceWorker.register("/sw.js"));
  await navigator.serviceWorker.ready;
  const subscription = await registration.pushManager.subscribe({
    userVisibleOnly: true,
    applicationServerKey: keyBytes(publicKey),
  });
  const json = subscription.toJSON();
  if (!json.endpoint || !json.keys?.p256dh || !json.keys?.auth) {
    await subscription.unsubscribe();
    throw new Error("the browser produced a subscription without keys");
  }
  return { endpoint: json.endpoint, keys: { p256dh: json.keys.p256dh, auth: json.keys.auth } };
}

/** Drop this browser's subscription. The server row is the caller's to delete first. */
export async function unsubscribePush(): Promise<void> {
  const registration = await navigator.serviceWorker.getRegistration();
  const subscription = registration ? await registration.pushManager.getSubscription() : null;
  if (subscription) {
    await subscription.unsubscribe();
  }
}

/** "iPhone · Safari" — enough to tell the rows apart in a list, nothing that identifies a person. */
export function deviceLabel(): string {
  const ua = navigator.userAgent;
  const device = /iPhone/.test(ua)
    ? "iPhone"
    : /iPad/.test(ua) || (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1)
      ? "iPad"
      : /Android/.test(ua)
        ? "Android"
        : /Mac/.test(ua)
          ? "Mac"
          : /Windows/.test(ua)
            ? "Windows"
            : /Linux/.test(ua)
              ? "Linux"
              : "a device";
  const browser = /Edg\//.test(ua)
    ? "Edge"
    : /Firefox\//.test(ua)
      ? "Firefox"
      : /Chrome\//.test(ua)
        ? "Chrome"
        : /Safari\//.test(ua)
          ? "Safari"
          : "browser";
  return `${device} · ${browser}${isInstalled() ? " (installed)" : ""}`;
}

/** base64url → the bytes `applicationServerKey` wants. */
function keyBytes(base64url: string): Uint8Array<ArrayBuffer> {
  const padded = base64url + "=".repeat((4 - (base64url.length % 4)) % 4);
  const binary = atob(padded.replace(/-/g, "+").replace(/_/g, "/"));
  const bytes = new Uint8Array(new ArrayBuffer(binary.length));
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return bytes;
}
