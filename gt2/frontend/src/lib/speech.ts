import { useCallback, useEffect, useRef, useState } from "react";

/**
 * Speech to text, using the browser's own recognizer.
 *
 * <p>Not a transcription API: the Messages API takes no audio, and shipping recordings to a
 * second vendor for a feature that "does not need to be perfect" would be a key, a bill and a
 * privacy question for a rougher result than the phone already produces on its own. The Web Speech
 * API is free, prompts for the microphone once, and — the part that matters here — streams
 * interim text as you talk, so the words appear in the box and get read before anything is sent.
 * Nothing here sends.
 *
 * <p>It is a browser capability with browser-shaped edges: absent in Firefox, and in an installed
 * iOS web app the microphone prompt has historically been unreliable. So the hook reports whether
 * it exists at all, and every failure is a sentence rather than a dead button.
 */

// The DOM lib does not type these. Only what is used is declared, on purpose.
interface RecognitionAlternative {
  transcript: string;
}
interface RecognitionResult {
  isFinal: boolean;
  0: RecognitionAlternative;
}
interface RecognitionEvent {
  resultIndex: number;
  results: ArrayLike<RecognitionResult>;
}
interface RecognitionErrorEvent {
  error: string;
}
interface Recognizer {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  onresult: ((e: RecognitionEvent) => void) | null;
  onerror: ((e: RecognitionErrorEvent) => void) | null;
  onend: (() => void) | null;
  start(): void;
  stop(): void;
  abort(): void;
}
type RecognizerCtor = new () => Recognizer;

function recognizerClass(): RecognizerCtor | null {
  const w = window as unknown as { SpeechRecognition?: RecognizerCtor; webkitSpeechRecognition?: RecognizerCtor };
  return w.SpeechRecognition ?? w.webkitSpeechRecognition ?? null;
}

/** The browser's error codes, as sentences a person can act on. */
function explain(code: string): string {
  switch (code) {
    case "not-allowed":
    case "service-not-allowed":
      return "microphone blocked — allow it for this site in the browser's settings";
    case "no-speech":
      return "did not hear anything";
    case "audio-capture":
      return "no microphone found";
    case "network":
      return "speech recognition needs a connection";
    case "aborted":
      return "";
    default:
      return "could not listen (" + code + ")";
  }
}

export interface Speech {
  /** False where the browser has no recognizer: hide the button rather than show a dead one. */
  supported: boolean;
  listening: boolean;
  /** Words heard but not yet settled — rendered live, replaced as the recognizer revises them. */
  interim: string;
  error: string;
  start(): void;
  /** Stop and let the last phrase settle. */
  stop(): void;
  /** Stop and discard the phrase in flight: for when the person has taken over the box. */
  abort(): void;
}

/**
 * @param onFinal called with each settled phrase, in order. The caller appends it to whatever is
 *     already typed; the hook never touches the box directly, so typed and spoken text mix freely.
 */
export function useSpeech(onFinal: (text: string) => void): Speech {
  const ctor = useRef<RecognizerCtor | null>(null);
  const active = useRef<Recognizer | null>(null);
  const onFinalRef = useRef(onFinal);
  onFinalRef.current = onFinal;
  const [listening, setListening] = useState(false);
  const [interim, setInterim] = useState("");
  const [error, setError] = useState("");

  if (ctor.current === null && typeof window !== "undefined") {
    ctor.current = recognizerClass();
  }

  const stop = useCallback(() => {
    active.current?.stop();
  }, []);

  const abort = useCallback(() => {
    active.current?.abort();
  }, []);

  const start = useCallback(() => {
    const Ctor = ctor.current;
    if (!Ctor || active.current) return;
    const r = new Ctor();
    r.lang = navigator.language || "en-US";
    r.continuous = true;
    r.interimResults = true;
    r.onresult = (e) => {
      let pending = "";
      for (let i = e.resultIndex; i < e.results.length; i++) {
        const res = e.results[i];
        if (res.isFinal) onFinalRef.current(res[0].transcript.trim());
        else pending += res[0].transcript;
      }
      setInterim(pending.trim());
    };
    r.onerror = (e) => {
      setError(explain(e.error));
    };
    r.onend = () => {
      // The recognizer ends itself on silence, on error, and on stop(); all three land here.
      active.current = null;
      setListening(false);
      setInterim("");
    };
    setError("");
    setInterim("");
    active.current = r;
    setListening(true);
    try {
      r.start();
    } catch {
      active.current = null;
      setListening(false);
      setError("could not start listening");
    }
  }, []);

  // A phone locked mid-sentence, or the tab hidden: stop rather than transcribe the room.
  useEffect(() => {
    const onHide = () => {
      if (document.visibilityState === "hidden") active.current?.abort();
    };
    document.addEventListener("visibilitychange", onHide);
    return () => {
      document.removeEventListener("visibilitychange", onHide);
      active.current?.abort();
    };
  }, []);

  return { supported: ctor.current !== null, listening, interim, error, start, stop, abort };
}
