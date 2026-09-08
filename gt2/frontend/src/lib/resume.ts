/**
 * Run something when the app comes back to the foreground.
 *
 * <p>Every screen loaded its data once, on mount, and never again. An instance left
 * open on a laptop showed whatever it had fetched that morning — log an hour on your
 * phone and the laptop would not know until you reloaded it. Nothing was wrong with
 * the data; nothing had asked for it.
 *
 * <p>Three signals, because no one of them covers the cases:
 *
 * <ul>
 *   <li>visibilitychange fires for a backgrounded tab or a phone app being reopened.
 *   <li>focus fires when you switch back to a window that was never hidden — a second
 *       monitor, or the installed app behind another window.
 *   <li>online fires when the network returns, which is when a queued write can finally
 *       go out.
 * </ul>
 *
 * <p>They overlap: switching windows often fires two of them. The throttle below is what
 * stops that from becoming two of every request on the page.
 */
import { useEffect, useRef } from "react";

/** Long enough to swallow the duplicate signals, short enough to feel immediate. */
const THROTTLE_MS = 3000;

export function useAppResume(onResume: () => void | Promise<unknown>): void {
  // Held in a ref so a caller that rebuilds its handler every render — most of them do —
  // does not resubscribe every render.
  const handler = useRef(onResume);
  handler.current = onResume;

  useEffect(() => {
    let lastRun = 0;
    const run = () => {
      if (document.visibilityState !== "visible") return;
      const now = Date.now();
      if (now - lastRun < THROTTLE_MS) return;
      lastRun = now;
      // A resume is a background courtesy, not something the user asked for, so a screen
      // that cannot refresh must not raise an unhandled rejection over it. Every caller
      // that has something to say already says it through its own error state.
      Promise.resolve(handler.current()).catch(() => {});
    };
    document.addEventListener("visibilitychange", run);
    window.addEventListener("focus", run);
    window.addEventListener("online", run);
    return () => {
      document.removeEventListener("visibilitychange", run);
      window.removeEventListener("focus", run);
      window.removeEventListener("online", run);
    };
  }, []);
}
