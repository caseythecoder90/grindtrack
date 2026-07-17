
import { useState } from "react";
import { api, jsonInit } from "../../lib/api";

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

  async function submit() {
    setBusy(true);
    setError("");
    try {
      const res = await api<{ username: string }>(
        "/api/auth/login",
        jsonInit("POST", { username, password, otp }),
      );
      onSuccess(res.username);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Login failed");
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
      <label htmlFor="o">Authenticator code</label>
      <input id="o" value={otp} inputMode="numeric" placeholder="6-digit code"
        onChange={(e) => setOtp(e.target.value)}
        onKeyDown={(e) => e.key === "Enter" && submit()} />
      <div className="actions">
        <button className="primary" onClick={submit} disabled={busy}>Sign in</button>
        <button onClick={onBack}>Back</button>
        {error && <span className="error">{error}</span>}
      </div>
    </div>
  );
}
