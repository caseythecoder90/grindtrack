import { useCallback, useEffect, useState } from "react";
import BottomNav from "./components/BottomNav";
import Heatmap from "./components/Heatmap";
import StatBar from "./components/StatBar";
import { forgetDevices, logout as endSession, me } from "./features/auth/authApi";
import CalendarPage from "./features/calendar/CalendarPage";
import Login from "./features/auth/Login";
import FinancePage from "./features/finance/FinancePage";
import FocusPage from "./features/focus/FocusPage";
import Landing from "./features/landing/Landing";
import PlanPage from "./features/plan/PlanPage";
import RelationshipPage from "./features/relationship/RelationshipPage";
import StatsPage from "./features/tracking/StatsPage";
import { EXPORT_URL, getStats } from "./features/tracking/trackingApi";
import Today from "./features/tracking/Today";
import Week from "./features/tracking/Week";
import TodoPage from "./features/todo/TodoPage";
import WorkPage from "./features/work/WorkPage";
import { AuthError, errorMessage } from "./lib/api";
import { useAppResume } from "./lib/resume";
import { TABS, type Tab } from "./lib/tabs";
import type { Scope, Stats } from "./lib/types";

type View = "landing" | "login" | "app";

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
  const [forgetLabel, setForgetLabel] = useState("forget trusted devices");

  // One request: /api/stats now carries the heatmap day series for every scope,
  // so switching scope is local and the header no longer needs /api/public/stats.
  const refreshHeader = useCallback(async () => {
    try {
      setStats(await getStats());
    } catch (e) {
      if (e instanceof AuthError) setView("landing");
    }
  }, []);

  const changeScope = useCallback((next: Scope) => {
    setScope(next);
    localStorage.setItem(SCOPE_KEY, next);
  }, []);

  /**
   * Is there still a session? Only a refused one sends you to the landing page.
   *
   * <p>This used to be `.catch(() => setView("landing"))`, which could not tell a
   * refusal from an unreachable server — so a redeploy or a sleeping wifi looked
   * exactly like being logged out, right down to the login form.
   */
  const checkSession = useCallback(async () => {
    try {
      await me();
      setView("app");
      refreshHeader();
    } catch (e) {
      // Never over the top of a login the user has already started. This check is async
      // and the answer arrives a round trip late, so on a cold load it can land after a
      // click on "Owner login" and wipe the half-filled form.
      if (e instanceof AuthError) setView((v) => (v === "login" ? v : "landing"));
      // Anything else is the network. Whatever is on screen stays on screen, and the
      // next resume tries again.
    }
  }, [refreshHeader]);

  useEffect(() => {
    checkSession();
  }, [checkSession]);

  // Coming back to a window that has been open since this morning: re-check the session
  // (a laptop that woke with no network recovers here) and refresh the header numbers.
  useAppResume(() => {
    void checkSession();
  });

  async function logout() {
    await endSession();
    setView("landing");
  }

  /**
   * Revoke every remembered device. Deliberately says how many were forgotten rather than
   * flashing a generic "done": the whole value of the button is knowing it did something, and
   * the count is the only evidence available from this side of the cookie.
   */
  async function forgetTrustedDevices() {
    try {
      const { count } = await forgetDevices();
      setForgetLabel(
        count === 0
          ? "no devices were remembered"
          : count === 1
            ? "1 device forgotten — it will ask for a code"
            : `${count} devices forgotten — they will ask for a code`,
      );
    } catch (e) {
      setForgetLabel(errorMessage(e, "could not forget devices"));
    }
  }

  return (
    <div className="wrap">
      <header>
        <div className="brand"><b>grindtrack</b> // 5-year plan<span className="cursor">_</span></div>
        <div className="sub">jul 2026 → jun 2031 · 20 h/wk study · 40 h/wk work</div>
        <div className="spacer" />
        {view === "app" && (
          <>
            <button onClick={() => (window.location.href = EXPORT_URL)}>Export JSON</button>
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
          {/* Two navigations, one at a time: styles.css shows the strip to a mouse
              and the bar to a thumb. Both render, so neither needs a resize listener,
              and display:none keeps the hidden one out of the accessibility tree. */}
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
          {tab === "cal" && <CalendarPage />}
          {tab === "todos" && <TodoPage />}
          {tab === "plan" && <PlanPage />}
          {tab === "work" && <WorkPage onSaved={refreshHeader} />}
          {tab === "money" && <FinancePage />}
          {tab === "us" && <RelationshipPage />}
          {tab === "week" && <Week />}
          {tab === "stats" && stats && <StatsPage stats={stats} scope={scope} />}
          <BottomNav
            tab={tab}
            onTab={setTab}
            onExport={() => (window.location.href = EXPORT_URL)}
            onLogout={logout}
            onForgetDevices={forgetTrustedDevices}
            forgetLabel={forgetLabel}
          />
        </>
      )}
    </div>
  );
}
