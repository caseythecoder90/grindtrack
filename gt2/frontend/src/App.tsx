import { useCallback, useEffect, useRef, useState } from "react";
import BottomNav from "./components/BottomNav";
import MoreSheet from "./components/MoreSheet";
import TabIcon from "./components/TabIcon";
import Heatmap from "./components/Heatmap";
import StatBar from "./components/StatBar";
import { forgetDevices, logout as endSession, logoutEverywhere as endEverySession, me } from "./features/auth/authApi";
import AskPage from "./features/assistant/AskPage";
import CalendarPage from "./features/calendar/CalendarPage";
import Login from "./features/auth/Login";
import FinancePage from "./features/finance/FinancePage";
import FocusPage from "./features/focus/FocusPage";
import Landing from "./features/landing/Landing";
import PlanPage from "./features/plan/PlanPage";
import RecoveryPage from "./features/recovery/RecoveryPage";
import RelationshipPage from "./features/relationship/RelationshipPage";
import StatsPage from "./features/tracking/StatsPage";
import { EXPORT_URL, getStats } from "./features/tracking/trackingApi";
import Today from "./features/tracking/Today";
import Week from "./features/tracking/Week";
import TodoPage from "./features/todo/TodoPage";
import WorkPage from "./features/work/WorkPage";
import { AuthError, errorMessage } from "./lib/api";
import { useAppResume } from "./lib/resume";
import NotificationsPanel from "./features/push/NotificationsPanel";
import { SECONDARY_TABS, TABS, type Tab } from "./lib/tabs";
import { TARGETS } from "./lib/types";
import type { Scope, Stats } from "./lib/types";

type View = "landing" | "login" | "app";

const SCOPE_KEY = "gt-scope";

function storedScope(): Scope {
  const raw = localStorage.getItem(SCOPE_KEY);
  return raw === "study" || raw === "work" || raw === "all" ? raw : "all";
}

function isTab(value: unknown): value is Tab {
  return typeof value === "string" && (TABS as string[]).includes(value);
}

/** `/?tab=week` from a notification tap opens on that tab, once, and the URL is cleaned. */
function initialTab(): Tab {
  const wanted = new URLSearchParams(window.location.search).get("tab");
  if (isTab(wanted)) {
    window.history.replaceState(null, "", window.location.pathname);
    return wanted;
  }
  return "today";
}

export default function App() {
  const [view, setView] = useState<View>("landing");
  const [tab, setTab] = useState<Tab>(initialTab);
  const [notifOpen, setNotifOpen] = useState(false);
  /** The phone's sheet of the sections the bar has no room for. */
  const [moreOpen, setMoreOpen] = useState(false);
  const moreButton = useRef<HTMLButtonElement>(null);
  const inSheet = SECONDARY_TABS.includes(tab);
  // A tab can change from anywhere (a notification tap, a link), and an open sheet
  // describing a section you already left is just in the way.
  useEffect(() => setMoreOpen(false), [tab]);
  const [stats, setStats] = useState<Stats | null>(null);
  const [scope, setScope] = useState<Scope>(storedScope);
  const [forgetLabel, setForgetLabel] = useState("forget trusted devices");
  const [logoutEverywhereLabel, setLogoutEverywhereLabel] = useState("log out everywhere");

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

  // A tap on a notification names a tab. With a window open the worker posts it here; with none it
  // opens /?tab=…, which initialTab reads once.
  useEffect(() => {
    if (!("serviceWorker" in navigator)) return;
    const onMessage = (e: MessageEvent) => {
      const data = e.data as { type?: string; tab?: string } | null;
      if (data?.type === "open-tab" && isTab(data.tab)) setTab(data.tab);
    };
    navigator.serviceWorker.addEventListener("message", onMessage);
    return () => navigator.serviceWorker.removeEventListener("message", onMessage);
  }, []);

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
   * End every session on every device. This browser lands on the landing page at once; the
   * others find out when their access cookie next lapses. The only failure worth showing is the
   * request not getting through, because then nothing was ended and the button should say so.
   */
  async function logoutEverywhere() {
    try {
      await endEverySession();
      setView("landing");
    } catch (e) {
      setLogoutEverywhereLabel(errorMessage(e, "could not log out everywhere"));
    }
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
        {/* Read from TARGETS rather than written out: the same two numbers used to live in six
            literals across two languages, which is how a target and the bar under it drift apart. */}
        <div className="sub">
          jul 2026 → jun 2031 · {TARGETS.study} h/wk study · {TARGETS.work} h/wk work
        </div>
        <div className="spacer" />
        {view === "app" && (
          <>
            {/* The phone's door to the rest of the sections. Hidden on a fine pointer, where
                the tab strip below shows everything. When the open section lives in the sheet,
                say which — otherwise nothing on the screen says where you are. */}
            <button
              type="button"
              ref={moreButton}
              className={"more" + (inSheet ? " active" : "")}
              aria-label={inSheet ? `more sections (${tab} is open)` : "more sections"}
              aria-haspopup="dialog"
              aria-expanded={moreOpen}
              onClick={() => setMoreOpen((open) => !open)}
            >
              {inSheet && <span>{tab}</span>}
              <TabIcon name="more" size={18} />
            </button>
            <button onClick={() => (window.location.href = EXPORT_URL)}>Export JSON</button>
            <button onClick={() => setNotifOpen((o) => !o)} aria-expanded={notifOpen}>
              Notifications
            </button>
            <button onClick={logout}>Log out</button>
            <button onClick={logoutEverywhere}>{logoutEverywhereLabel}</button>
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
          {/* The desktop door to the same panel the phone reaches through the more sheet. */}
          {notifOpen && (
            <div className="notif-pop">
              <NotificationsPanel />
            </div>
          )}
          {/* The hours and the heatmap head every section but one: the recovery tab is not
              about hours, and its first screen should be its own number. */}
          {stats && tab !== "recovery" && (
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
          {tab === "ask" && <AskPage />}
          {tab === "recovery" && <RecoveryPage />}
          <BottomNav tab={tab} onTab={setTab} />
          {moreOpen && (
            <MoreSheet
              current={tab}
              onPick={setTab}
              onClose={() => {
                setMoreOpen(false);
                moreButton.current?.focus();
              }}
              onExport={() => (window.location.href = EXPORT_URL)}
              onLogout={logout}
              onForgetDevices={forgetTrustedDevices}
              forgetLabel={forgetLabel}
              onLogoutEverywhere={logoutEverywhere}
              logoutEverywhereLabel={logoutEverywhereLabel}
            />
          )}
        </>
      )}
    </div>
  );
}
