import { useCallback, useEffect, useState, type FormEvent } from "react";
import { errorMessage } from "../../lib/api";
import {
  createPartner,
  endPartnerSessions,
  listAccounts,
  resetPartnerPassword,
  type Account,
  type PartnerCreated,
} from "./authApi";

/**
 * Who can sign in. The owner's row and any partner's; a form to add a partner; and, once, the
 * secret their authenticator needs.
 *
 * <p>The secret is on screen exactly once, in the answer to the create request, and the panel
 * keeps it only until the next refresh or the sheet closes. There is nothing to fetch it again
 * with, which is the point: like the owner's own secret on the bootstrap log, it is read into the
 * authenticator app there and then, and a lost authenticator means a new account.
 */
export default function AccountsPanel() {
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [created, setCreated] = useState<PartnerCreated | null>(null);
  /** Which partner's row has the new-password box open, if any. */
  const [resetting, setResetting] = useState<number | null>(null);
  const [newPassword, setNewPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [note, setNote] = useState("");

  const refresh = useCallback(async () => {
    setAccounts(await listAccounts().catch(() => []));
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  async function add(e: FormEvent) {
    e.preventDefault();
    if (busy) return;
    setBusy(true);
    setNote("");
    try {
      setCreated(await createPartner(username.trim(), password));
      setUsername("");
      setPassword("");
      await refresh();
    } catch (err) {
      setNote(errorMessage(err, "could not add that account"));
    } finally {
      setBusy(false);
    }
  }

  async function reset(id: number) {
    if (busy) return;
    setBusy(true);
    setNote("");
    try {
      const account = await resetPartnerPassword(id, newPassword);
      setNote(`${account.username}'s password is changed`);
      setResetting(null);
      setNewPassword("");
    } catch (err) {
      setNote(errorMessage(err, "could not change that password"));
    } finally {
      setBusy(false);
    }
  }

  async function signOut(account: Account) {
    if (busy) return;
    setBusy(true);
    setNote("");
    try {
      const { sessionsEnded } = await endPartnerSessions(account.id);
      setNote(
        sessionsEnded === 0
          ? `${account.username} had no open session · devices forgotten`
          : `${account.username} is signed out of ${sessionsEnded} ${sessionsEnded === 1 ? "session" : "sessions"} · devices forgotten`,
      );
    } catch (err) {
      setNote(errorMessage(err, "could not sign them out"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="notif accounts">
      <div className="row">
        <span className="label">accounts</span>
        <span className="state">who can sign in</span>
      </div>

      <ul className="devices">
        {accounts.map((a) => (
          <li key={a.id}>
            <span>
              {a.username} · {a.role.toLowerCase()}
              {a.createdAt && ` · since ${a.createdAt.slice(0, 10)}`}
            </span>
            {a.role === "PARTNER" && (
              <span>
                <button
                  type="button"
                  className="linkish"
                  disabled={busy}
                  onClick={() => {
                    setResetting((r) => (r === a.id ? null : a.id));
                    setNewPassword("");
                  }}
                >
                  new password
                </button>{" "}
                <button type="button" className="linkish" disabled={busy} onClick={() => signOut(a)}>
                  sign out everywhere
                </button>
              </span>
            )}
          </li>
        ))}
      </ul>

      {resetting !== null && (
        <form
          onSubmit={(e) => {
            e.preventDefault();
            void reset(resetting);
          }}
        >
          <input
            type="password"
            value={newPassword}
            placeholder="a new password, 12+ characters"
            autoComplete="new-password"
            onChange={(e) => setNewPassword(e.target.value)}
          />
          <button type="submit" disabled={busy || newPassword.length < 12}>
            change it
          </button>
        </form>
      )}

      {created && (
        <div className="secret-card">
          <p className="hint">
            <b>{created.username}</b> can sign in now. Add this key to their authenticator app —
            it is shown once and cannot be fetched again.
          </p>
          <p className="secret">{created.totpSecret}</p>
          <p className="hint">Or paste the whole URI into a QR generator and scan that:</p>
          <p className="secret small">{created.otpauthUri}</p>
          <button type="button" className="linkish" onClick={() => setCreated(null)}>
            done — hide it
          </button>
        </div>
      )}

      <form onSubmit={add}>
        <input
          value={username}
          placeholder="a partner's username"
          autoComplete="off"
          onChange={(e) => setUsername(e.target.value)}
        />
        <input
          type="password"
          value={password}
          placeholder="their password, 12+ characters"
          autoComplete="new-password"
          onChange={(e) => setPassword(e.target.value)}
        />
        <button type="submit" disabled={busy || !username.trim() || password.length < 12}>
          add a partner
        </button>
      </form>
      <p className="hint">
        A partner signs in with the same form and sees only their own screen — none of this.
      </p>

      {note && <p className="hint">{note}</p>}
    </div>
  );
}
