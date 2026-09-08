import { useEffect, useRef } from "react";
import TabIcon from "./TabIcon";
import { SECONDARY_TABS, type Tab } from "../lib/tabs";

interface Props {
  current: Tab;
  onPick: (tab: Tab) => void;
  onClose: () => void;
  /** The header's actions. They have no room in a 390px header, and they belong here. */
  onExport: () => void;
  onLogout: () => void;
  onForgetDevices: () => void;
  forgetLabel: string;
}

/**
 * The sections the bottom bar has no room for.
 *
 * A sheet rather than a second row of tabs: these are reviewed weekly, not opened
 * daily, so they can cost a tap. It is modal — Escape and the backdrop close it, and
 * focus moves in on open and back to the button that opened it on close, because a
 * dialog that strands the keyboard behind a backdrop is worse than no dialog.
 */
export default function MoreSheet({
  current,
  onPick,
  onClose,
  onExport,
  onLogout,
  onForgetDevices,
  forgetLabel,
}: Props) {
  const panel = useRef<HTMLDivElement>(null);

  useEffect(() => {
    panel.current?.querySelector<HTMLButtonElement>("button")?.focus();

    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") {
        onClose();
        return;
      }
      if (e.key !== "Tab" || !panel.current) return;
      // Keep Tab inside the sheet. Without this the next stop is the page behind
      // the backdrop, which is visible but not reachable by pointer.
      const stops = panel.current.querySelectorAll<HTMLButtonElement>("button");
      if (stops.length === 0) return;
      const first = stops[0];
      const last = stops[stops.length - 1];
      if (e.shiftKey && document.activeElement === first) {
        e.preventDefault();
        last.focus();
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault();
        first.focus();
      }
    }

    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [onClose]);

  return (
    <div className="sheet-backdrop" onClick={onClose}>
      <div
        className="sheet"
        ref={panel}
        role="dialog"
        aria-modal="true"
        aria-label="More sections"
        // The backdrop closes on click; the panel must not pass its own clicks up.
        onClick={(e) => e.stopPropagation()}
      >
        <div className="sheet-grip" aria-hidden="true" />
        <div className="sheet-items">
          {SECONDARY_TABS.map((t) => (
            <button
              key={t}
              type="button"
              className={t === current ? "sheet-item active" : "sheet-item"}
              aria-current={t === current ? "page" : undefined}
              onClick={() => onPick(t)}
            >
              <TabIcon name={t} size={20} />
              <span>{t}</span>
            </button>
          ))}
        </div>
        <div className="sheet-actions">
          <button type="button" onClick={onExport}>
            export json
          </button>
          <button type="button" onClick={onLogout}>
            log out
          </button>
        </div>
        {/* Sits apart from the two above because it is the only one that changes the account
            rather than this browser: after it, every device asks for the authenticator again. */}
        <button type="button" className="sheet-close" onClick={onForgetDevices}>
          {forgetLabel}
        </button>
        <button type="button" className="sheet-close" onClick={onClose}>
          close
        </button>
      </div>
    </div>
  );
}
