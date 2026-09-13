import { useEffect, useRef, useState } from "react";
import { useSpeech } from "../../lib/speech";

interface Props {
  onSave: (body: string, spoken: boolean) => Promise<void>;
  /** Bumped by the parent to put the cursor in the box (the today view's door). */
  focusKey?: number;
}

/**
 * The ask tab's composer, for the journal: the same card, the same mic, the same send. An entry
 * is marked spoken when any of it came through the mic.
 */
export default function JournalComposer({ onSave, focusKey = 0 }: Props) {
  const [draft, setDraft] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const spoke = useRef(false);
  const box = useRef<HTMLTextAreaElement>(null);
  const speech = useSpeech((phrase) => {
    spoke.current = true;
    setDraft((d) => (d.trim() ? d.replace(/\s*$/, " ") : "") + phrase);
  });
  const shown = speech.interim ? draft + (draft.trim() ? " " : "") + speech.interim : draft;

  useEffect(() => {
    const el = box.current;
    if (!el) return;
    el.style.height = "0px";
    el.style.height = Math.min(el.scrollHeight, 168) + "px";
  }, [shown]);

  useEffect(() => {
    if (focusKey > 0) box.current?.focus();
  }, [focusKey]);

  async function save() {
    const body = draft.trim();
    if (!body || busy) return;
    speech.stop();
    setBusy(true);
    setError("");
    try {
      await onSave(body, spoke.current);
      setDraft("");
      spoke.current = false;
    } catch (e) {
      setError(e instanceof Error ? e.message : "could not save that");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className={"composer rec-composer" + (speech.listening ? " listening" : "")}>
      <textarea
        ref={box}
        className="composer-box"
        value={shown}
        onChange={(e) => {
          if (speech.listening) speech.abort();
          setDraft(e.target.value);
        }}
        placeholder={speech.listening ? "listening…" : "how are you today?"}
        rows={1}
        aria-label="Journal entry"
        onKeyDown={(e) => {
          if (e.key === "Enter" && (e.metaKey || e.ctrlKey)) {
            e.preventDefault();
            save();
          }
        }}
      />
      <div className="composer-row">
        <div className="composer-left">
          {speech.listening ? (
            <span className="listening-pill">
              <span className="dot" aria-hidden="true" />
              listening
            </span>
          ) : error ? (
            <span className="composer-hint error">{error}</span>
          ) : speech.error ? (
            <span className="composer-hint">{speech.error}</span>
          ) : (
            <span className="composer-hint">private · only in your database</span>
          )}
        </div>
        <div className="composer-right">
          {speech.supported && (
            <button
              type="button"
              className={"mic" + (speech.listening ? " on" : "")}
              aria-pressed={speech.listening}
              aria-label={speech.listening ? "stop listening" : "speak an entry"}
              disabled={busy}
              onClick={() => (speech.listening ? speech.stop() : speech.start())}
            >
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <rect x="9" y="3" width="6" height="11" rx="3" />
                <path d="M5 11a7 7 0 0 0 14 0M12 18v3" />
              </svg>
            </button>
          )}
          <button
            type="button"
            className="send"
            aria-label="save the entry"
            onClick={save}
            disabled={!draft.trim() || busy}
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor"
              strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="M12 19V5M5 12l7-7 7 7" />
            </svg>
          </button>
        </div>
      </div>
    </div>
  );
}
