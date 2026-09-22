import { useEffect, useRef, useState } from "react";
import { keepSessionAlive } from "../../lib/api";
import { mediaUrl, posterUrl, type ChatMessage, type MediaView } from "./chatApi";
import { clock, isBigEmoji } from "./media";

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

/** Anything that starts http(s):// up to the next space, without the punctuation a sentence ends on. */
const URL_RE = /(https?:\/\/[^\s<>]+?)(?=[.,;:!?)\]'"]*(?:\s|$))/g;

/** The words with their links live: a tap opens the page, not the message's actions. */
function Linked({ text }: { text: string }) {
  const parts = text.split(URL_RE);
  if (parts.length === 1) return <>{text}</>;
  return (
    <>
      {parts.map((part, i) =>
        i % 2 === 1 ? (
          <a key={i} href={part} target="_blank" rel="noopener noreferrer" onClick={(e) => e.stopPropagation()}>
            {part}
          </a>
        ) : (
          <span key={i}>{part}</span>
        ),
      )}
    </>
  );
}

/**
 * A picture in the thread: the poster is what loads. A tap opens the message's actions like any
 * other bubble — "open" among them shows the full picture — so a picture with no caption is not
 * a bubble whose only tappable part is its rim. A picture that fails to load is asked for once
 * more after renewing the session — the img tag cannot refresh a lapsed cookie by itself, and
 * that is the one way it fails.
 */
function Picture({ media }: { media: MediaView }) {
  const [retry, setRetry] = useState(0);
  const src = posterUrl(media.id) + (retry ? `?r=${retry}` : "");
  return (
    <img
      className="chat-media"
      src={src}
      alt=""
      loading="lazy"
      style={{ aspectRatio: ratio(media) }}
      onError={() => {
        if (retry > 0) return;
        void keepSessionAlive().finally(() => setRetry(1));
      }}
    />
  );
}

/**
 * A voice message: play, a bar that fills, the time. The audio element is the player; this is only
 * its face, because the browser's own controls are a different size on every phone and none of
 * them fit in a bubble.
 */
function Voice({ media, mine }: { media: MediaView; mine: boolean }) {
  const audio = useRef<HTMLAudioElement>(null);
  const [playing, setPlaying] = useState(false);
  const [at, setAt] = useState(0);
  const [length, setLength] = useState(media.durationMs ? media.durationMs / 1000 : 0);

  useEffect(() => {
    const a = audio.current;
    if (!a) return;
    const onTime = () => setAt(a.currentTime);
    const onMeta = () => {
      if (Number.isFinite(a.duration) && a.duration > 0) setLength(a.duration);
    };
    const onEnd = () => {
      setPlaying(false);
      setAt(0);
    };
    a.addEventListener("timeupdate", onTime);
    a.addEventListener("loadedmetadata", onMeta);
    a.addEventListener("durationchange", onMeta);
    a.addEventListener("ended", onEnd);
    a.addEventListener("pause", () => setPlaying(false));
    a.addEventListener("play", () => setPlaying(true));
    return () => {
      a.removeEventListener("timeupdate", onTime);
      a.removeEventListener("loadedmetadata", onMeta);
      a.removeEventListener("durationchange", onMeta);
      a.removeEventListener("ended", onEnd);
    };
  }, []);

  function toggle(e: React.MouseEvent) {
    e.stopPropagation();
    const a = audio.current;
    if (!a) return;
    if (a.paused) void a.play().catch(() => setPlaying(false));
    else a.pause();
  }

  function seek(e: React.MouseEvent<HTMLDivElement>) {
    e.stopPropagation();
    const a = audio.current;
    if (!a || !length) return;
    const box = e.currentTarget.getBoundingClientRect();
    const fraction = Math.min(1, Math.max(0, (e.clientX - box.left) / box.width));
    a.currentTime = fraction * length;
    setAt(a.currentTime);
  }

  const fraction = length > 0 ? Math.min(1, at / length) : 0;
  return (
    <div className={"chat-voice" + (mine ? " mine" : "")}>
      <audio ref={audio} src={mediaUrl(media.id)} preload="metadata" />
      <button type="button" className="chat-voice-play" aria-label={playing ? "pause" : "play"} onClick={toggle}>
        {playing ? (
          <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M7 5h4v14H7zM13 5h4v14h-4z" /></svg>
        ) : (
          <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M8 5v14l11-7z" /></svg>
        )}
      </button>
      <div className="chat-voice-bar" role="progressbar" aria-valuenow={Math.round(fraction * 100)} aria-valuemin={0} aria-valuemax={100} onClick={seek}>
        <i style={{ width: `${fraction * 100}%` }} />
      </div>
      <span className="chat-voice-time">{playing || at > 0 ? clock(at * 1000) : clock(length * 1000)}</span>
    </div>
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
  const asSticker = !!media && message.sticker;
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
    (media && !asSticker && media.kind !== "AUDIO" ? " media" : "") +
    (media && media.kind === "AUDIO" ? " voice" : "") +
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
        {media && media.kind === "IMAGE" && <Picture media={media} />}
        {media && media.kind === "VIDEO" && <Clip media={media} />}
        {media && media.kind === "AUDIO" && <Voice media={media} mine={mine} />}
        {!unsent && message.body && (
          <div className={media ? "chat-caption" : undefined}>
            <Linked text={message.body} />
          </div>
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
            <>
              <button type="button" className="word" onClick={() => setLightbox(true)}>
                open
              </button>
              <button type="button" className="word" onClick={() => onKeepSticker(!media.sticker)}>
                {media.sticker ? "drop sticker" : "keep as sticker"}
              </button>
            </>
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
