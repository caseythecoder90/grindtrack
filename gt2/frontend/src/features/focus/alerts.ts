/**
 * Side effects that announce timer phase changes: a short beep and, if the
 * user allowed it, a desktop notification. Kept out of the state machine so
 * timer logic stays pure.
 */

export function chime() {
  try {
    const ctx = new AudioContext();
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.frequency.value = 880;
    osc.connect(gain);
    gain.connect(ctx.destination);
    gain.gain.setValueAtTime(0.25, ctx.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 1.1);
    osc.start();
    osc.stop(ctx.currentTime + 1.1);
  } catch {
    /* no audio available */
  }
}

export function notify(body: string) {
  if ("Notification" in window && Notification.permission === "granted") {
    new Notification("grindtrack", { body });
  }
}

/** Ask for notification permission the first time the timer is started. */
export function requestNotifyPermission() {
  if ("Notification" in window && Notification.permission === "default") {
    Notification.requestPermission();
  }
}
