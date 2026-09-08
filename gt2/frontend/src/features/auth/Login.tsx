
import { useEffect, useState } from "react";
import { errorMessage } from "../../lib/api";
import { deviceTrusted, login } from "./authApi";

interface Props {
  onSuccess: (username: string) => void;
  onBack: () => void;
}

export default function Login({ onSuccess, onBack }: Props) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [otp, setOtp] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  /**
   * Whether this browser has already proved the second factor. Null until the server answers,
   * because guessing either way flickers: assume trusted and the code field appears late on a
   * new browser, assume not and it vanishes late on a familiar one.
   */
  const [trusted, setTrusted] = useState<boolean | null>(null);
  const [trustThis, setTrustThis] = useState(false);

  useEffect(() => {
    // Unreachable server means show the field: asking for a code you did not need is a small
    // annoyance, and hiding one you did need is a login that cannot succeed.
    deviceTrusted()
      .then((d) => setTrusted(d.trusted))
      .catch(() => setTrusted(false));
  }, []);

  async function submit() {
    setBusy(true);
    setError("");
    try {
      const res = await login(username, password, otp, trustThis);
      onSuccess(res.username);
    } catch (e) {
      setError(errorMessage(e, "Login failed"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="panel login-card">
      <h2>owner login</h2>
      <label htmlFor="u">Username</label>
      <input id="u" value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" />
      <label htmlFor="p">Password</label>
      <input id="p" type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" />
      {trusted === false && (
        <>
          <label htmlFor="o">Authenticator code</label>
          <input id="o" value={otp} inputMode="numeric" placeholder="6-digit code"
            autoComplete="one-time-code"
            onChange={(e) => setOtp(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && submit()} />
          {/* Offered only where it can be taken: a browser that is already trusted has nothing
              to opt into, and showing a ticked, inert box would suggest otherwise. */}
          <label className="inline-check" htmlFor="trust">
            <input id="trust" type="checkbox" checked={trustThis}
              onChange={(e) => setTrustThis(e.target.checked)} />
            Trust this device for 30 days
          </label>
        </>
      )}
      {trusted && (
        <p className="muted small">
          This device is remembered — password only. Forget it from the menu, under Log out.
        </p>
      )}
      <div className="actions">
        <button className="primary" onClick={submit} disabled={busy}>Sign in</button>
        <button onClick={onBack}>Back</button>
        {error && <span className="error">{error}</span>}
      </div>
    </div>
  );
}
