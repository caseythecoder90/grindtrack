import { useCallback, useEffect, useRef, useState } from "react";
import { api } from "./api";

/**
 * Speech to text for the ask box, through the app's own relay.
 *
 * <p>The microphone is captured here, turned into 16-bit PCM at 24 kHz by a worklet, and sent
 * down a WebSocket to `/api/speech/ws`; the server relays it to a transcription model and sends
 * text back as each phrase settles. The words land in the box as they arrive and get read before
 * anything is sent — nothing here sends a question.
 *
 * <p>This replaced the browser's own recognizer, which was free but unreliable in an installed
 * iPhone app and absent in Firefox. What the browser has to provide now — a microphone, an
 * `AudioWorklet`, a socket — every current browser does, installed or not. The key stays on the
 * server; the phone never sees it.
 *
 * <p>Frames from the server: `ready`, `delta {text}`, `final {text}`, `speech {state}`,
 * `error {message}`. Frames to the server: binary audio, and `{"type":"stop"}`.
 */

export interface Speech {
  /** False until the server says it is configured, and where the browser lacks a piece. */
  supported: boolean;
  listening: boolean;
  /** Words heard but not yet settled — rendered live, replaced as the model revises them. */
  interim: string;
  error: string;
  start(): void;
  /** Stop and let the last phrase arrive. */
  stop(): void;
  /** Stop and discard the phrase in flight: for when the person has taken over the box. */
  abort(): void;
}

/** What the server asked for, and what the worklet produces. */
const TARGET_RATE = 24000;
/** Frames of a tenth of a second: small enough to feel live, large enough not to flood. */
const FRAME_SAMPLES = 2400;

/**
 * The worklet: float samples in, PCM16 frames out, resampled when the context is not 24 kHz
 * (Safari picks its own rate). Shipped as a string because Vite would otherwise need a separate
 * entry for a hundred lines that only this file uses.
 */
const WORKLET = `
class Pcm16 extends AudioWorkletProcessor {
  constructor() {
    super();
    this.ratio = sampleRate / ${TARGET_RATE};
    this.pending = new Float32Array(0);
    this.pos = 0;
    this.out = new Int16Array(${FRAME_SAMPLES});
    this.filled = 0;
  }
  process(inputs) {
    const input = inputs[0] && inputs[0][0];
    if (!input) return true;
    const joined = new Float32Array(this.pending.length + input.length);
    joined.set(this.pending);
    joined.set(input, this.pending.length);
    let pos = this.pos;
    while (pos + 1 < joined.length) {
      const i = Math.floor(pos);
      const frac = pos - i;
      const sample = joined[i] + (joined[i + 1] - joined[i]) * frac;
      const clamped = Math.max(-1, Math.min(1, sample));
      this.out[this.filled++] = clamped < 0 ? clamped * 0x8000 : clamped * 0x7fff;
      if (this.filled === this.out.length) {
        this.port.postMessage(this.out.buffer.slice(0));
        this.filled = 0;
      }
      pos += this.ratio;
    }
    const consumed = Math.floor(pos);
    this.pending = joined.slice(consumed);
    this.pos = pos - consumed;
    return true;
  }
}
registerProcessor("pcm16", Pcm16);
`;

function browserCan(): boolean {
  return (
    typeof navigator !== "undefined" &&
    !!navigator.mediaDevices?.getUserMedia &&
    "AudioWorkletNode" in window &&
    "WebSocket" in window
  );
}

function socketUrl(): string {
  const scheme = window.location.protocol === "https:" ? "wss" : "ws";
  return `${scheme}://${window.location.host}/api/speech/ws`;
}

/** The browser's reasons, as sentences a person can act on. */
function explainMedia(e: unknown): string {
  const name = e instanceof Error ? e.name : "";
  switch (name) {
    case "NotAllowedError":
    case "SecurityError":
      return "microphone blocked — allow it for this site in the browser's settings";
    case "NotFoundError":
    case "OverconstrainedError":
      return "no microphone found";
    case "NotReadableError":
      return "the microphone is in use by something else";
    default:
      return "could not open the microphone";
  }
}

/** Everything one dictation holds, so it can be torn down as one thing. */
interface Session {
  socket: WebSocket;
  stream: MediaStream | null;
  context: AudioContext | null;
  /** Set once the server says ready; audio before that is not sent. */
  ready: boolean;
  /** Set on stop: the socket stays open for the last phrase, the microphone does not. */
  stopping: boolean;
}

/**
 * @param onFinal called with each settled phrase, in order. The caller appends it to whatever is
 *     already typed; the hook never touches the box directly, so typed and spoken text mix freely.
 */
