import { useSyncExternalStore } from "react";
import { errorMessage } from "../../lib/api";
import {
  getMessages,
  getRoom,
  moveCursor,
  sendMessage,
  setReaction,
  unsendMessage,
  type ChatMessage,
  type Cursor,
  type Person,
} from "./chatApi";
import { ChatSocket, type Connection, type Frame } from "./chatSocket";

/** A message on its way: shown at once, replaced by the real one when the server answers. */
export interface Pending {
  clientId: string;
  body: string;
  failed: boolean;
}

export interface ChatView {
  loaded: boolean;
  error: string;
  me: Person | null;
  them: Person | null;
  /** Ascending by id, contiguous from the oldest loaded to the newest known. */
  messages: ChatMessage[];
  hasMore: boolean;
  loadingOlder: boolean;
  pending: Pending[];
  mine: Cursor | null;
  theirs: Cursor | null;
  unread: number;
  /** The other person is typing (clears itself). */
  typing: boolean;
  connection: Connection;
}

const EMPTY: ChatView = {
  loaded: false,
  error: "",
  me: null,
  them: null,
  messages: [],
  hasMore: false,
  loadingOlder: false,
  pending: [],
  mine: null,
  theirs: null,
  unread: 0,
  typing: false,
  connection: "closed",
};

/** Typing is re-sent at most this often while the keys keep moving. */
const TYPING_RESEND_MS = 3000;
/** Keys stopped for this long means stopped typing. */
const TYPING_IDLE_MS = 5000;
/** How long the other person's "typing" shows after the last word of it. */
const TYPING_SHOW_MS = 6000;
/** Acknowledgements are batched: a burst of messages is one cursor request. */
const ACK_DEBOUNCE_MS = 250;

/**
 * The chat's one piece of shared state, and the one socket.
 *
 * <p>The rest of the app has no store: each screen owns what it fetched. The chat is the
 * exception because two things outlive the page — the socket, which should stay open while the
 * app is, and the unread count, which the header shows on every tab — and because a message that
 * arrives while you are on the calendar should be there, not fetched again, when you come back.
 * So this is a small external store read through {@link useChat}, started when someone signs in and
 * stopped when they sign out.
 */
export class ChatStore {
  private state: ChatView = EMPTY;
  private readonly listeners = new Set<() => void>();
  private readonly socket: ChatSocket;
  /** Whether the chat is on screen: when it is, what arrives is read as it arrives. */
  private reading = false;
  private typingOn = false;
  private typingSentAt = 0;
  private typingIdle: number | null = null;
  private typingShow: number | null = null;
  private ack: number | null = null;

  constructor() {
    this.socket = new ChatSocket(
      (frame) => this.onFrame(frame),
      (connection) => this.onConnection(connection),
    );
  }

