import { api, jsonInit, keepSessionAlive } from "../../lib/api";

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

export type MediaKind = "IMAGE" | "VIDEO" | "AUDIO";

/** A photo or clip in the bucket. No link: the bytes are asked for by id, see {@link mediaUrl}. */
export interface MediaView {
  id: number;
  kind: MediaKind;
  contentType: string;
  bytes: number;
  width: number | null;
  height: number | null;
  durationMs: number | null;
  hasPoster: boolean;
  /** In the tray: a picture either of you kept to send again. */
  sticker: boolean;
}

export interface ChatMessage {
  id: number;
  senderId: number;
  /** Empty once unsent, and possibly empty with a picture. */
  body: string;
  sentAt: string;
  deletedAt: string | null;
  clientId: string;
  reactions: Reaction[];
  media: MediaView | null;
  /** Sent from the tray: shown small and without a bubble. */
  sticker: boolean;
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

export interface MediaStatus {
  configured: boolean;
  maxBytes: number;
  /** "ok" once the bucket answered a HeadBucket; otherwise what it said; null when off. */
  bucket: string | null;
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

export const sendMessage = (clientId: string, body: string, mediaId?: number, sticker = false) =>
  api<ChatMessage>(
    `${BASE}/messages`,
    jsonInit("POST", { clientId, body, mediaId: mediaId ?? null, sticker }),
  );

export const unsendMessage = (id: number) =>
  api<ChatMessage>(`${BASE}/messages/${id}`, { method: "DELETE" });

export const setReaction = (id: number, emoji: string, on: boolean) =>
  api<ChatMessage>(`${BASE}/messages/${id}/reactions/${encodeURIComponent(emoji)}`, {
    method: on ? "PUT" : "DELETE",
  });

export const moveCursor = (cursor: { deliveredUpTo?: number; readUpTo?: number }) =>
  api<Cursor>(`${BASE}/cursor`, jsonInit("POST", cursor));

// ---- pictures and video ------------------------------------------------------------------

export const getMediaStatus = () => api<MediaStatus>(`${BASE}/media/status`);

/** The bytes: a 302 to a signed link the browser follows on its own. Fine as an img or video src. */
export const mediaUrl = (id: number) => `${BASE}/media/${id}`;

/** The small one: the thumbnail, or a video's frame. */
export const posterUrl = (id: number) => `${BASE}/media/${id}/poster`;

export const getStickers = () => api<MediaView[]>(`${BASE}/media/stickers`);

export const keepSticker = (id: number, on: boolean) =>
  api<MediaView>(`${BASE}/media/${id}/sticker`, { method: on ? "PUT" : "DELETE" });

/**
 * The upload, with progress. XMLHttpRequest rather than fetch because fetch cannot report how much
 * of a hundred megabytes has gone; the session is renewed first so a cookie that lapsed while the
 * clip was being chosen does not turn the whole upload into a 401 at the end.
 */
export async function uploadMedia(
  file: Blob,
  filename: string,
  poster: Blob | null,
  shape: { width?: number; height?: number; durationMs?: number },
  onProgress: (fraction: number) => void,
): Promise<MediaView> {
  await keepSessionAlive().catch(() => false);
  const form = new FormData();
  form.append("file", file, filename);
  if (poster) form.append("poster", poster, "poster.jpg");
  if (shape.width) form.append("width", String(shape.width));
  if (shape.height) form.append("height", String(shape.height));
  if (shape.durationMs) form.append("durationMs", String(Math.round(shape.durationMs)));
  return new Promise<MediaView>((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open("POST", `${BASE}/media`);
    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable) onProgress(e.loaded / e.total);
    };
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve(JSON.parse(xhr.responseText) as MediaView);
        return;
      }
      let message = `the upload failed (${xhr.status})`;
      if (xhr.status === 413) message = "that upload is larger than the server accepts";
      else if (xhr.status === 401) message = "the session lapsed — try again";
      else {
        try {
          const body = JSON.parse(xhr.responseText) as { error?: string };
          if (body.error) message = body.error;
        } catch {
          // no JSON body: the status is the message
        }
      }
      reject(new Error(message));
    };
    xhr.onerror = () => reject(new Error("could not reach the server"));
    xhr.send(form);
  });
}
