/*
 * Grindtrack service worker.
 *
 * Its whole job is making the app open fast and survive a flaky connection. It is
 * deliberately NOT an offline mode: every screen renders server data, so an app that
 * opened without a network would have nothing to show.
 *
 * The rule that matters: /api is never intercepted. Auth rides in httpOnly cookies with
 * a rotating refresh token, and the responses are the most personal data in the app —
 * neither belongs in Cache Storage, and a stale authed response served from a cache is a
 * bug with no upside. Those requests fall through to the network untouched, exactly as if
 * no worker were installed.
 *
 * BUILD_ID is stamped in by the vite config at build time (see pwaBuildId), so a deploy
 * always produces a new cache and the old one is dropped on activate.
 */

const BUILD_ID = "__BUILD_ID__";
const SHELL_CACHE = `gt-shell-${BUILD_ID}`;
const ASSET_CACHE = `gt-assets-${BUILD_ID}`;
const SHELL_URL = "/index.html";

self.addEventListener("install", (event) => {
  // Take over as soon as the new worker is ready rather than waiting for every tab to
  // close. Safe here because the worker caches no data — the worst case is a page that
  // keeps running already-loaded assets until its next navigation.
  event.waitUntil(
    caches.open(SHELL_CACHE).then((c) => c.add(SHELL_URL)).then(() => self.skipWaiting()),
  );
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((names) =>
        Promise.all(
          names
            .filter((n) => n.startsWith("gt-") && n !== SHELL_CACHE && n !== ASSET_CACHE)
            .map((n) => caches.delete(n)),
        ),
      )
      .then(() => self.clients.claim()),
  );
});

/** Static files this worker is willing to cache. Everything else goes to the network. */
function isCacheableAsset(url) {
  return (
    url.pathname.startsWith("/assets/") ||
    /\.(?:png|svg|ico|webmanifest|woff2?)$/.test(url.pathname)
  );
}

self.addEventListener("fetch", (event) => {
  const { request } = event;
  if (request.method !== "GET") return;

  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return;
  if (url.pathname.startsWith("/api/")) return; // see the header comment
  if (url.pathname === "/sw.js") return;

  // Navigations: network first, so a deploy is picked up immediately, with the cached
  // shell as the fallback that makes the app open on a bad connection.
  if (request.mode === "navigate") {
    event.respondWith(
      fetch(request)
        .then((res) => {
          const copy = res.clone();
          caches.open(SHELL_CACHE).then((c) => c.put(SHELL_URL, copy));
          return res;
        })
        .catch(() => caches.match(SHELL_URL).then((hit) => hit ?? Response.error())),
    );
    return;
  }

  if (!isCacheableAsset(url)) return;

  // Assets: stale-while-revalidate. Vite hashes the filenames, so a cache hit is always
  // the right bytes for that URL and the background refresh only matters for the
  // unhashed ones (icons, the manifest).
  event.respondWith(
    caches.open(ASSET_CACHE).then((cache) =>
      cache.match(request).then((hit) => {
        const fetching = fetch(request)
          .then((res) => {
            if (res.ok) cache.put(request, res.clone());
            return res;
          })
          .catch(() => hit);
        return hit ?? fetching;
      }),
    ),
  );
});

/*
 * --- push ---------------------------------------------------------------------
 * The worker is where a push must be handled: the page may not be open, and on a phone the
 * app is usually not running. The server encrypted the payload to this browser's keys and
 * the browser decrypted it before this event fires; what arrives here is the JSON the server
 * wrote: {title, body, tab, tag}. Anything else is shown as plain text rather than dropped,
 * because a notification the server sent and the phone swallowed is the worst outcome.
 */
self.addEventListener("push", (event) => {
  let data = {};
  try {
    data = event.data ? event.data.json() : {};
  } catch {
    data = { title: "grindtrack", body: event.data ? event.data.text() : "" };
  }
  event.waitUntil(
    self.registration.showNotification(data.title || "grindtrack", {
      body: data.body || "",
      icon: "/icon-192.png",
      badge: "/icon-192.png",
      // Same tag replaces: a redrafted brief updates the morning's notification, not stacks on it.
      tag: data.tag || undefined,
      data: { tab: data.tab || "today" },
    }),
  );
});

/* A tap lands on the tab the notification named: in an open window if there is one, else a new one. */
self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const tab = (event.notification.data && event.notification.data.tab) || "today";
  event.waitUntil(
    self.clients.matchAll({ type: "window", includeUncontrolled: true }).then((clients) => {
      const open = clients.find((c) => "focus" in c);
      if (open) {
        open.postMessage({ type: "open-tab", tab });
        return open.focus();
      }
      return self.clients.openWindow("/?tab=" + encodeURIComponent(tab));
    }),
  );
});
