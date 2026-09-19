import { api, jsonInit } from "../../lib/api";

export interface Person {
  id: number;
  username: string;
}

/** How far one person has got: delivered to a device, and read on a screen. */
export interface Cursor {
  userId: number;
  deliveredId: number;
  readId: number;
}

export interface Reaction {
  userId: number;
  emoji: string;
}

export interface ChatMessage {
  id: number;
  senderId: number;
  /** Empty once unsent. */
  body: string;
  sentAt: string;
  deletedAt: string | null;
  clientId: string;
  reactions: Reaction[];
}

export interface RoomState {
  me: Person;
  /** Null while there is nobody else yet: an owner before a partner exists. */
  them: Person | null;
  unread: number;
  latestId: number;
  mine: Cursor;
  theirs: Cursor | null;
}

export interface Page {
  /** Oldest first within the page. */
  messages: ChatMessage[];
  hasMore: boolean;
}

const BASE = "/api/chat";

export const getRoom = () => api<RoomState>(BASE);

/** No argument: the newest page. `before`: the page above it. `after`: everything since. */
export const getMessages = (query: { before?: number; after?: number }) => {
  const params = new URLSearchParams();
  if (query.before !== undefined) params.set("before", String(query.before));
  if (query.after !== undefined) params.set("after", String(query.after));
  const qs = params.toString();
  return api<Page>(`${BASE}/messages${qs ? "?" + qs : ""}`);
};

export const sendMessage = (clientId: string, body: string) =>
  api<ChatMessage>(`${BASE}/messages`, jsonInit("POST", { clientId, body }));

export const unsendMessage = (id: number) =>
  api<ChatMessage>(`${BASE}/messages/${id}`, { method: "DELETE" });

export const setReaction = (id: number, emoji: string, on: boolean) =>
  api<ChatMessage>(`${BASE}/messages/${id}/reactions/${encodeURIComponent(emoji)}`, {
    method: on ? "PUT" : "DELETE",
  });

export const moveCursor = (cursor: { deliveredUpTo?: number; readUpTo?: number }) =>
  api<Cursor>(`${BASE}/cursor`, jsonInit("POST", cursor));
