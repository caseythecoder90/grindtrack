import NotificationsPanel from "../push/NotificationsPanel";

interface Props {
  username: string;
  onLogout: () => void;
  onLogoutEverywhere: () => void;
  logoutEverywhereLabel: string;
}

/**
 * Everything a partner sees, which is the whole point: not the hours, not the money, not the
 * journal, not the other tab. The server refuses those with a 403 whether or not this component
 * exists; this is only the screen that makes their side of the app look like a place rather
 * than an absence. The chat lands here next; the notifications switch is here now so a phone is
 * ready to be told when the first message arrives.
 */
export default function PartnerHome({
  username,
  onLogout,
  onLogoutEverywhere,
  logoutEverywhereLabel,
}: Props) {
  return (
    <section className="panel partner-home">
      <h2>hi, {username}</h2>
      <p className="muted">
        This is your side of grindtrack. The chat is on its way; for now this is where
        notifications are turned on, so it is ready when the first message comes.
      </p>
      <NotificationsPanel />
      <div className="actions">
        <button type="button" onClick={onLogout}>
          log out
        </button>
        <button type="button" onClick={onLogoutEverywhere}>
          {logoutEverywhereLabel}
        </button>
      </div>
    </section>
  );
}
