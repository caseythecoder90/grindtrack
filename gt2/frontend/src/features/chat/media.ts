/**
 * What the phone does to a photo or a clip before it is sent.
 *
 * <p>A photo is redrawn on a canvas at most 2000 pixels on its long side and saved as a JPEG:
 * an iPhone photo goes from ten megabytes to under one, and the redraw drops the EXIF block —
 * the GPS position included — which is the right default for a picture leaving your network.
 * Orientation is applied in the redraw, so the picture is upright without the tag that said so.
 * A GIF is kept as it is, so it still moves. A clip is sent as it is (no transcoding on a phone),
 * with one frame drawn to a small JPEG as its poster.
 */

export type PreparedKind = "image" | "video";

export interface Prepared {
  kind: PreparedKind;
  file: Blob;
  filename: string;
  /** A small JPEG: the thumbnail, or a frame. */
  poster: Blob | null;
  width: number;
  height: number;
  durationMs: number | null;
  /** An object URL for the composer's preview; revoke it when done. */
  previewUrl: string;
}

const MAX_IMAGE_EDGE = 2000;
/**
 * The thread draws a poster up to 340 CSS pixels wide, which on a phone is a thousand device
 * pixels; 480 was upscaled more than twice and looked it. 1200 is sharp there and still a few
 * hundred kilobytes.
 */
const POSTER_EDGE = 1200;
const JPEG_QUALITY = 0.85;
const POSTER_QUALITY = 0.82;

export async function prepare(file: File, maxBytes: number): Promise<Prepared> {
  if (file.type.startsWith("image/")) return prepareImage(file, maxBytes);
  if (file.type.startsWith("video/")) return prepareVideo(file, maxBytes);
  throw new Error("that is not a photo or a video");
}

async function prepareImage(file: File, maxBytes: number): Promise<Prepared> {
  let bitmap: ImageBitmap;
  try {
    bitmap = await createImageBitmap(file, { imageOrientation: "from-image" });
  } catch {
    throw new Error("that photo could not be read — a JPEG or PNG works");
  }
  try {
    const poster = await draw(bitmap, POSTER_EDGE, POSTER_QUALITY);
    // A GIF stays a GIF: redrawn it would stop moving.
    if (file.type === "image/gif") {
      if (file.size > maxBytes) throw tooBig(maxBytes);
      return {
        kind: "image",
        file,
        filename: file.name || "picture.gif",
        poster,
        width: bitmap.width,
        height: bitmap.height,
        durationMs: null,
        previewUrl: URL.createObjectURL(poster),
      };
    }
    const scaled = scaleTo(bitmap.width, bitmap.height, MAX_IMAGE_EDGE);
    const redrawn = await draw(bitmap, MAX_IMAGE_EDGE, JPEG_QUALITY);
    if (redrawn.size > maxBytes) throw tooBig(maxBytes);
    return {
      kind: "image",
      file: redrawn,
      filename: "photo.jpg",
      poster,
      width: scaled.width,
      height: scaled.height,
      durationMs: null,
      previewUrl: URL.createObjectURL(poster),
    };
  } finally {
    bitmap.close();
  }
}

async function prepareVideo(file: File, maxBytes: number): Promise<Prepared> {
  if (file.size > maxBytes) throw tooBig(maxBytes);
  const url = URL.createObjectURL(file);
  try {
    const video = document.createElement("video");
    video.muted = true;
    video.playsInline = true;
    video.preload = "metadata";
    video.src = url;
    await new Promise<void>((resolve, reject) => {
      video.onloadedmetadata = () => resolve();
      video.onerror = () => reject(new Error("that video could not be read"));
    });
    const durationMs = Number.isFinite(video.duration) ? video.duration * 1000 : null;
    // A frame from just inside the clip: the first is often black.
    const at = Math.min(0.5, (video.duration || 1) / 2);
    await new Promise<void>((resolve, reject) => {
      video.onseeked = () => resolve();
      video.onerror = () => reject(new Error("that video could not be read"));
      video.currentTime = at;
    });
    const width = video.videoWidth;
    const height = video.videoHeight;
    let poster: Blob | null = null;
    if (width > 0 && height > 0) {
      const small = scaleTo(width, height, POSTER_EDGE);
      const canvas = document.createElement("canvas");
      canvas.width = small.width;
      canvas.height = small.height;
      canvas.getContext("2d")?.drawImage(video, 0, 0, small.width, small.height);
      poster = await toBlob(canvas, POSTER_QUALITY).catch(() => null);
    }
    return {
      kind: "video",
      file,
      filename: file.name || "clip.mp4",
      poster,
      width,
      height,
      durationMs,
      previewUrl: poster ? URL.createObjectURL(poster) : url,
    };
  } finally {
    // The preview keeps the file's URL only when there was no poster to show instead.
  }
}

function scaleTo(width: number, height: number, edge: number): { width: number; height: number } {
  const longest = Math.max(width, height);
  if (longest <= edge) return { width, height };
  const factor = edge / longest;
  return { width: Math.round(width * factor), height: Math.round(height * factor) };
}

async function draw(bitmap: ImageBitmap, edge: number, quality: number): Promise<Blob> {
  const size = scaleTo(bitmap.width, bitmap.height, edge);
  const canvas = document.createElement("canvas");
  canvas.width = size.width;
  canvas.height = size.height;
  const ctx = canvas.getContext("2d");
  if (!ctx) throw new Error("this browser cannot resize a photo");
  ctx.drawImage(bitmap, 0, 0, size.width, size.height);
  return toBlob(canvas, quality);
}

function toBlob(canvas: HTMLCanvasElement, quality: number): Promise<Blob> {
  return new Promise((resolve, reject) => {
    canvas.toBlob(
      (blob) => (blob ? resolve(blob) : reject(new Error("could not encode the picture"))),
      "image/jpeg",
      quality,
    );
  });
}

function tooBig(maxBytes: number): Error {
  return new Error(`that is larger than the ${Math.round(maxBytes / (1024 * 1024))} MB the chat takes`);
}

/** Intl.Segmenter, which every current browser has and this project's TypeScript lib does not name. */
interface Segmenter {
  segment(input: string): Iterable<{ segment: string }>;
}
const INTL = Intl as unknown as {
  Segmenter?: new (locale?: string, options?: { granularity: "grapheme" }) => Segmenter;
};

/** Emoji and nothing else, a few of them: shown big, the way every chat shows them. */
export function isBigEmoji(body: string): boolean {
  const text = body.trim();
  if (!text || text.length > 40 || !INTL.Segmenter) return false;
  const graphemes = [...new INTL.Segmenter(undefined, { granularity: "grapheme" }).segment(text)];
  if (graphemes.length === 0 || graphemes.length > 6) return false;
  return graphemes.every((g) => /^\p{Extended_Pictographic}/u.test(g.segment) || /^\s$/.test(g.segment));
}
