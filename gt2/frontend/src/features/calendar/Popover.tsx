import {
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
  type CSSProperties,
  type ReactNode,
} from "react";
import { isCoarse } from "./model";

interface Props {
  /** Where the card sits next to, on a mouse screen. Null centres it. */
  anchor: DOMRect | null;
  label: string;
  onClose: () => void;
  children: ReactNode;
  className?: string;
}

const GAP = 8;

/**
 * A card next to what was clicked, or a sheet from the bottom on a phone.
 *
 * <p>The same children either way; only the frame changes, and it changes on the pointer rather
 * than the width for the reason styles.css gives. On a mouse screen the card goes to the right of
 * the anchor, then the left, then below, and is pushed back inside the window if it would not fit;
 * a popover half off the screen is worse than one that is not quite where you clicked.
 *
 * <p>Escape closes; clicking the backdrop closes; nothing inside does, so a mis-click on a chip
 * does not lose a half-written event.
 */
export default function Popover({
  anchor,
  label,
  onClose,
  children,
  className,
}: Props) {
  const sheet = isCoarse();
  const ref = useRef<HTMLDivElement>(null);
  // Invisible, not hidden, until it is placed: a hidden element cannot take focus, and the
  // editor focuses its title the moment it mounts.
  const [style, setStyle] = useState<CSSProperties>({ opacity: 0 });

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  useLayoutEffect(() => {
    if (sheet) {
      setStyle({});
      return;
    }
    const el = ref.current;
    if (!el) return;
    const w = el.offsetWidth;
    const h = el.offsetHeight;
    const vw = window.innerWidth;
    const vh = window.innerHeight;
    let left: number;
    let top: number;
    if (!anchor) {
      left = (vw - w) / 2;
      top = Math.max(GAP, (vh - h) / 2.4);
    } else if (anchor.right + GAP + w <= vw) {
      left = anchor.right + GAP;
      top = anchor.top;
    } else if (anchor.left - GAP - w >= 0) {
      left = anchor.left - GAP - w;
      top = anchor.top;
    } else {
      left = anchor.left;
      top = anchor.bottom + GAP;
    }
    left = Math.min(Math.max(GAP, left), vw - w - GAP);
    top = Math.min(Math.max(GAP, top), vh - h - GAP);
    setStyle({ left, top });
  }, [anchor, sheet, children]);

  return (
    <div
      className={"pop-backdrop" + (sheet ? " sheet-backdrop" : "")}
      onClick={onClose}
    >
      <div
        ref={ref}
        role="dialog"
        aria-label={label}
        className={
          (sheet ? "sheet pop-sheet" : "pop") +
          (className ? " " + className : "")
        }
        style={style}
        onClick={(e) => e.stopPropagation()}
      >
        {sheet && <div className="sheet-grip" />}
        {children}
      </div>
    </div>
  );
}
