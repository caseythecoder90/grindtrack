import { useCallback, useEffect, useState } from "react";
import Heatmap from "./components/Heatmap";
import StatBar from "./components/StatBar";
import Login from "./features/auth/Login";
import FocusPage from "./features/focus/FocusPage";
import Landing from "./features/landing/Landing";
import PlanPage from "./features/plan/PlanPage";
import StatsPage from "./features/tracking/StatsPage";
import Today from "./features/tracking/Today";
import Week from "./features/tracking/Week";
import TodoPage from "./features/todo/TodoPage";
import WorkPage from "./features/work/WorkPage";
import { api, AuthError } from "./lib/api";
import type { Scope, Stats } from "./lib/types";

type View = "landing" | "login" | "app";
type Tab = "today" | "focus" | "todos" | "plan" | "work" | "week" | "stats";

const TABS: Tab[] = ["today", "focus", "todos", "plan", "work", "week", "stats"];

const SCOPE_KEY = "gt-scope";

function storedScope(): Scope {
  const raw = localStorage.getItem(SCOPE_KEY);
  return raw === "study" || raw === "work" || raw === "all" ? raw : "all";
}

export default function App() {
  const [view, setView] = useState<View>("landing");
  const [tab, setTab] = useState<Tab>("today");
  const [stats, setStats] = useState<Stats | null>(null);
  const [scope, setScope] = useState<Scope>(storedScope);

  // One request: /api/stats now carries the heatmap day series for every scope,
  // so switching scope is local and the header no longer needs /api/public/stats.
  const refreshHeader = useCallback(async () => {
    try {
      setStats(await api<Stats>("/api/stats"));
    } catch (e) {
      if (e instanceof AuthError) setView("landing");
    }
  }, []);

  const changeScope = useCallback((next: Scope) => {
    setScope(next);
    localStorage.setItem(SCOPE_KEY, next);
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

  return (
    <div className="wrap">
      <header>
        <div className="brand"><b>grindtrack</b> // 4-year plan<span className="cursor">_</span></div>
        <div className="sub">jul 2026 → jun 2030 · 20 h/wk study · 40 h/wk work</div>
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
            <>
              <StatBar stats={stats} scope={scope} onScopeChange={changeScope} />
              <Heatmap study={stats.study.days} work={stats.work.days} scope={scope} />
            </>
          )}
          <nav className="tabs" aria-label="Sections">
            {TABS.map((t) => (
              <button key={t} className={tab === t ? "active" : ""}
                aria-current={tab === t ? "page" : undefined}
                onClick={() => setTab(t)}>
                {t}
              </button>
            ))}
          </nav>
          {tab === "today" && <Today onSaved={refreshHeader} />}
          {tab === "focus" && <FocusPage onLogged={refreshHeader} />}
          {tab === "todos" && <TodoPage />}
          {tab === "plan" && <PlanPage />}
          {tab === "work" && <WorkPage onSaved={refreshHeader} />}
          {tab === "week" && <Week />}
          {tab === "stats" && stats && <StatsPage stats={stats} scope={scope} />}
        </>
      )}
    </div>
  );
}
