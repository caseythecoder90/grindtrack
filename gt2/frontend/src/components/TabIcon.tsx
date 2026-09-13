import type { Tab } from "../lib/tabs";

/**
 * Bottom-bar icons. Drawn inline rather than pulled from an icon set: there are a dozen
 * of them, they never change, and a dependency for six paths is not worth the bytes.
 *
 * All on a 24px grid with a 1.6 stroke so they sit at one weight beside each other.
 */
const PATHS: Record<Tab | "more", JSX.Element> = {
  // A logged day, as a line chart — the shape the heatmap and the week bars share.
  today: <path d="M3 12h4l3 8 4-16 3 8h4" />,
  // A stopwatch, crown included, so it reads as a timer and not a clock.
  focus: (
    <>
      <circle cx="12" cy="13" r="8" />
      <path d="M12 9v4l2.5 2.5M9 2h6" />
    </>
  ),
  todos: (
    <>
      <path d="M4 7h11M4 12h11M4 17h7" />
      <path d="M18 6l2 2 3-3.5" />
    </>
  ),
  // A speech bubble with a tail — the one icon here that means "say something".
  ask: (
    <>
      <path d="M20 15a2 2 0 0 1-2 2H8l-4 4V6a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2z" />
      <path d="M9 10h6" />
    </>
  ),
  cal: (
    <>
      <rect x="3" y="5" width="18" height="16" rx="2" />
      <path d="M3 10h18M8 3v4M16 3v4" />
    </>
  ),
  // Descending rules: a roadmap narrowing to what is next.
  plan: <path d="M4 6h16M4 12h10M4 18h13" />,
  work: (
    <>
      <rect x="3" y="7" width="18" height="13" rx="2" />
      <path d="M8 7V5a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
    </>
  ),
  more: (
    <>
      <circle cx="5" cy="12" r="1.4" />
      <circle cx="12" cy="12" r="1.4" />
      <circle cx="19" cy="12" r="1.4" />
    </>
  ),
  money: (
    <>
      <path d="M12 3v18" />
      <path d="M16.5 7.5A3.5 3.5 0 0 0 13 5h-1.5a3.5 3.5 0 0 0 0 7h1a3.5 3.5 0 0 1 0 7H11a3.5 3.5 0 0 1-3.5-2.5" />
    </>
  ),
  us: <path d="M12 20s-7-4.5-7-9.2A4 4 0 0 1 12 8a4 4 0 0 1 7 2.8c0 4.7-7 9.2-7 9.2z" />,
  week: (
    <>
      <rect x="3" y="5" width="18" height="16" rx="2" />
      <path d="M3 10h18M8 3v4M16 3v4" />
    </>
  ),
  stats: <path d="M4 20V10M10 20V4M16 20v-7M22 20h-20" />,
  // A sunrise: the morning's first screen, and one day at a time.
  recovery: (
    <>
      <path d="M4 18h16M6 18a6 6 0 0 1 12 0" />
      <path d="M12 4v3M5 8l2 2M19 8l-2 2" />
    </>
  ),
};

interface Props {
  name: Tab | "more";
  size?: number;
}

export default function TabIcon({ name, size = 21 }: Props) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.6}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      {PATHS[name]}
    </svg>
  );
}
