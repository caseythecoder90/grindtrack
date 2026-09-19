import { useState } from "react";
import { keepSessionAlive } from "../../lib/api";
import { mediaUrl, posterUrl, type ChatMessage, type MediaView } from "./chatApi";
import { isBigEmoji } from "./media";

/** The reactions a tap offers. Anything else is a keyboard away in the box. */
export const QUICK_REACTIONS = ["❤️", "😂", "👍", "😮", "😢", "🙏"];

/** Under my last message: how far it got. */
export type Receipt = "sent" | "delivered" | "seen" | null;

interface Props {
  message: ChatMessage;
  mine: boolean;
  meId: number;
  /** Whether the row of actions is open under this message. */
  open: boolean;
  receipt: Receipt;
  onToggle: () => void;
  onReact: (emoji: string) => void;
  onUnsend: () => void;
  onKeepSticker: (on: boolean) => void;
}

function timeOf(iso: string): string {
  return new Date(iso).toLocaleTimeString([], { hour: "numeric", minute: "2-digit" });
}

/** Wide, tall or square: the box the picture will fill, known before it loads. */
function ratio(media: MediaView): string | undefined {
  return media.width && media.height ? `${media.width} / ${media.height}` : undefined;
}

/**
 * A picture in the thread. The poster (small) is what the thread shows; a tap opens the full one.
 * A picture that fails to load is asked for once more after renewing the session — the img tag
 * cannot refresh a lapsed cookie by itself, and that is the one way it fails.
 */
function Picture({ media, onOpen }: { media: MediaView; onOpen: () => void }) {
  const [retry, setRetry] = useState(0);
  const src = posterUrl(media.id) + (retry ? `?r=${retry}` : "");
  return (
    <img
      className="chat-media"
      src={src}
      alt=""
      loading="lazy"
      style={{ aspectRatio: ratio(media) }}
      onClick={(e) => {
        e.stopPropagation();
        onOpen();
      }}
      onError={() => {
        if (retry > 0) return;
        void keepSessionAlive().finally(() => setRetry(1));
      }}
    />
  );
}

function Clip({ media }: { media: MediaView }) {
  return (
    <video
      className="chat-media"
      controls
      playsInline
      preload="none"
      poster={media.hasPoster ? posterUrl(media.id) : undefined}
      src={mediaUrl(media.id)}
      style={{ aspectRatio: ratio(media) }}
      onClick={(e) => e.stopPropagation()}
    />
  );
}

/**
 * One message: the bubble, the reactions under it, and — when tapped — a row of things to do to
 * it. Mine on the right in the tint the ask tab uses for my words; theirs on the left in the
 * panel colour. A picture sits in the bubble with its caption under it; a sticker stands alone;
 * a few emoji and nothing else are shown big. An unsent one keeps its place and says so.
 */
export default function Message({
  message,
  mine,
  meId,
  open,
  receipt,
  onToggle,
  onReact,
  onUnsend,
  onKeepSticker,
}: Props) {
  const [lightbox, setLightbox] = useState(false);
  const unsent = message.deletedAt !== null;
  const media = unsent ? null : message.media;
  const asSticker = !!media && media.sticker && message.body === "";
  const big = !media && !unsent && isBigEmoji(message.body);
  const counts = new Map<string, { n: number; mine: boolean }>();
  for (const r of message.reactions) {
    const c = counts.get(r.emoji) ?? { n: 0, mine: false };
    c.n += 1;
    if (r.userId === meId) c.mine = true;
    counts.set(r.emoji, c);
  }

  const bubbleClass =
    "bubble" +
    (unsent ? " unsent" : "") +
    (media && !asSticker ? " media" : "") +
    (asSticker ? " sticker" : "") +
    (big ? " big" : "");

  return (
    <div className={"chat-msg " + (mine ? "mine" : "theirs")}>
      <div
        className={bubbleClass}
        role="button"
        tabIndex={0}
        title={timeOf(message.sentAt)}
        onClick={onToggle}
        onKeyDown={(e) => {
          if (e.key === "Enter" || e.key === " ") {
            e.preventDefault();
            onToggle();
          }
        }}
      >
        {unsent && "unsent"}
        {media && media.kind === "IMAGE" && (
          <Picture media={media} onOpen={() => setLightbox(true)} />
        )}
        {media && media.kind === "VIDEO" && <Clip media={media} />}
        {!unsent && message.body && (
          <div className={media ? "chat-caption" : undefined}>{message.body}</div>
        )}
      </div>
      {counts.size > 0 && (
        <div className="chat-reactions">
          {[...counts.entries()].map(([emoji, c]) => (
            <button
              key={emoji}
              type="button"
              className={"chat-reaction" + (c.mine ? " mine" : "")}
              aria-pressed={c.mine}
              onClick={() => onReact(emoji)}
            >
              {emoji}
              {c.n > 1 && <span> {c.n}</span>}
            </button>
          ))}
        </div>
      )}
      {open && !unsent && (
        <div className="chat-actions" role="group" aria-label="react">
          {QUICK_REACTIONS.map((emoji) => (
            <button key={emoji} type="button" onClick={() => onReact(emoji)} aria-label={emoji}>
              {emoji}
            </button>
          ))}
          {media && media.kind === "IMAGE" && (
            <button type="button" className="word" onClick={() => onKeepSticker(!media.sticker)}>
              {media.sticker ? "drop sticker" : "keep as sticker"}
            </button>
          )}
          {mine && (
            <button type="button" className="word" onClick={onUnsend}>
              unsend
            </button>
          )}
        </div>
      )}
      {(open || receipt) && (
        <div className="chat-meta">
          {open && timeOf(message.sentAt)}
          {open && receipt && " · "}
          {receipt}
        </div>
      )}
      {lightbox && media && (
        <div
          className="chat-lightbox"
          role="dialog"
          aria-label="picture"
          onClick={() => setLightbox(false)}
        >
          <img src={mediaUrl(media.id)} alt="" />
        </div>
      )}
    </div>
  );
}
