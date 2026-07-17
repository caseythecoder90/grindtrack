
import { useCallback, useEffect, useState } from "react";
import Heatmap from "./components/Heatmap";
import StatBar from "./components/StatBar";
import Login from "./features/auth/Login";
import FocusPage from "./features/focus/FocusPage";
import Landing from "./features/landing/Landing";
import StatsPage from "./features/tracking/StatsPage";
import Today from "./features/tracking/Today";
import Week from "./features/tracking/Week";
import { api, AuthError } from "./lib/api";
import { mondayOf, todayISO } from "./lib/dates";
import type { PublicStats, Stats } from "./lib/types";

type View = "landing" | "login" | "app";
type Tab = "today" | "focus" | "week" | "stats";

export default function App() {
  const [view, setView] = useState<View>("landing");
  const [tab, setTab] = useState<Tab>("today");
  const [stats, setStats] = useState<Stats | null>(null);
  const [heatDays, setHeatDays] = useState<PublicStats["days"]>([]);

  const refreshHeader = useCallback(async () => {
    try {
      setStats(await api<Stats>("/api/stats"));
      const pub = await api<PublicStats>("/api/public/stats");
      setHeatDays(pub.days);
    } catch (e) {
      if (e instanceof AuthError) setView("landing");
    }
  }, []);

  useEffect(() => {
    // If a valid session exists (cookie), land directly in the app.
    api<{ username: string }>("/api/auth/me")
      .then(() => {
        setView("app");
        refreshHeader();
      })
      .catch(() => setView("landing"));
  }, [refreshHeader]);

  async function logout() {
    await fetch("/api/auth/logout", { method: "POST", credentials: "same-origin" });
    setView("landing");
  }

  const weekHours = stats?.weeks.find((w) => w.weekStart === mondayOf(todayISO()))?.hours ?? 0;

  return (
    <div className="wrap">
      <header>
        <div className="brand"><b>grindtrack</b> // 3-year plan<span className="cursor">_</span></div>
        <div className="sub">jul 2026 → jun 2029 · target: 16 h/wk</div>
        <div className="spacer" />
        {view === "app" && (
          <>
            <button onClick={() => (window.location.href = "/api/export")}>Export JSON</button>
            <button onClick={logout}>Log out</button>
          </>
        )}
      </header>

      {view === "landing" && <Landing onLoginClick={() => setView("login")} />}
      {view === "login" && (
        <Login onBack={() => setView("landing")}
          onSuccess={() => { setView("app"); refreshHeader(); }} />
      )}
      {view === "app" && (
        <>
          {stats && (
            <StatBar streak={stats.streak} weekHours={weekHours}
              totalHours={stats.totalHours} daysLogged={stats.daysLogged} />
          )}
          <Heatmap days={heatDays} />
          <nav className="tabs">
            {(["today", "focus", "week", "stats"] as Tab[]).map((t) => (
              <button key={t} className={tab === t ? "active" : ""} onClick={() => setTab(t)}>
                {t[0].toUpperCase() + t.slice(1)}
              </button>
            ))}
          </nav>
          {tab === "today" && <Today onSaved={refreshHeader} />}
          {tab === "focus" && <FocusPage onLogged={refreshHeader} />}
          {tab === "week" && <Week />}
          {tab === "stats" && <StatsPage />}
        </>
      )}
    </div>
  );
}
