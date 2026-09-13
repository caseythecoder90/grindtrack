/**
 * The bell at the end of a sitting: three soft sine tones a fifth apart, each ringing down over
 * two and a half seconds. Synthesised rather than a sound file, so there is nothing to load and
 * nothing to cache.
 *
 * iOS lets a page make sound only from a context created inside a tap, so the context is made
 * when the timer is started and rung later; a browser without the API just gets the vibration.
 */
export type Bell = { ring(): void; close(): void };

export function prepareBell(): Bell {
  const Ctx = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
  const ctx = Ctx ? new Ctx() : null;
  ctx?.resume().catch(() => {});
  return {
    ring() {
      if (navigator.vibrate) navigator.vibrate([300, 150, 300]);
      if (!ctx) return;
      const t = ctx.currentTime;
      [523.25, 783.99, 1046.5].forEach((hz, i) => {
        const at = t + i * 0.2;
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        osc.type = "sine";
        osc.frequency.value = hz;
        gain.gain.setValueAtTime(0.0001, at);
        gain.gain.exponentialRampToValueAtTime(0.3, at + 0.03);
        gain.gain.exponentialRampToValueAtTime(0.0001, at + 2.5);
        osc.connect(gain).connect(ctx.destination);
        osc.start(at);
        osc.stop(at + 2.6);
      });
    },
    close() {
      ctx?.close().catch(() => {});
    },
  };
}