export function useSpeech(onFinal: (text: string) => void): Speech {
  const onFinalRef = useRef(onFinal);
  onFinalRef.current = onFinal;
  const session = useRef<Session | null>(null);
  const [configured, setConfigured] = useState(false);
  const [listening, setListening] = useState(false);
  const [interim, setInterim] = useState("");
  const [error, setError] = useState("");

  // Off on the server means no button, not a dead one.
  useEffect(() => {
    if (!browserCan()) return;
    let cancelled = false;
    api<{ configured: boolean }>("/api/speech/status")
      .then((s) => {
        if (!cancelled) setConfigured(s.configured);
      })
      .catch(() => {
        /* no status, no button: the ask box works without it */
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const teardown = useCallback((keepSocket: boolean) => {
    const s = session.current;
    if (!s) return;
    s.stream?.getTracks().forEach((t) => t.stop());
    s.stream = null;
    void s.context?.close().catch(() => {});
    s.context = null;
    if (!keepSocket) {
      session.current = null;
      if (s.socket.readyState === WebSocket.OPEN || s.socket.readyState === WebSocket.CONNECTING) {
        s.socket.close();
      }
    }
    setListening(false);
  }, []);

  const start = useCallback(() => {
    if (session.current) return;
    setError("");
    setInterim("");
    setListening(true);
    void (async () => {
      // The microphone first, inside the tap: iOS grants it only there.
      let stream: MediaStream;
      try {
        stream = await navigator.mediaDevices.getUserMedia({
          audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true },
        });
      } catch (e) {
        setError(explainMedia(e));
        setListening(false);
        return;
      }

      const socket = new WebSocket(socketUrl());
      socket.binaryType = "arraybuffer";
      const s: Session = { socket, stream, context: null, ready: false, stopping: false };
      session.current = s;
      let pending = "";

      socket.onmessage = (event) => {
        let frame: { type?: string; text?: string; message?: string };
        try {
          frame = JSON.parse(String(event.data));
        } catch {
          return;
        }
        switch (frame.type) {
          case "ready":
            s.ready = true;
            break;
          case "delta":
            pending += frame.text ?? "";
            setInterim(pending.trim());
            break;
          case "final": {
            pending = "";
            setInterim("");
            const text = (frame.text ?? "").trim();
            if (text) onFinalRef.current(text);
            break;
          }
          case "error":
            setError(frame.message ?? "speech to text failed");
            break;
          default:
            break;
        }
      };
      socket.onerror = () => {
        if (session.current === s && !s.stopping) setError("lost the connection while listening");
      };
      socket.onclose = () => {
        // Ours, still current: the server closed after the last phrase or on an error. Either
        // way the microphone must be off and the button back to "speak".
        if (session.current !== s) return;
        setInterim("");
        teardown(false);
      };

      socket.onopen = async () => {
        if (session.current !== s) return;
        try {
          const context = new AudioContext({ sampleRate: TARGET_RATE });
          s.context = context;
          await context.resume();
          const url = URL.createObjectURL(new Blob([WORKLET], { type: "text/javascript" }));
          try {
            await context.audioWorklet.addModule(url);
          } finally {
            URL.revokeObjectURL(url);
          }
          const source = context.createMediaStreamSource(stream);
          const node = new AudioWorkletNode(context, "pcm16", {
            numberOfInputs: 1,
            numberOfOutputs: 1,
            channelCount: 1,
          });
          node.port.onmessage = (e: MessageEvent<ArrayBuffer>) => {
            if (s.ready && !s.stopping && socket.readyState === WebSocket.OPEN) socket.send(e.data);
          };
          // A node with nothing downstream is not always run; a silent gain keeps it processing.
          const mute = context.createGain();
          mute.gain.value = 0;
          source.connect(node);
          node.connect(mute);
          mute.connect(context.destination);
        } catch {
          setError("could not start listening in this browser");
          teardown(false);
        }
      };
    })();
  }, [teardown]);

  const stop = useCallback(() => {
    const s = session.current;
    if (!s) return;
    s.stopping = true;
    // Microphone off now; the socket stays until the server has sent the last phrase.
    teardown(true);
    if (s.socket.readyState === WebSocket.OPEN) {
      s.socket.send(JSON.stringify({ type: "stop" }));
    } else {
      s.socket.close();
      session.current = null;
    }
  }, [teardown]);

  const abort = useCallback(() => {
    setInterim("");
    teardown(false);
  }, [teardown]);

  // A phone locked mid-sentence, or the tab hidden: stop rather than transcribe the room.
  useEffect(() => {
    const onHide = () => {
      if (document.visibilityState === "hidden") abort();
    };
    document.addEventListener("visibilitychange", onHide);
    return () => {
      document.removeEventListener("visibilitychange", onHide);
      abort();
    };
  }, [abort]);

  return { supported: configured && browserCan(), listening, interim, error, start, stop, abort };
}