  readonly subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  };

  readonly getSnapshot = (): ChatView => this.state;

  private set(patch: Partial<ChatView>): void {
    this.state = { ...this.state, ...patch };
    for (const listener of this.listeners) listener();
  }

  start(): void {
    this.socket.start();
    void this.load();
  }

  stop(): void {
    this.socket.stop();
    for (const t of [this.typingIdle, this.typingShow, this.ack]) if (t !== null) clearTimeout(t);
    this.typingIdle = this.typingShow = this.ack = null;
    this.typingOn = false;
    this.state = EMPTY;
    for (const listener of this.listeners) listener();
  }

  /** The page says whether it is on screen. */
  setReading(on: boolean): void {
    this.reading = on;
    if (on) this.acknowledge();
  }

  // ---- loading -----------------------------------------------------------------------------

  async load(): Promise<void> {
    try {
      const [room, page] = await Promise.all([getRoom(), getMessages({})]);
      this.set({
        loaded: true,
        error: "",
        me: room.me,
        them: room.them,
        mine: room.mine,
        theirs: room.theirs,
        unread: room.unread,
        messages: page.messages,
        hasMore: page.hasMore,
      });
      this.acknowledge();
    } catch (e) {
      this.set({ error: errorMessage(e, "could not open the chat") });
    }
  }

  /**
   * After the socket was away: everything since the last message we hold, then the room's state,
   * so nothing said or read in the gap is missed. Before the first load, it is the first load.
   */
  private async catchUp(): Promise<void> {
    if (!this.state.loaded) return this.load();
    try {
      let after = this.lastId();
      for (let more = true; more; ) {
        const page = await getMessages({ after });
        this.merge(page.messages);
        more = page.hasMore && page.messages.length > 0;
        if (more) after = page.messages[page.messages.length - 1].id;
      }
      const room = await getRoom();
      this.set({ them: room.them, mine: room.mine, theirs: room.theirs, unread: room.unread });
      this.acknowledge();
    } catch {
      // The next reopen tries again; the socket is already delivering what comes now.
    }
  }

  async loadOlder(): Promise<void> {
    const { hasMore, loadingOlder, messages } = this.state;
    if (!hasMore || loadingOlder || messages.length === 0) return;
    this.set({ loadingOlder: true });
    try {
      const page = await getMessages({ before: messages[0].id });
      this.merge(page.messages);
      this.set({ hasMore: page.hasMore, loadingOlder: false });
    } catch (e) {
      this.set({ loadingOlder: false, error: errorMessage(e, "could not load earlier messages") });
    }
  }

  /** Insert or replace by id, keep the order, and retire any pending copy the server has now named. */
  private merge(incoming: ChatMessage[]): void {
    if (incoming.length === 0) return;
    const byId = new Map(this.state.messages.map((m) => [m.id, m]));
    for (const m of incoming) byId.set(m.id, m);
    const messages = [...byId.values()].sort((a, b) => a.id - b.id);
    const named = new Set(incoming.map((m) => m.clientId));
    const pending = this.state.pending.filter((p) => !named.has(p.clientId));
    this.set({ messages, pending });
  }

  private lastId(): number {
    const { messages } = this.state;
    return messages.length ? messages[messages.length - 1].id : 0;
  }

  // ---- what the socket says ----------------------------------------------------------------

  private onFrame(frame: Frame): void {
    const me = this.state.me;
    switch (frame.type) {
      case "message": {
        const fromThem = me !== null && frame.message.senderId !== me.id;
        this.merge([frame.message]);
        if (fromThem) {
          this.clearTyping();
          this.set({ unread: this.readingNow() ? 0 : this.state.unread + 1 });
          this.acknowledge();
        }
        return;
      }
      case "unsent":
      case "reaction":
        this.merge([frame.message]);
        return;
      case "cursor":
        if (me !== null && frame.cursor.userId === me.id) {
          // Another device of mine read up to there, or this one did.
          const unread = frame.cursor.readId >= this.lastId() ? 0 : this.state.unread;
          this.set({ mine: frame.cursor, unread });
        } else {
          this.set({ theirs: frame.cursor });
        }
        return;
      case "typing":
        if (me !== null && frame.userId === me.id) return;
        if (frame.on) {
          this.set({ typing: true });
          if (this.typingShow !== null) clearTimeout(this.typingShow);
          this.typingShow = window.setTimeout(() => this.clearTyping(), TYPING_SHOW_MS);
        } else {
          this.clearTyping();
        }
        return;
    }
  }

  private clearTyping(): void {
    if (this.typingShow !== null) clearTimeout(this.typingShow);
    this.typingShow = null;
    if (this.state.typing) this.set({ typing: false });
  }

  private onConnection(connection: Connection): void {
    this.set({ connection });
    if (connection === "open") void this.catchUp();
  }

  private readingNow(): boolean {
    return this.reading && document.visibilityState === "visible";
  }

  /**
   * Delivered: every message we hold got here. Read: when the chat is on screen. One request for
   * a burst, and the server never lets either number move backwards.
   */
  private acknowledge(): void {
    const last = this.lastId();
    if (last === 0) return;
    const { mine } = this.state;
    const read = this.readingNow();
    const needDelivered = mine === null || mine.deliveredId < last;
    const needRead = read && (mine === null || mine.readId < last);
    if (!needDelivered && !needRead) return;
    if (this.ack !== null) clearTimeout(this.ack);
    this.ack = window.setTimeout(async () => {
      this.ack = null;
      try {
        const cursor = await moveCursor({ deliveredUpTo: last, readUpTo: read ? last : undefined });
        this.set({ mine: cursor, unread: read ? 0 : this.state.unread });
      } catch {
        // The next message, or the next reopen, asks again.
      }
    }, ACK_DEBOUNCE_MS);
  }

  // ---- what I do ---------------------------------------------------------------------------

  async send(body: string): Promise<void> {
    const clientId = crypto.randomUUID();
    this.set({ pending: [...this.state.pending, { clientId, body, failed: false }] });
    this.typing(false);
    await this.deliver(clientId, body);
  }

  async retry(clientId: string): Promise<void> {
    const item = this.state.pending.find((p) => p.clientId === clientId);
    if (!item) return;
    this.set({
      pending: this.state.pending.map((p) => (p.clientId === clientId ? { ...p, failed: false } : p)),
    });
    await this.deliver(clientId, item.body);
  }

  discard(clientId: string): void {
    this.set({ pending: this.state.pending.filter((p) => p.clientId !== clientId) });
  }

  /** The same clientId on a retry is the same message to the server, so nothing lands twice. */
  private async deliver(clientId: string, body: string): Promise<void> {
    try {
      this.merge([await sendMessage(clientId, body)]);
    } catch {
      this.set({
        pending: this.state.pending.map((p) => (p.clientId === clientId ? { ...p, failed: true } : p)),
      });
    }
  }

  async unsend(id: number): Promise<void> {
    try {
      this.merge([await unsendMessage(id)]);
    } catch (e) {
      this.set({ error: errorMessage(e, "could not unsend that") });
    }
  }

  /** Toggle: mine on it already comes off, otherwise it goes on. */
  async react(id: number, emoji: string): Promise<void> {
    const me = this.state.me;
    const message = this.state.messages.find((m) => m.id === id);
    if (!me || !message) return;
    const on = !message.reactions.some((r) => r.userId === me.id && r.emoji === emoji);
    try {
      this.merge([await setReaction(id, emoji, on)]);
    } catch (e) {
      this.set({ error: errorMessage(e, "could not react to that") });
    }
  }

  clearError(): void {
    if (this.state.error) this.set({ error: "" });
  }

  /** Called on every keystroke with true, and with false on send or an empty box. */
  typing(on: boolean): void {
    if (on) {
      const now = Date.now();
      if (!this.typingOn || now - this.typingSentAt > TYPING_RESEND_MS) {
        this.socket.send({ type: "typing", on: true });
        this.typingSentAt = now;
        this.typingOn = true;
      }
      if (this.typingIdle !== null) clearTimeout(this.typingIdle);
      this.typingIdle = window.setTimeout(() => this.typing(false), TYPING_IDLE_MS);
    } else if (this.typingOn) {
      this.typingOn = false;
      if (this.typingIdle !== null) clearTimeout(this.typingIdle);
      this.typingIdle = null;
      this.socket.send({ type: "typing", on: false });
    }
  }
}

/** The one store. Started by App when someone signs in, stopped when they sign out. */
export const chatStore = new ChatStore();

export function useChat(): ChatView {
  return useSyncExternalStore(chatStore.subscribe, chatStore.getSnapshot);
}
