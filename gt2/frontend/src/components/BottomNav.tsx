import { useEffect, useRef, useState } from "react";
import MoreSheet from "./MoreSheet";
import TabIcon from "./TabIcon";
import { PRIMARY_TABS, SECONDARY_TABS, shortLabel, type Tab } from "../lib/tabs";

interface Props {
  tab: Tab;
  onTab: (tab: Tab) => void;
  onExport: () => void;
  onLogout: () => void;
  onForgetDevices: () => void;
  forgetLabel: string;
}

/**
 * The phone's navigation: four sections plus a sheet for the rest.
 *
 * It is rendered on every screen and hidden by CSS on a fine pointer — the desktop
 * tab strip stays exactly as it was. Which one you get is decided by
 * `@media (pointer: coarse)` in styles.css, not by a width, because a half-width
 * browser on a laptop is still a mouse.
 */
export default function BottomNav({
  tab,
  onTab,
  onExport,
  onLogout,
  onForgetDevices,
  forgetLabel,
}: Props) {
  const [sheetOpen, setSheetOpen] = useState(false);
  const moreButton = useRef<HTMLButtonElement>(null);
  const inSheet = SECONDARY_TABS.includes(tab);

  // A tab can change from outside this component (a link elsewhere in the app), and
  // an open sheet describing a section you already left is just in the way.
  useEffect(() => setSheetOpen(false), [tab]);

  function close() {
    setSheetOpen(false);
    moreButton.current?.focus();
  }

  return (
    <>
      {sheetOpen && (
        <MoreSheet
          current={tab}
          onPick={onTab}
          onClose={close}
          onExport={onExport}
          onLogout={onLogout}
          onForgetDevices={onForgetDevices}
          forgetLabel={forgetLabel}
        />
      )}
      <nav className="bottomnav" aria-label="Sections">
        {PRIMARY_TABS.map((t) => (
          <button
            key={t}
            type="button"
            className={t === tab ? "active" : ""}
            aria-current={t === tab ? "page" : undefined}
            onClick={() => onTab(t)}
          >
            <TabIcon name={t} />
            <span>{shortLabel(t)}</span>
          </button>
        ))}
        <button
          type="button"
          ref={moreButton}
          className={inSheet ? "active" : ""}
          aria-haspopup="dialog"
          aria-expanded={sheetOpen}
          onClick={() => setSheetOpen((open) => !open)}
        >
          <TabIcon name="more" />
          {/* When the open section lives in the sheet, say which — otherwise the bar
              shows nothing selected and the app looks lost. */}
          <span>{inSheet ? tab : "more"}</span>
        </button>
      </nav>
    </>
  );
}
