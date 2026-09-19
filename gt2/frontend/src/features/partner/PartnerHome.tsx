import { useState } from "react";
import ChatPage from "../chat/ChatPage";
import NotificationsPanel from "../push/NotificationsPanel";

interface Props {
  username: string;
  onLogout: () => void;
  onLogoutEverywhere: () => void;
  logoutEverywhereLabel: string;
}

/**
 * Everything a partner sees, which is the point: not the hours, not the money, not the journal,
 * not the other tab. The server refuses those with a 403 whether or not this component exists;
 * this is only the screen that makes their side of the app a place. The chat is the page; the
 * notifications switch and the way out sit behind one small word so the first message is the
 * first thing seen.
 */
export default function PartnerHome({
  username,
  onLogout,
  onLogoutEverywhere,
  logoutEverywhereLabel,
}: Props) {
  const [tools, setTools] = useState(false);
  return (
    <section className="partner-home">
      <div className="partner-tools">
        <span>hi, {username}</span>
        <span className="spacer" />
        <button type="button" className="linkish" onClick={() => setTools((o) => !o)} aria-expanded={tools}>
          {tools ? "hide settings" : "settings"}
        </button>
      </div>
      {tools && (
        <div className="panel">
          <NotificationsPanel />
          <div className="actions">
            <button type="button" onClick={onLogout}>
              log out
            </button>
            <button type="button" onClick={onLogoutEverywhere}>
              {logoutEverywhereLabel}
            </button>
          </div>
        </div>
      )}
      <ChatPage />
    </section>
  );
}
