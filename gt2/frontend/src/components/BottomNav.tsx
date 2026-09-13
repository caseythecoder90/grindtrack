import TabIcon from "./TabIcon";
import { PRIMARY_TABS, shortLabel, type Tab } from "../lib/tabs";

interface Props {
  tab: Tab;
  onTab: (tab: Tab) => void;
}

/**
 * The phone's navigation: five sections. The rest are behind the "more" button in the
 * header, which opens the same sheet this bar used to own.
 *
 * It is rendered on every screen and hidden by CSS on a fine pointer — the desktop
 * tab strip stays exactly as it was. Which one you get is decided by
 * `@media (pointer: coarse)` in styles.css, not by a width, because a half-width
 * browser on a laptop is still a mouse.
 */
export default function BottomNav({ tab, onTab }: Props) {
  return (
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
    </nav>
  );
}
