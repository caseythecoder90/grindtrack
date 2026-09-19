import type { ChatMessage } from "./chatApi";

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
}

function timeOf(iso: string): string {
  return new Date(iso).toLocaleTimeString([], { hour: "numeric", minute: "2-digit" });
}

/**
 * One message: the bubble, the reactions under it, and — when tapped — a row of things to do to
 * it. Mine on the right in the tint the ask tab uses for my words; theirs on the left in the
 * panel colour. An unsent one keeps its place and says so.
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
}: Props) {
  const unsent = message.deletedAt !== null;
  const counts = new Map<string, { n: number; mine: boolean }>();
  for (const r of message.reactions) {
    const c = counts.get(r.emoji) ?? { n: 0, mine: false };
    c.n += 1;
    if (r.userId === meId) c.mine = true;
    counts.set(r.emoji, c);
  }

  return (
    <div className={"chat-msg " + (mine ? "mine" : "theirs")}>
      <div
        className={"bubble" + (unsent ? " unsent" : "")}
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
        {unsent ? "unsent" : message.body}
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
          {mine && (
            <button type="button" className="unsend" onClick={onUnsend}>
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
    </div>
  );
}
