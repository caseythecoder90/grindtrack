import { keepSessionAlive } from "../../lib/api";
import type { ChatMessage, Cursor } from "./chatApi";

/** What the server sends. Everything that changes the room arrives as one of these. */
export type Frame =
  | { type: "message"; message: ChatMessage }
  | { type: "unsent"; message: ChatMessage }
  | { type: "reaction"; message: ChatMessage }
  | { type: "cursor"; cursor: Cursor }
  | { type: "typing"; userId: number; on: boolean };

export type Connection = "connecting" | "open" | "closed";

/** Reconnect delays: a second, then doubling, and never more than half a minute. */
const MAX_BACKOFF_MS = 30_000;

/**
 * One socket for as long as the app is open, reconnecting on its own.
 *
 * <p>The only thing the browser ever sends is typing; everything else it does is a request, and
 * everything it hears is a frame from the server. A dropped socket comes back by itself — with
 * backoff while the network is gone, and at once when the app returns to the foreground or the
 * network returns — and each reopen tells the store, whose job it is to fetch whatever was said in
 * the gap. Before every handshake the session is renewed, because the access cookie it carries
 * lasts thirty minutes and an app that sat idle would otherwise be refused and retried for nothing.
 */
export class ChatSocket {
  private ws: WebSocket | null = null;
  private attempt = 0;
  private timer: number | null = null;
  private stopped = true;

  constructor(
    private readonly onFrame: (frame: Frame) => void,
    private readonly onState: (connection: Connection) => void,
  ) {}

  start(): void {
    if (!this.stopped) return;
    this.stopped = false;
    document.addEventListener("visibilitychange", this.wake);
    window.addEventListener("online", this.wake);
    window.addEventListener("focus", this.wake);
    void this.connect();
  }

  stop(): void {
    this.stopped = true;
    document.removeEventListener("visibilitychange", this.wake);
    window.removeEventListener("online", this.wake);
    window.removeEventListener("focus", this.wake);
    if (this.timer !== null) {
      clearTimeout(this.timer);
      this.timer = null;
    }
    const ws = this.ws;
    this.ws = null;
    ws?.close();
    this.onState("closed");
  }

  /** Fire and forget; a socket that is not open drops it, which is right for typing. */
  send(frame: object): void {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(frame));
    }
  }

  private async connect(): Promise<void> {
    if (this.stopped || this.ws) return;
    this.onState("connecting");
    await keepSessionAlive().catch(() => false);
    if (this.stopped || this.ws) return;
    const scheme = location.protocol === "https:" ? "wss://" : "ws://";
    const ws = new WebSocket(scheme + location.host + "/api/chat/ws");
    this.ws = ws;
    ws.onopen = () => {
      if (this.ws !== ws) return;
      this.attempt = 0;
      this.onState("open");
    };
    ws.onmessage = (e: MessageEvent<string>) => {
      let frame: Frame | null = null;
      try {
        frame = JSON.parse(e.data) as Frame;
      } catch {
        return;
      }
      if (frame && typeof frame.type === "string") this.onFrame(frame);
    };
    ws.onclose = () => {
      if (this.ws !== ws) return;
      this.ws = null;
      this.onState("closed");
      this.scheduleReconnect();
    };
    // An error is always followed by a close; that is where the reconnect lives.
    ws.onerror = () => {};
  }

  private scheduleReconnect(): void {
    if (this.stopped || this.timer !== null) return;
    const delay = Math.min(MAX_BACKOFF_MS, 1000 * 2 ** Math.min(this.attempt, 5));
    this.attempt += 1;
    this.timer = window.setTimeout(() => {
      this.timer = null;
      void this.connect();
    }, delay);
  }

  /** Back in the foreground, or the network is back: now, not at the next backoff. */
  private readonly wake = (): void => {
    if (this.stopped || this.ws || document.visibilityState !== "visible") return;
    if (this.timer !== null) {
      clearTimeout(this.timer);
      this.timer = null;
    }
    this.attempt = 0;
    void this.connect();
  };
}
