/**
 * Service worker registration.
 *
 * Kept out of main.tsx so the render path stays two lines, and deliberately fire-and-forget:
 * the worker only makes the app open faster, so nothing here may keep it from starting.
 * Registration is skipped in dev, where Vite serves modules that must not be cached.
 */
export function registerServiceWorker(): void {
  if (!import.meta.env.PROD || !("serviceWorker" in navigator)) return;

  // After load, so registering never competes with the first render for bandwidth.
  window.addEventListener("load", () => {
    navigator.serviceWorker.register("/sw.js").catch(() => {
      /* An unregistered worker costs nothing but a slower open — never surface it. */
    });
  });
}
